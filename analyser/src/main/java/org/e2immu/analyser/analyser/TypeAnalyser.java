/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Either;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

/**
 * In the type analysis record we state whether this type has "free fields" or not.
 * Nested types will be allowed in two forms:
 * (1) non-private nested types, where (a) all non-private fields must be @E1Immutable,
 * and (b) access to private methods and fields from enclosing to nested and nested to enclosing is restricted
 * to reading fields and calling @NotModified methods in a direct hierarchical line
 * (2) private subtypes, which do not need to satisfy (1a), and which have the one additional freedom compared to (1b) that
 * the enclosing type can access private fields and methods at will as long as the types are in hierarchical line
 * <p>
 * The analyse and check methods are called independently for types and nested types, in an order of dependence determined
 * by the resolver, but guaranteed such that a nested type will always come before its enclosing type.
 * <p>
 * Therefore, at the end of an enclosing type's analysis, we should have decisions on @NotModified of the methods of the
 * enclosing type, and it should be possible to establish whether a nested type only reads fields (does NOT assign) and
 * calls @NotModified private methods.
 * <p>
 * Errors related to those constraints are added to the type making the violation.
 */

@Container(builds = TypeAnalysis.class)
public class TypeAnalyser extends AbstractAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeAnalyser.class);

    private final Messages messages = new Messages();
    public final TypeInfo primaryType;
    public final TypeInfo typeInfo;
    public final TypeInspection typeInspection;
    public final TypeAnalysisImpl.Builder typeAnalysis;

    // initialized in a separate method
    private List<MethodAnalyser> myMethodAnalysersExcludingSAMs;
    private List<MethodAnalyser> myMethodAndConstructorAnalysersExcludingSAMs;
    private List<MethodAnalyser> myMethodAnalysers;
    private List<MethodAnalyser> myConstructors;

    private List<TypeAnalysis> parentAndOrEnclosingTypeAnalysis;
    private List<FieldAnalyser> myFieldAnalysers;

    private final AnalyserComponents<String, Integer> analyserComponents;

    public TypeAnalyser(@NotModified TypeInfo typeInfo,
                        TypeInfo primaryType,
                        AnalyserContext analyserContext) {
        super("Type " + typeInfo.simpleName, analyserContext);
        this.typeInfo = typeInfo;
        this.primaryType = primaryType;
        typeInspection = typeInfo.typeInspection.get();

        typeAnalysis = new TypeAnalysisImpl.Builder(analyserContext.getPrimitives(), typeInfo);
        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>()
                .add("findAspects", (iteration) -> findAspects())
                .add("analyseImplicitlyImmutableTypes", (iteration) -> analyseImplicitlyImmutableTypes());

        if (!typeInfo.isInterface()) {
            builder.add("analyseOnlyMarkEventuallyE1Immutable", this::analyseOnlyMarkEventuallyE1Immutable)
                    .add("analyseEffectivelyE1Immutable", (iteration) -> analyseEffectivelyE1Immutable())
                    .add("analyseIndependent", (iteration) -> analyseIndependent())
                    .add("analyseEffectivelyEventuallyE2Immutable", (iteration) -> analyseEffectivelyEventuallyE2Immutable())
                    .add("analyseContainer", (iteration) -> analyseContainer())
                    .add("analyseUtilityClass", (iteration) -> analyseUtilityClass())
                    .add("analyseExtensionClass", (iteration) -> analyseExtensionClass())
                    .add("makeInternalObjectFlowsPermanent", (iteration) -> makeInternalObjectFlowsPermanent());
        }
        analyserComponents = builder.build();

        messages.addAll(typeAnalysis.fromAnnotationsIntoProperties(typeInfo.isInterface(), typeInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions()));
    }

    @Override
    public AnalyserComponents<String, Integer> getAnalyserComponents() {
        return analyserComponents;
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return typeInfo;
    }

    // slightly ugly code, but speed is of the issue
    @Override
    public void initialize() {

        ImmutableList.Builder<MethodAnalyser> myMethodAnalysersExcludingSAMs = new ImmutableList.Builder<>();
        ImmutableList.Builder<MethodAnalyser> myMethodAnalysers = new ImmutableList.Builder<>();
        ImmutableList.Builder<MethodAnalyser> myMethodAndConstructorAnalysersExcludingSAMs = new ImmutableList.Builder<>();
        ImmutableList.Builder<MethodAnalyser> myConstructors = new ImmutableList.Builder<>();
        ImmutableList.Builder<FieldAnalyser> myFieldAnalysers = new ImmutableList.Builder<>();

        analyserContext.getMethodAnalysers().values().forEach(methodAnalyser -> {
            if (methodAnalyser.methodInfo.typeInfo == typeInfo) {
                if (methodAnalyser.methodInfo.isConstructor) {
                    myConstructors.add(methodAnalyser);
                } else {
                    myMethodAnalysers.add(methodAnalyser);
                    if (!methodAnalyser.isSAM) {
                        myMethodAnalysersExcludingSAMs.add(methodAnalyser);
                    }
                }
                if (!methodAnalyser.isSAM) {
                    myMethodAndConstructorAnalysersExcludingSAMs.add(methodAnalyser);
                }
            }
        });
        analyserContext.getFieldAnalysers().values().forEach(fieldAnalyser -> {
            if (fieldAnalyser.fieldInfo.owner == typeInfo) {
                myFieldAnalysers.add(fieldAnalyser);
            }
        });

        this.myMethodAnalysersExcludingSAMs = myMethodAnalysersExcludingSAMs.build();
        this.myConstructors = myConstructors.build();
        this.myMethodAnalysers = myMethodAnalysers.build();
        this.myMethodAndConstructorAnalysersExcludingSAMs = myMethodAndConstructorAnalysersExcludingSAMs.build();
        this.myFieldAnalysers = myFieldAnalysers.build();

        Either<String, TypeInfo> pe = typeInfo.packageNameOrEnclosingType;
        List<TypeAnalysis> tmp = new ArrayList<>(2);
        if (pe.isRight() && !typeInfo.isStatic()) {
            tmp.add(analyserContext.getTypeAnalysers().get(pe.getRight()).typeAnalysis);
        }
        if (!Primitives.isJavaLangObject(typeInspection.parentClass())) {
            TypeAnalyser typeAnalyser = analyserContext.getTypeAnalysers().get(typeInspection.parentClass().typeInfo);
            tmp.add(typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInspection.parentClass().typeInfo.typeAnalysis.get());
        }
        parentAndOrEnclosingTypeAnalysis = ImmutableList.copyOf(tmp);
    }

    @Override
    public Analysis getAnalysis() {
        return typeAnalysis;
    }

    @Override
    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        // before we check, we copy the properties into annotations
        log(ANALYSER, "\n******\nAnnotation validation on type {}\n******", typeInfo.fullyQualifiedName);

        check(typeInfo, UtilityClass.class, e2.utilityClass);
        check(typeInfo, E1Immutable.class, e2.e1Immutable);
        check(typeInfo, E1Container.class, e2.e1Container);
        check(typeInfo, ExtensionClass.class, e2.extensionClass);
        check(typeInfo, Container.class, e2.container);
        check(typeInfo, E2Immutable.class, e2.e2Immutable);
        check(typeInfo, E2Container.class, e2.e2Container);
        check(typeInfo, Independent.class, e2.independent);

        // opposites
        check(typeInfo, MutableModifiesArguments.class, e2.mutableModifiesArguments);
    }

    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(typeAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(typeInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    @Override
    public AnalysisStatus analyse(int iteration) {
        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(iteration);
            for (TypeAnalyserVisitor typeAnalyserVisitor : analyserContext.getConfiguration().debugConfiguration.afterTypePropertyComputations) {
                typeAnalyserVisitor.visit(new TypeAnalyserVisitor.Data(iteration,
                        analyserContext.getPrimitives(),
                        typeInfo, typeAnalysis, analyserComponents.getStatusesAsMap()));
            }

            return analysisStatus;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in type analyser: {}", typeInfo.fullyQualifiedName);
            throw rte;
        }
    }

    @Override
    public void write() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        typeAnalysis.transferPropertiesToAnnotations(analyserContext, e2);
    }

    private AnalysisStatus findAspects() {
        return findAspects(typeAnalysis, typeInfo);
    }

    public static AnalysisStatus findAspects(TypeAnalysisImpl.Builder typeAnalysis, TypeInfo typeInfo) {
        Set<TypeInfo> typesToSearch = new HashSet<>(typeInfo.typeResolution.get().superTypesExcludingJavaLangObject());
        typesToSearch.add(typeInfo);
        assert !typeAnalysis.aspects.isFrozen();

        typesToSearch.forEach(type -> findAspectsSingleType(typeAnalysis, type));

        typeAnalysis.aspects.freeze();
        return DONE;
    }

    // also used by ShallowTypeAnalyser
    private static void findAspectsSingleType(TypeAnalysisImpl.Builder typeAnalysis,
                                              TypeInfo typeInfo) {
        typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .forEach(mainMethod -> findAspectsSingleMethod(typeAnalysis, mainMethod));
    }

    private static void findAspectsSingleMethod(TypeAnalysisImpl.Builder typeAnalysis, MethodInfo mainMethod) {
        List<CompanionMethodName> companionMethodNames =
                mainMethod.methodInspection.get().getCompanionMethods().keySet().stream()
                        .filter(mi -> mi.action() == CompanionMethodName.Action.ASPECT).collect(Collectors.toList());
        if (!companionMethodNames.isEmpty()) {
            for (CompanionMethodName companionMethodName : companionMethodNames) {
                if (companionMethodName.aspect() == null) {
                    throw new UnsupportedOperationException("Aspect is null in aspect definition of " +
                            mainMethod.fullyQualifiedName());
                }
                String aspect = companionMethodName.aspect();
                if (!typeAnalysis.aspects.isSet(aspect)) {
                    typeAnalysis.aspects.put(aspect, mainMethod);
                } else {
                    throw new UnsupportedOperationException("Duplicating aspect " + aspect + " in " +
                            mainMethod.fullyQualifiedName());
                }
            }
            log(ANALYSER, "Found aspects {} in {}, {}", typeAnalysis.aspects.stream().map(Map.Entry::getKey).collect(Collectors.joining(",")),
                    typeAnalysis.typeInfo.fullyQualifiedName, mainMethod.fullyQualifiedName);
        }
    }

    private AnalysisStatus makeInternalObjectFlowsPermanent() {
        if (typeAnalysis.constantObjectFlows.isFrozen()) return DONE;
        for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
            if (methodAnalyser.hasCode()) {
                MethodLevelData methodLevelData = methodAnalyser.methodLevelData();
                if (!methodLevelData.internalObjectFlows.isFrozen()) {
                    log(DELAYED, "Delay the freezing of internal object flows in type {}", typeInfo.fullyQualifiedName);
                    return DELAYS;
                }
                methodLevelData.internalObjectFlows.stream().filter(of -> of.origin == Origin.LITERAL).forEach(of -> {
                    ObjectFlow inType = ensureConstantObjectFlow(of);
                    of.moveAllInto(inType);
                });
            }
        }
        typeAnalysis.constantObjectFlows.freeze();
        return DONE;
    }

    private ObjectFlow ensureConstantObjectFlow(ObjectFlow objectFlow) {
        if (objectFlow == ObjectFlow.NO_FLOW) throw new UnsupportedOperationException();
        if (typeAnalysis.constantObjectFlows.contains(objectFlow))
            return typeAnalysis.constantObjectFlows.get(objectFlow);
        typeAnalysis.constantObjectFlows.add(objectFlow);
        return objectFlow;
    }


    private AnalysisStatus analyseImplicitlyImmutableTypes() {
        if (typeAnalysis.implicitlyImmutableDataTypes.isSet()) return DONE;

        log(E2IMMUTABLE, "Computing implicitly immutable types for {}", typeInfo.fullyQualifiedName);
        Set<ParameterizedType> typesOfFields = typeInspection.fields().stream()
                .map(fieldInfo -> fieldInfo.type).collect(Collectors.toCollection(HashSet::new));
        typesOfFields.addAll(typeInfo.typesOfMethodsAndConstructors());
        typesOfFields.addAll(typesOfFields.stream().flatMap(pt -> pt.components(false).stream()).collect(Collectors.toList()));
        log(E2IMMUTABLE, "Types of fields, methods and constructors: {}", typesOfFields);

        Set<ParameterizedType> explicitTypes = typeInspection.explicitTypes();
        log(E2IMMUTABLE, "Explicit types: {}", explicitTypes);

        typesOfFields.removeIf(type -> {
            if (type.arrays > 0) return true;
            if (type.isUnboundParameterType()) return false;

            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            boolean self = type.typeInfo == typeInfo;
            if (self || Primitives.isPrimitiveExcludingVoid(typeInfo) || Primitives.isBoxedExcludingVoid(typeInfo))
                return true;
            return explicitTypes.contains(type) || explicitTypes.stream().anyMatch(t -> type.isAssignableFrom(analyserContext, t));
        });

        // e2immu is more work, we need to check delays
        boolean e2immuDelay = typesOfFields.stream().anyMatch(type -> {
            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            int immutable = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
            return immutable == MultiLevel.DELAY && analyserContext.getTypeAnalysis(bestType).isBeingAnalysed();
        });
        if (e2immuDelay) {
            log(DELAYED, "Delaying implicitly immutable data types on {} because of immutable", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        typesOfFields.removeIf(type -> {
            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            int immutable = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
            return MultiLevel.isAtLeastEventuallyE2Immutable(immutable);
        });

        typeAnalysis.implicitlyImmutableDataTypes.set(ImmutableSet.copyOf(typesOfFields));
        log(E2IMMUTABLE, "Implicitly immutable data types for {} are: [{}]", typeInfo.fullyQualifiedName, typesOfFields);
        return DONE;
    }

    @Override
    protected Expression getVariableValue(Variable variable) {
        if (variable instanceof DependentVariable) {
            throw new UnsupportedOperationException("NYI");
        }
        if (variable instanceof This) {
            ObjectFlow objectFlow = new ObjectFlow(new Location(typeInfo), typeInfo.asParameterizedType(analyserContext), Origin.NO_ORIGIN);
            return new VariableExpression(variable, objectFlow);
        }
        throw new UnsupportedOperationException();
    }

    /*
          writes: typeAnalysis.approvedPreconditions, the official marker for eventuality in the type

          when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE

         */
    private AnalysisStatus analyseOnlyMarkEventuallyE1Immutable(int iteration) {
        if (typeAnalysis.approvedPreconditions.isFrozen()) {
            return DONE;
        }
        boolean someModifiedNotSet = myMethodAnalysersExcludingSAMs.stream()
                .anyMatch(methodAnalyser -> methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someModifiedNotSet) return DELAYS;

        boolean allPreconditionsOnModifyingMethodsSet = myMethodAnalysersExcludingSAMs.stream()
                .filter(methodAnalyser -> methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                .allMatch(methodAnalyser -> methodAnalyser.methodAnalysis.preconditionForMarkAndOnly.isSet());
        if (!allPreconditionsOnModifyingMethodsSet) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        boolean someInvalidPreconditionsOnModifyingMethods = myMethodAnalysersExcludingSAMs.stream().anyMatch(methodAnalyser ->
                methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED) == Level.TRUE &&
                        methodAnalyser.methodAnalysis.preconditionForMarkAndOnly.get().isEmpty());
        if (someInvalidPreconditionsOnModifyingMethods) {
            log(MARK, "Not all modifying methods have a valid precondition in {}", typeInfo.fullyQualifiedName);
            typeAnalysis.approvedPreconditions.freeze();
            return DONE;
        }

        Map<String, Expression> tempApproved = new HashMap<>();
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.TRUE) {
                List<Expression> preconditions = methodAnalyser.methodAnalysis.preconditionForMarkAndOnly.get();
                for (Expression precondition : preconditions) {
                    handlePrecondition(methodAnalyser, precondition, tempApproved, iteration);
                }
            }
        }
        if (tempApproved.isEmpty()) {
            log(MARK, "No modifying methods in {}", typeInfo.fullyQualifiedName);
            typeAnalysis.approvedPreconditions.freeze();
            return DONE;
        }

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis.approvedPreconditions::put);
        typeAnalysis.approvedPreconditions.freeze();
        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EVENTUAL);
        log(MARK, "Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return DONE;
    }

    private void handlePrecondition(@NotModified MethodAnalyser methodAnalyser, Expression precondition, Map<String, Expression> tempApproved, int iteration) {
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()));
        Expression negated = Negation.negate(evaluationContext, precondition);
        String label = labelOfPreconditionForMarkAndOnly(precondition);
        Expression inMap = tempApproved.get(label);

        boolean isMark = assignmentIncompatibleWithPrecondition(evaluationContext, precondition, methodAnalyser);
        if (isMark) {
            if (inMap == null) {
                tempApproved.put(label, precondition);
            } else if (inMap.equals(precondition)) {
                log(MARK, "OK, precondition for {} turns out to be 'before' already", label);
            } else if (inMap.equals(negated)) {
                log(MARK, "Precondition for {} turns out to be 'after', we switch");
                tempApproved.put(label, precondition);
            }
        } else if (inMap == null) {
            tempApproved.put(label, precondition); // no idea yet if before or after
        } else if (!inMap.equals(precondition) && !inMap.equals(negated)) {
            messages.add(Message.newMessage(new Location(methodAnalyser.methodInfo), Message.DUPLICATE_MARK_LABEL, "Label: " + label));
        }
    }

    public static boolean assignmentIncompatibleWithPrecondition(EvaluationContext evaluationContext,
                                                                 Expression precondition,
                                                                 MethodAnalyser methodAnalyser) {
        Set<Variable> variables = new HashSet<>(precondition.variables());
        for (Variable variable : variables) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            // fieldSummaries are set after the first iteration
            return methodAnalyser.getFieldAsVariableStream(fieldInfo, false).anyMatch(variableInfo -> {
                boolean assigned = variableInfo.isAssigned();
                log(MARK, "Field {} is assigned in {}? {}", variable.fullyQualifiedName(),
                        methodAnalyser.methodInfo.distinguishingName(), assigned);

                String index = variableInfo.getAssignmentId().substring(0, variableInfo.getAssignmentId().indexOf(':'));
                StatementAnalysis statementAnalysis = methodAnalyser.findStatementAnalysis(index);
                Expression state = statementAnalysis.methodLevelData.getCombinedPrecondition();
                if (assigned && state != null && isCompatible(evaluationContext, state, precondition)) {
                    log(MARK, "We checked, and found the state {} compatible with the precondition {}", state, precondition);
                    return false;
                }
                return assigned;
            });
        }
        return false;
    }

    private static boolean isCompatible(EvaluationContext evaluationContext, Expression v1, Expression v2) {
        Expression and = new And(evaluationContext.getPrimitives()).append(evaluationContext, v1, v2);
        return v1.equals(and) || v2.equals(and);
    }

    public static String labelOfPreconditionForMarkAndOnly(List<Expression> values) {
        return values.stream().map(TypeAnalyser::labelOfPreconditionForMarkAndOnly).sorted().collect(Collectors.joining(","));
    }

    public static String labelOfPreconditionForMarkAndOnly(Expression value) {
        return value.variables().stream().map(Variable::simpleName).distinct().sorted().collect(Collectors.joining("+"));
    }

    private AnalysisStatus analyseContainer() {
        int container = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (container != Level.UNDEFINED) return DONE;

        AnalysisStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.CONTAINER, Function.identity(), Level.FALSE);
        if (parentOrEnclosing == DONE || parentOrEnclosing == DELAYS) return parentOrEnclosing;

        boolean fieldsReady = myFieldAnalysers.stream().allMatch(
                fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE ||
                        fieldAnalyser.fieldAnalysis.getEffectivelyFinalValue() != null);
        if (!fieldsReady) {
            log(DELAYED, "Delaying container, need assignedToField to be set");
            return DELAYS;
        }
        boolean allReady = myMethodAndConstructorAnalysersExcludingSAMs.stream().allMatch(MethodAnalyser::fromFieldToParametersIsDone);
        if (!allReady) {
            log(DELAYED, "Delaying container, variables linked to fields and params not yet set");
            return DELAYS;
        }
        for (MethodAnalyser methodAnalyser : myMethodAndConstructorAnalysersExcludingSAMs) {
            if (!methodAnalyser.methodInfo.isPrivate()) {
                for (ParameterAnalyser parameterAnalyser : methodAnalyser.getParameterAnalysers()) {
                    int modified = parameterAnalyser.parameterAnalysis.getProperty(VariableProperty.MODIFIED);
                    if (modified == Level.DELAY && methodAnalyser.hasCode()) return DELAYS; // cannot yet decide
                    if (modified == Level.TRUE) {
                        log(CONTAINER, "{} is not a @Container: the content of {} is modified in {}",
                                typeInfo.fullyQualifiedName,
                                parameterAnalyser.parameterInfo.fullyQualifiedName(),
                                methodAnalyser.methodInfo.distinguishingName());
                        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.FALSE);
                        return DONE;
                    }
                }
            }
        }
        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.TRUE);
        log(CONTAINER, "Mark {} as @Container", typeInfo.fullyQualifiedName);
        return DONE;
    }

    private AnalysisStatus analyseEffectivelyE1Immutable() {
        int typeE1Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E1IMMUTABLE);
        if (typeE1Immutable != MultiLevel.DELAY) return DONE; // we have a decision already

        AnalysisStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.IMMUTABLE,
                i -> convertMultiLevelEffectiveToDelayTrue(MultiLevel.value(i, MultiLevel.E1IMMUTABLE)), MultiLevel.FALSE);
        if (parentOrEnclosing == DELAYS || parentOrEnclosing == DONE) return parentOrEnclosing;

        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            int effectivelyFinal = fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) return DELAYS; // cannot decide
            if (effectivelyFinal == Level.FALSE) {
                log(E1IMMUTABLE, "Type {} cannot be @E1Immutable, field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldAnalyser.fieldInfo.name);
                typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
                return DONE;
            }
        }
        log(E1IMMUTABLE, "Improve IMMUTABLE property of type {} to @E1Immutable", typeInfo.fullyQualifiedName);
        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_E1IMMUTABLE);
        return DONE;
    }

    private static int convertMultiLevelEffectiveToDelayTrue(int i) {
        if (i <= MultiLevel.DELAY) return Level.DELAY;
        if (i == MultiLevel.EFFECTIVE) return Level.TRUE;
        return Level.FALSE;
    }

    /**
     * 4 different rules to enforce:
     * <p>
     * RULE 1: All constructor parameters linked to fields/fields linked to constructor parameters must be @NotModified
     * <p>
     * RULE 2: All fields linking to constructor parameters must be either private or E2Immutable
     * <p>
     * RULE 3: All return values of methods must be independent of the fields linking to constructor parameters
     * <p>
     * We obviously start by collecting exactly these fields.
     *
     * @return true if a decision was made
     */
    private AnalysisStatus analyseIndependent() {
        int typeIndependent = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
        if (typeIndependent != Level.DELAY) return DONE;

        AnalysisStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.INDEPENDENT, Function.identity(), Level.FALSE);
        if (parentOrEnclosing == DONE || parentOrEnclosing == DELAYS) return parentOrEnclosing;

        boolean variablesLinkedNotSet = myFieldAnalysers.stream()
                .anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getLinkedVariables() == null);
        if (variablesLinkedNotSet) {
            log(DELAYED, "Delay independence of type {}, not all variables linked to fields set", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        List<FieldAnalyser> fieldsLinkedToParameters =
                myFieldAnalysers.stream().filter(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getLinkedVariables().variables()
                        .stream().filter(v -> v instanceof ParameterInfo)
                        .map(v -> (ParameterInfo) v).anyMatch(pi -> pi.owner.isConstructor)).collect(Collectors.toList());

        // RULE 1

        boolean modificationStatusUnknown = fieldsLinkedToParameters.stream().anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (modificationStatusUnknown) {
            log(DELAYED, "Delay independence of type {}, modification status of linked fields not yet set", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        boolean someModified = fieldsLinkedToParameters.stream().anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.MODIFIED) == Level.TRUE);
        if (someModified) {
            log(INDEPENDENT, "Type {} cannot be @Independent, some fields linked to parameters are modified", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
            return DONE;
        }

        // RULE 2

        List<FieldAnalyser> nonPrivateFields = fieldsLinkedToParameters.stream().filter(fieldAnalyser -> !fieldAnalyser.fieldInfo.isPrivate()).collect(Collectors.toList());
        for (FieldAnalyser nonPrivateField : nonPrivateFields) {
            int immutable = nonPrivateField.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
            if (immutable == Level.DELAY) {
                log(DELAYED, "Delay independence of type {}, field {} is not known to be immutable", typeInfo.fullyQualifiedName,
                        nonPrivateField.fieldInfo.name);
                return DELAYS;
            }
            if (!MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
                log(INDEPENDENT, "Type {} cannot be @Independent, field {} is non-private and not level 2 immutable",
                        typeInfo.fullyQualifiedName, nonPrivateField.fieldInfo.name);
                typeAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                return DONE;
            }
        }

        // RULE 3

        for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
            if (methodAnalyser.methodInfo.hasReturnValue() && methodAnalyser.hasCode() &&
                    !typeAnalysis.implicitlyImmutableDataTypes.get().contains(methodAnalyser.methodInfo.returnType())) {
                VariableInfo variableInfo = methodAnalyser.getReturnAsVariable();
                if (variableInfo == null) {
                    log(DELAYED, "Delay independence of type {}, method {}'s return statement not known",
                            typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                    return DELAYS;
                }
                if (variableInfo.getLinkedVariables() == null) {
                    log(DELAYED, "Delay independence of type {}, method {}'s return statement summaries linking not known",
                            typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                    return DELAYS;
                }
                boolean safeMethod = Collections.disjoint(variableInfo.getLinkedVariables().variables(), fieldsLinkedToParameters);
                if (!safeMethod) {
                    log(INDEPENDENT, "Type {} cannot be @Independent, method {}'s return values link to some of the fields linked to constructors",
                            typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                    typeAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                    return DONE;
                }
            }
        }

        log(INDEPENDENT, "Improve type {} to @Independent", typeInfo.fullyQualifiedName);
        typeAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.EFFECTIVE);
        return DELAYS;
    }

    private AnalysisStatus parentOrEnclosingMustHaveTheSameProperty(VariableProperty variableProperty,
                                                                    Function<Integer, Integer> mapProperty,
                                                                    int falseValue) {
        List<Integer> propertyValues = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> mapProperty.apply(typeAnalysis.getProperty(variableProperty)))
                .collect(Collectors.toList());
        if (propertyValues.stream().anyMatch(level -> level == Level.DELAY)) {
            log(DELAYED, "Waiting with {} on {}, parent or enclosing class's status not yet known",
                    variableProperty, typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (propertyValues.stream().anyMatch(level -> level != Level.TRUE)) {
            log(DELAYED, "{} cannot be {}, parent or enclosing class is not", typeInfo.fullyQualifiedName, variableProperty);
            typeAnalysis.setProperty(variableProperty, falseValue);
            return DONE;
        }
        return PROGRESS;
    }

    /**
     * Rules as of 30 July 2020: Definition on top of @E1Immutable
     * <p>
     * RULE 1: All fields must be @NotModified.
     * <p>
     * RULE 2: All @SupportData fields must be private, or their types must be level 2 immutable
     * <p>
     * RULE 3: All methods and constructors must be independent of the @SupportData fields
     *
     * @return true if a change was made to typeAnalysis
     */
    private AnalysisStatus analyseEffectivelyEventuallyE2Immutable() {
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE2Immutable = MultiLevel.value(typeImmutable, MultiLevel.E2IMMUTABLE);
        if (typeE2Immutable != MultiLevel.DELAY) return DONE; // we have a decision already
        int typeE1Immutable = MultiLevel.value(typeImmutable, MultiLevel.E1IMMUTABLE);
        if (typeE1Immutable < MultiLevel.EVENTUAL) {
            log(E2IMMUTABLE, "Type {} is not (yet) @E2Immutable, because it is not (yet) (eventually) @E1Immutable", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        int no = MultiLevel.compose(typeE1Immutable, MultiLevel.FALSE);

        AnalysisStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.IMMUTABLE,
                i -> convertMultiLevelEventualToDelayTrue(MultiLevel.value(i, MultiLevel.E2IMMUTABLE)), no);
        if (parentOrEnclosing == DONE || parentOrEnclosing == DELAYS) return parentOrEnclosing;

        boolean eventual = typeAnalysis.isEventual();
        boolean haveToEnforcePrivateAndIndependenceRules = false;

        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.fieldAnalysis;
            FieldInfo fieldInfo = fieldAnalyser.fieldInfo;
            String fieldFQN = fieldInfo.fullyQualifiedName();

            if (fieldAnalysis.isOfImplicitlyImmutableDataType() == null) {
                log(DELAYED, "Field {} not yet known if @SupportData, delaying @E2Immutable on type", fieldFQN);
                return DELAYS;
            }
            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or E2Immutable themselves

            int fieldImmutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
            int fieldE2Immutable = MultiLevel.value(fieldImmutable, MultiLevel.E2IMMUTABLE);
            // field is of the type of the class being analysed... it will not make the difference.
            if (fieldE2Immutable == MultiLevel.DELAY && typeInfo == fieldInfo.type.typeInfo) {
                fieldE2Immutable = MultiLevel.EFFECTIVE;
            }
            // part of rule 2: we now need to check that @NotModified is on the field
            if (fieldE2Immutable == MultiLevel.DELAY) {
                log(DELAYED, "Field {} not known yet if @E2Immutable, delaying @E2Immutable on type", fieldFQN);
                return DELAYS;
            }

            // we're allowing eventualities to cascade!
            if (fieldE2Immutable < MultiLevel.EVENTUAL) {

                boolean fieldRequiresRules = !fieldAnalysis.isOfImplicitlyImmutableDataType();
                haveToEnforcePrivateAndIndependenceRules |= fieldRequiresRules;

                int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!eventual && modified == Level.DELAY) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldFQN);
                    return DELAYS;
                }
                if (!eventual && modified == Level.TRUE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and its content is modified",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    typeAnalysis.setProperty(VariableProperty.IMMUTABLE, no);
                    return DONE;
                }

                // RULE 2: ALL @SupportData FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, no);
                        return DONE;
                    }
                } else {
                    log(E2IMMUTABLE, "Ignoring private modifier check of {}, self-referencing", fieldFQN);
                }
            }
        }

        if (haveToEnforcePrivateAndIndependenceRules) {

            for (MethodAnalyser constructor : myConstructors) {
                int independent = constructor.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
                if (independent == Level.DELAY) {
                    log(DELAYED, "Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}",
                            constructor.methodInfo.distinguishingName());
                    return DELAYS; //not decided
                }
                if (independent == Level.FALSE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because constructor is not @Independent",
                            typeInfo.fullyQualifiedName, constructor.methodInfo.name);
                    typeAnalysis.setProperty(VariableProperty.IMMUTABLE, no);
                    return DONE;
                }
            }

            for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                if (methodAnalyser.methodInfo.isVoid()) continue; // we're looking at return types
                int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED);
                // in the eventual case, we only need to look at the non-modifying methods
                // calling a modifying method will result in an error
                if (modified == Level.FALSE || !typeAnalysis.isEventual()) {
                    int returnTypeImmutable = methodAnalyser.methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    int returnTypeE2Immutable = MultiLevel.value(returnTypeImmutable, MultiLevel.E2IMMUTABLE);
                    if (returnTypeE2Immutable == MultiLevel.DELAY) {
                        log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodAnalyser.methodInfo.distinguishingName());
                        return DELAYS;
                    }
                    if (returnTypeE2Immutable < MultiLevel.EVENTUAL) {
                        // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
                        int independent = methodAnalyser.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
                        if (independent == Level.DELAY) {
                            log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                            return DELAYS; //not decided
                        }
                        if (independent == MultiLevel.FALSE) {
                            log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                            typeAnalysis.setProperty(VariableProperty.IMMUTABLE, no);
                            return DONE;
                        }
                    }
                }
            }
        }

        log(E2IMMUTABLE, "Improve @Immutable of type {} to @E2Immutable", typeInfo.fullyQualifiedName);
        int e2Immutable = eventual ? MultiLevel.EVENTUAL : MultiLevel.EFFECTIVE;
        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.compose(typeE1Immutable, e2Immutable));
        return DONE;
    }

    private static int convertMultiLevelEventualToDelayTrue(int i) {
        if (i <= MultiLevel.DELAY) return Level.DELAY;
        if (i >= MultiLevel.EVENTUAL) return Level.TRUE;
        return Level.FALSE;
    }

    private AnalysisStatus analyseExtensionClass() {
        int extensionClass = typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS);
        if (extensionClass != Level.DELAY) return DONE;

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (e2Immutable < MultiLevel.EVENTUAL) {
            log(UTILITY_CLASS, "Type {} is not an @ExtensionClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, Level.FALSE);
            return DONE;
        }

        boolean haveFirstParameter = false;
        ParameterizedType commonTypeOfFirstParameter = null;
        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (methodInfo.methodInspection.get().isStatic() && !methodInfo.isPrivate()) {
                List<ParameterInfo> parameters = methodInfo.methodInspection.get().getParameters();
                ParameterizedType typeOfFirstParameter;
                if (parameters.isEmpty()) {
                    typeOfFirstParameter = methodInfo.returnType();
                } else {
                    typeOfFirstParameter = parameters.get(0).parameterizedType;
                    haveFirstParameter = true;
                }
                if (commonTypeOfFirstParameter == null) {
                    commonTypeOfFirstParameter = typeOfFirstParameter;
                } else if (ParameterizedType.notEqualsTypeParametersOnlyIndex(commonTypeOfFirstParameter,
                        typeOfFirstParameter)) {
                    log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName +
                            " is not an @ExtensionClass, it has no common type for the first " +
                            "parameter (or return type, if no parameters) of static methods, seeing " +
                            commonTypeOfFirstParameter.detailedString() + " vs " + typeOfFirstParameter.detailedString());
                    commonTypeOfFirstParameter = null;
                    break;
                }
            }
        }
        boolean isExtensionClass = commonTypeOfFirstParameter != null && haveFirstParameter;
        typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, Level.fromBool(isExtensionClass));
        log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return DONE;
    }

    private AnalysisStatus analyseUtilityClass() {
        int utilityClass = typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS);
        if (utilityClass != Level.DELAY) return DELAYS;

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (e2Immutable < MultiLevel.EVENTUAL) {
            log(UTILITY_CLASS, "Type {} is not a @UtilityClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return DONE;
        }

        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (!methodInfo.methodInspection.get().isStatic()) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return DONE;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInspection.constructors()) {
            if (!constructor.isPrivate()) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return DONE;
            }
        }

        if (typeInspection.constructors().isEmpty()) {
            log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return DONE;
        }

        // and there should be no means of generating an object
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (methodAnalyser.methodInfo.methodResolution.get().createObjectOfSelf()) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but an object of the class is created in method "
                        + methodAnalyser.methodInfo.fullyQualifiedName());
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return DONE;
            }
        }

        typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.TRUE);
        log(UTILITY_CLASS, "Type {} marked @UtilityClass", typeInfo.fullyQualifiedName);
        return DONE;
    }

    class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager, null);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }
    }
}
