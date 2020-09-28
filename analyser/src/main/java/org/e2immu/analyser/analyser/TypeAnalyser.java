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
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.Either;
import org.e2immu.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final Messages messages = new Messages();
    public final TypeInfo primaryType;
    public final TypeInfo typeInfo;
    public final TypeInspection typeInspection;
    public final TypeAnalysis typeAnalysis;

    // initialized in a separate method
    private List<MethodAnalyser> myMethodAnalysersExcludingSAMs;
    private List<MethodAnalyser> myMethodAndConstructorAnalysersExcludingSAMs;
    private List<MethodAnalyser> myMethodAnalysers;
    private List<MethodAnalyser> myConstructors;

    private List<TypeAnalysis> parentAndOrEnclosingTypeAnalysis;
    private List<FieldAnalyser> myFieldAnalysers;


    public TypeAnalyser(@NotModified TypeInfo typeInfo,
                        TypeInfo primaryType,
                        AnalyserContext analyserContext) {
        super(analyserContext);
        this.typeInfo = typeInfo;
        this.primaryType = primaryType;
        typeInspection = typeInfo.typeInspection.get();

        typeAnalysis = new TypeAnalysis(typeInfo);
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

        Either<String, TypeInfo> pe = typeInspection.packageNameOrEnclosingType;
        List<TypeAnalysis> tmp = new ArrayList<>(2);
        if (pe.isRight() && !typeInfo.isStatic()) {
            tmp.add(analyserContext.getTypeAnalysers().get(pe.getRight()).typeAnalysis);
        }
        if (typeInspection.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
            TypeAnalyser typeAnalyser = analyserContext.getTypeAnalysers().get(typeInspection.parentClass.typeInfo);
            tmp.add(typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInspection.parentClass.typeInfo.typeAnalysis.get());
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
        typeInfo.typeAnalysis.get().transferPropertiesToAnnotations(e2);

        check(typeInfo, UtilityClass.class, e2.utilityClass.get());
        check(typeInfo, E1Immutable.class, e2.e1Immutable.get());
        check(typeInfo, E1Container.class, e2.e1Container.get());
        check(typeInfo, ExtensionClass.class, e2.extensionClass.get());
        check(typeInfo, Container.class, e2.container.get());
        check(typeInfo, E2Immutable.class, e2.e2Immutable.get());
        check(typeInfo, E2Container.class, e2.e2Container.get());
        check(typeInfo, Independent.class, e2.independent.get());

        // opposites
        check(typeInfo, MutableModifiesArguments.class, e2.mutableModifiesArguments.get());
    }

    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(typeInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    @Override
    public boolean analyse(int iteration) {

        boolean changes = analyseImplicitlyImmutableTypes();

        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);

        if (typeInfo.hasBeenDefined() && !typeInfo.isInterface()) {
            if (analyseOnlyMarkEventuallyE1Immutable()) changes = true;
            if (analyseEffectivelyE1Immutable()) changes = true;
            if (analyseIndependent()) changes = true;
            if (analyseEffectivelyEventuallyE2Immutable()) changes = true;
            if (analyseContainer()) changes = true;
            if (analyseUtilityClass()) changes = true;
            if (analyseExtensionClass()) changes = true;
        }

        for (TypeAnalyserVisitor typeAnalyserVisitor : analyserContext.getConfiguration().debugConfiguration.afterTypePropertyComputations) {
            typeAnalyserVisitor.visit(iteration, typeInfo);
        }

        return changes;
    }

    private boolean analyseImplicitlyImmutableTypes() {
        if (typeAnalysis.implicitlyImmutableDataTypes.isSet()) return false;

        log(E2IMMUTABLE, "Computing implicitly immutable types for {}", typeInfo.fullyQualifiedName);
        Set<ParameterizedType> typesOfFields = typeInspection.fields.stream()
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
            if (self || typeInfo.isPrimitiveOrBoxed()) return true;
            return explicitTypes.contains(type) || explicitTypes.stream().anyMatch(type::isAssignableFrom);
        });

        // e2immu is more work, we need to check delays
        boolean e2immuDelay = typesOfFields.stream().anyMatch(type -> {
            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            int immutable = bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            return immutable == MultiLevel.DELAY && bestType.hasBeenDefined();
        });
        if (e2immuDelay) {
            log(DELAYED, "Delaying implicitly immutable data types on {} because of immutable", typeInfo.fullyQualifiedName);
            return false;
        }
        typesOfFields.removeIf(type -> {
            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            int immutable = bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            return MultiLevel.isAtLeastEventuallyE2Immutable(immutable);
        });

        typeAnalysis.implicitlyImmutableDataTypes.set(ImmutableSet.copyOf(typesOfFields));
        log(E2IMMUTABLE, "Implicitly immutable data types for {} are: [{}]", typeInfo.fullyQualifiedName, typesOfFields);
        return true;
    }

    @Override
    protected Value getVariableValue(Variable variable) {
        if (variable instanceof DependentVariable) {
            throw new UnsupportedOperationException("NYI");
        }
        if (variable instanceof This) {
            Map<VariableProperty, Integer> properties = new HashMap<>();
            properties.put(VariableProperty.MODIFIED, typeAnalysis.getProperty(VariableProperty.MODIFIED));
            // TODO this is prob. not correct
            Set<Variable> linkedVariables = Set.of();
            // TODO this is prob. not correct
            ObjectFlow objectFlow = new ObjectFlow(new Location(typeInfo), typeInfo.asParameterizedType(), Origin.NO_ORIGIN);
            return new VariableValue(variable, variable.name(), properties, linkedVariables, objectFlow, false);
        }
        throw new UnsupportedOperationException();
    }

    /*
          writes: typeAnalysis.approvedPreconditions, the official marker for eventuality in the type

          when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE

          TODO: parents, enclosing types
         */
    private boolean analyseOnlyMarkEventuallyE1Immutable() {
        if (!typeAnalysis.approvedPreconditions.isEmpty()) {
            return false; // already done
        }
        boolean someModifiedNotSet = myMethodAnalysersExcludingSAMs.stream()
                .anyMatch(methodAnalyser -> methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someModifiedNotSet) return false;

        boolean allPreconditionsOnModifyingMethodsSet = myMethodAnalysersExcludingSAMs.stream()
                .filter(methodAnalyser -> methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                .allMatch(methodAnalyser -> methodAnalyser.methodAnalysis.preconditionForMarkAndOnly.isSet());
        if (!allPreconditionsOnModifyingMethodsSet) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean someInvalidPreconditionsOnModifyingMethods = myMethodAnalysersExcludingSAMs.stream().anyMatch(methodAnalyser ->
                methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED) == Level.TRUE &&
                        methodAnalyser.methodAnalysis.preconditionForMarkAndOnly.get().isEmpty());
        if (someInvalidPreconditionsOnModifyingMethods) {
            log(MARK, "Not all modifying methods have a valid precondition in {}", typeInfo.fullyQualifiedName);
            return false;
        }

        Map<String, Value> tempApproved = new HashMap<>();
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.TRUE) {
                List<Value> preconditions = methodAnalyser.methodAnalysis.preconditionForMarkAndOnly.get();
                for (Value precondition : preconditions) {
                    handlePrecondition(methodAnalyser, precondition, tempApproved);
                }
            }
        }
        if (tempApproved.isEmpty()) {
            log(MARK, "No modifying methods in {}", typeInfo.fullyQualifiedName);
            return false;
        }

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis.approvedPreconditions::put);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.EVENTUAL);
        log(MARK, "Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return true;
    }

    private void handlePrecondition(@NotModified MethodAnalyser methodAnalyser, Value precondition, Map<String, Value> tempApproved) {
        Value negated = NegatedValue.negate(precondition);
        String label = labelOfPreconditionForMarkAndOnly(precondition);
        Value inMap = tempApproved.get(label);

        boolean isMark = assignmentIncompatibleWithPrecondition(precondition, methodAnalyser.methodInfo, methodAnalyser.methodLevelData());
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

    public static boolean assignmentIncompatibleWithPrecondition(Value precondition, MethodInfo methodInfo, @NotModified MethodLevelData methodLevelData) {
        Set<Variable> variables = precondition.variables();
        for (Variable variable : variables) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            // fieldSummaries are set after the first iteration
            if (methodLevelData.fieldSummaries.isSet(fieldInfo)) {
                TransferValue tv = methodLevelData.fieldSummaries.get(fieldInfo);
                boolean assigned = tv.properties.get(VariableProperty.ASSIGNED) >= Level.READ_ASSIGN_ONCE;
                log(MARK, "Field {} is assigned in {}? {}", variable.name(), methodInfo.distinguishingName(), assigned);

                if (assigned && tv.stateOnAssignment.isSet()) {
                    Value state = tv.stateOnAssignment.get();
                    if (isCompatible(state, precondition)) {
                        log(MARK, "We checked, and found the state {} compatible with the precondition {}", state, precondition);
                        return false;
                    }
                }

                return assigned;
            }
        }
        return false;
    }

    private static boolean isCompatible(Value v1, Value v2) {
        Value and = new AndValue().append(v1, v2);
        return v1.equals(and) || v2.equals(and);
    }

    public static String labelOfPreconditionForMarkAndOnly(List<Value> values) {
        return values.stream().map(TypeAnalyser::labelOfPreconditionForMarkAndOnly).sorted().collect(Collectors.joining(","));
    }

    public static String labelOfPreconditionForMarkAndOnly(Value value) {
        return value.variables().stream().map(Variable::name).distinct().sorted().collect(Collectors.joining("+"));
    }

    private boolean analyseContainer() {
        int container = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (container != Level.UNDEFINED) return false;

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.CONTAINER, Function.identity(), Level.FALSE);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        boolean fieldsReady = myFieldAnalysers.stream().allMatch(
                fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE ||
                        fieldAnalyser.fieldAnalysis.effectivelyFinalValue.isSet());
        if (!fieldsReady) {
            log(DELAYED, "Delaying container, need assignedToField to be set");
            return false;
        }
        boolean allReady = myMethodAndConstructorAnalysersExcludingSAMs.stream().allMatch(
                methodAnalyser -> methodAnalyser.getParameterAnalysers().stream().allMatch(parameterAnalyser ->
                        !parameterAnalyser.getParameterAnalysis().assignedToField.isSet() ||
                                parameterAnalyser.parameterAnalysis.copiedFromFieldToParameters.isSet()));
        if (!allReady) {
            log(DELAYED, "Delaying container, variables linked to fields and params not yet set");
            return false;
        }
        for (MethodAnalyser methodAnalyser : myMethodAndConstructorAnalysersExcludingSAMs) {
            if (!methodAnalyser.methodInfo.isPrivate()) {
                for (ParameterAnalyser parameterAnalyser : methodAnalyser.getParameterAnalysers()) {
                    int modified = parameterAnalyser.parameterAnalysis.getProperty(VariableProperty.MODIFIED);
                    if (modified == Level.DELAY) return false; // cannot yet decide
                    if (modified == Level.TRUE) {
                        log(CONTAINER, "{} is not a @Container: the content of {} is modified in {}",
                                typeInfo.fullyQualifiedName,
                                parameterAnalyser.parameterInfo.detailedString(),
                                methodAnalyser.methodInfo.distinguishingName());
                        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.FALSE);
                        return true;
                    }
                }
            }
        }
        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.TRUE);
        log(CONTAINER, "Mark {} as @Container", typeInfo.fullyQualifiedName);
        return true;
    }

    private boolean analyseEffectivelyE1Immutable() {
        int typeE1Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E1IMMUTABLE);
        if (typeE1Immutable != MultiLevel.DELAY) return false; // we have a decision already

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.IMMUTABLE,
                i -> convertMultiLevelEffectiveToDelayTrue(MultiLevel.value(i, MultiLevel.E1IMMUTABLE)), MultiLevel.FALSE);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            int effectivelyFinal = fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) return false; // cannot decide
            if (effectivelyFinal == Level.FALSE) {
                log(E1IMMUTABLE, "Type {} cannot be @E1Immutable, field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldAnalyser.fieldInfo.name);
                typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
                return true;
            }
        }
        log(E1IMMUTABLE, "Improve IMMUTABLE property of type {} to @E1Immutable", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_E1IMMUTABLE);
        return true;
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
    private boolean analyseIndependent() {
        int typeIndependent = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
        if (typeIndependent != Level.DELAY) return false;

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.INDEPENDENT, Function.identity(), Level.FALSE);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        boolean variablesLinkedNotSet = myFieldAnalysers.stream()
                .anyMatch(fieldAnalyser -> !fieldAnalyser.fieldAnalysis.variablesLinkedToMe.isSet());
        if (variablesLinkedNotSet) {
            log(DELAYED, "Delay independence of type {}, not all variables linked to fields set", typeInfo.fullyQualifiedName);
            return false;
        }
        List<FieldAnalyser> fieldsLinkedToParameters =
                myFieldAnalysers.stream().filter(fieldAnalyser -> fieldAnalyser.fieldAnalysis.variablesLinkedToMe.get()
                        .stream().filter(v -> v instanceof ParameterInfo)
                        .map(v -> (ParameterInfo) v).anyMatch(pi -> pi.owner.isConstructor)).collect(Collectors.toList());

        // RULE 1

        boolean modificationStatusUnknown = fieldsLinkedToParameters.stream().anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (modificationStatusUnknown) {
            log(DELAYED, "Delay independence of type {}, modification status of linked fields not yet set", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean someModified = fieldsLinkedToParameters.stream().anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.MODIFIED) == Level.TRUE);
        if (someModified) {
            log(INDEPENDENT, "Type {} cannot be @Independent, some fields linked to parameters are modified", typeInfo.fullyQualifiedName);
            typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
            return true;
        }

        // RULE 2

        List<FieldAnalyser> nonPrivateFields = fieldsLinkedToParameters.stream().filter(fieldAnalyser -> !fieldAnalyser.fieldInfo.isPrivate()).collect(Collectors.toList());
        for (FieldAnalyser nonPrivateField : nonPrivateFields) {
            int immutable = nonPrivateField.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
            if (immutable == Level.DELAY) {
                log(DELAYED, "Delay independence of type {}, field {} is not known to be immutable", typeInfo.fullyQualifiedName,
                        nonPrivateField.fieldInfo.name);
                return false;
            }
            if (!MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
                log(INDEPENDENT, "Type {} cannot be @Independent, field {} is non-private and not level 2 immutable",
                        typeInfo.fullyQualifiedName, nonPrivateField.fieldInfo.name);
                typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                return true;
            }
        }

        // RULE 3

        for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
            if (!typeAnalysis.implicitlyImmutableDataTypes.get().contains(methodAnalyser.methodInfo.returnType())) {
                MethodLevelData methodLevelData = methodAnalyser.methodLevelData();
                boolean notAllSet = methodLevelData.returnStatementSummaries.stream().map(Map.Entry::getValue)
                        .anyMatch(tv -> !tv.linkedVariables.isSet());
                if (notAllSet) {
                    log(DELAYED, "Delay independence of type {}, method {}'s return statement summaries linking not known",
                            typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                    return false;
                }
                boolean safeMethod = methodLevelData.returnStatementSummaries.stream().map(Map.Entry::getValue)
                        .allMatch(tv -> {
                            Set<FieldInfo> linkedFields = tv.linkedVariables.get().stream()
                                    .filter(v -> v instanceof FieldReference)
                                    .map(v -> ((FieldReference) v).fieldInfo)
                                    .collect(Collectors.toSet());
                            return Collections.disjoint(linkedFields, fieldsLinkedToParameters);
                        });
                if (!safeMethod) {
                    log(INDEPENDENT, "Type {} cannot be @Independent, method {}'s return values link to some of the fields linked to constructors",
                            typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                    return true;
                }
            }
        }

        log(INDEPENDENT, "Improve type {} to @Independent", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.EFFECTIVE);
        return true;
    }

    private Boolean parentOrEnclosingMustHaveTheSameProperty(VariableProperty variableProperty,
                                                             Function<Integer, Integer> mapProperty,
                                                             int falseValue) {
        List<Integer> propertyValues = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> mapProperty.apply(typeAnalysis.getProperty(variableProperty)))
                .collect(Collectors.toList());
        if (propertyValues.stream().anyMatch(level -> level == Level.DELAY)) {
            log(DELAYED, "Waiting with {} on {}, parent or enclosing class's status not yet known",
                    variableProperty, typeInfo.fullyQualifiedName);
            return null;
        }
        if (propertyValues.stream().anyMatch(level -> level != Level.TRUE)) {
            log(DELAYED, "{} cannot be {}, parent or enclosing class is not", typeInfo.fullyQualifiedName, variableProperty);
            typeInfo.typeAnalysis.get().improveProperty(variableProperty, falseValue);
            return true;
        }
        return false;
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
    private boolean analyseEffectivelyEventuallyE2Immutable() {
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE2Immutable = MultiLevel.value(typeImmutable, MultiLevel.E2IMMUTABLE);
        if (typeE2Immutable != MultiLevel.DELAY) return false; // we have a decision already
        int typeE1Immutable = MultiLevel.value(typeImmutable, MultiLevel.E1IMMUTABLE);
        if (typeE1Immutable < MultiLevel.EVENTUAL) {
            log(E2IMMUTABLE, "Type {} is not (yet) @E2Immutable, because it is not (yet) (eventually) @E1Immutable", typeInfo.fullyQualifiedName);
            return false;
        }
        int no = MultiLevel.compose(typeE1Immutable, MultiLevel.FALSE);

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.IMMUTABLE,
                i -> convertMultiLevelEventualToDelayTrue(MultiLevel.value(i, MultiLevel.E2IMMUTABLE)), no);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        boolean eventual = typeAnalysis.isEventual();
        boolean haveToEnforcePrivateAndIndependenceRules = false;

        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.fieldAnalysis;
            FieldInfo fieldInfo = fieldAnalyser.fieldInfo;
            String fieldFQN = fieldInfo.fullyQualifiedName();

            if (!fieldAnalysis.isOfImplicitlyImmutableDataType.isSet()) {
                log(DELAYED, "Field {} not yet known if @SupportData, delaying @E2Immutable on type", fieldFQN);
                return false;
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
                return false;
            }

            // we're allowing eventualities to cascade!
            if (fieldE2Immutable < MultiLevel.EVENTUAL) {

                boolean fieldRequiresRules = !fieldAnalysis.isOfImplicitlyImmutableDataType.get();
                haveToEnforcePrivateAndIndependenceRules |= fieldRequiresRules;

                int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!eventual && modified == Level.DELAY) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldFQN);
                    return false;
                }
                if (!eventual && modified == Level.TRUE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and its content is modified",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                    return true;
                }

                // RULE 2: ALL @SupportData FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                        return true;
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
                    return false; //not decided
                }
                if (independent == Level.FALSE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because constructor is not @Independent",
                            typeInfo.fullyQualifiedName, constructor.methodInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                    return true;
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
                        return false;
                    }
                    if (returnTypeE2Immutable < MultiLevel.EVENTUAL) {
                        // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
                        int independent = methodAnalyser.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
                        if (independent == Level.DELAY) {
                            log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                            return false; //not decided
                        }
                        if (independent == MultiLevel.FALSE) {
                            log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                            typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                            return true;
                        }
                    }
                }
            }
        }

        log(E2IMMUTABLE, "Improve @Immutable of type {} to @E2Immutable", typeInfo.fullyQualifiedName);
        int e2Immutable = eventual ? MultiLevel.EVENTUAL : MultiLevel.EFFECTIVE;
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.compose(typeE1Immutable, e2Immutable));
        return true;
    }

    private static int convertMultiLevelEventualToDelayTrue(int i) {
        if (i <= MultiLevel.DELAY) return Level.DELAY;
        if (i >= MultiLevel.EVENTUAL) return Level.TRUE;
        return Level.FALSE;
    }

    private boolean analyseExtensionClass() {
        int extensionClass = typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS);
        if (extensionClass != Level.DELAY) return false;

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        if (e2Immutable < MultiLevel.EVENTUAL) {
            log(UTILITY_CLASS, "Type {} is not an @ExtensionClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, Level.FALSE);
            return true;
        }

        boolean haveFirstParameter = false;
        ParameterizedType commonTypeOfFirstParameter = null;
        for (MethodInfo methodInfo : typeInspection.methods) {
            if (methodInfo.isStatic && !methodInfo.isPrivate()) {
                List<ParameterInfo> parameters = methodInfo.methodInspection.get().parameters;
                ParameterizedType typeOfFirstParameter;
                if (parameters.isEmpty()) {
                    typeOfFirstParameter = methodInfo.returnType();
                } else {
                    typeOfFirstParameter = parameters.get(0).parameterizedType;
                    haveFirstParameter = true;
                }
                if (commonTypeOfFirstParameter == null) {
                    commonTypeOfFirstParameter = typeOfFirstParameter;
                } else if (!ParameterizedType.equalsTypeParametersOnlyIndex(commonTypeOfFirstParameter,
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
        typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, isExtensionClass);
        log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return true;
    }

    private boolean analyseUtilityClass() {
        int utilityClass = typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS);
        if (utilityClass != Level.DELAY) return false;

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        if (e2Immutable < MultiLevel.EVENTUAL) {
            log(UTILITY_CLASS, "Type {} is not a @UtilityClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return true;
        }

        for (MethodInfo methodInfo : typeInspection.methods) {
            if (!methodInfo.isStatic) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInspection.constructors) {
            if (!constructor.isPrivate()) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }

        if (typeInspection.constructors.isEmpty()) {
            log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return true;
        }

        // and there should be no means of generating an object
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (!methodAnalyser.methodInfo.methodResolution.get().createObjectOfSelf.isSet()) {
                log(UTILITY_CLASS, "Not yet deciding on @Utility class for {}, createObjectOfSelf not yet set on method {}",
                        typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                return false;
            }
            if (methodAnalyser.methodInfo.methodResolution.get().createObjectOfSelf.get()) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but an object of the class is created in method "
                        + methodAnalyser.methodInfo.fullyQualifiedName());
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }

        typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.TRUE);
        log(UTILITY_CLASS, "Type {} marked @UtilityClass", typeInfo.fullyQualifiedName);
        return true;
    }

}
