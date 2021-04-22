/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.check.CheckE1E2Immutable;
import org.e2immu.analyser.analyser.util.AssignmentIncompatibleWithPrecondition;
import org.e2immu.analyser.analyser.util.ExplicitTypes;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.annotation.*;
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
                        AnalyserContext analyserContextInput) {
        super("Type " + typeInfo.simpleName, new ExpandableAnalyserContextImpl(analyserContextInput));
        this.typeInfo = typeInfo;
        this.primaryType = primaryType;
        typeInspection = typeInfo.typeInspection.get();

        typeAnalysis = new TypeAnalysisImpl.Builder(analyserContext.getPrimitives(), typeInfo, analyserContext);
        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>()
                .add("findAspects", iteration -> findAspects())
                .add("analyseImplicitlyImmutableTypes", iteration -> analyseImplicitlyImmutableTypes());

        if (!typeInfo.isInterface()) {
            builder.add("computeApprovedPreconditionsE1", this::computeApprovedPreconditionsE1)
                    .add("computeApprovedPreconditionsE2", this::computeApprovedPreconditionsE2)
                    .add("analyseIndependent", iteration -> analyseIndependent())
                    .add("analyseEffectivelyEventuallyE2Immutable", iteration -> analyseEffectivelyEventuallyE2Immutable())
                    .add("analyseContainer", iteration -> analyseContainer())
                    .add("analyseUtilityClass", iteration -> analyseUtilityClass())
                    .add("analyseSingleton", iteration -> analyseSingleton())
                    .add("analyseExtensionClass", iteration -> analyseExtensionClass());
        } else {
            typeAnalysis.freezeApprovedPreconditionsE1();
            typeAnalysis.freezeApprovedPreconditionsE2();
        }

        analyserComponents = builder.build();

        messages.addAll(typeAnalysis.fromAnnotationsIntoProperties(null,
                AnalyserIdentification.TYPE, typeInfo.isInterface(), typeInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeAnalyser that = (TypeAnalyser) o;
        return typeInfo.equals(that.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
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

        List<MethodAnalyser> myMethodAnalysersExcludingSAMs = new LinkedList<>();
        List<MethodAnalyser> myMethodAnalysers = new LinkedList<>();
        List<MethodAnalyser> myMethodAndConstructorAnalysersExcludingSAMs = new LinkedList<>();
        List<MethodAnalyser> myConstructors = new LinkedList<>();
        List<FieldAnalyser> myFieldAnalysers = new LinkedList<>();

        analyserContext.methodAnalyserStream().forEach(methodAnalyser -> {
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
        analyserContext.fieldAnalyserStream().forEach(fieldAnalyser -> {
            if (fieldAnalyser.fieldInfo.owner == typeInfo) {
                myFieldAnalysers.add(fieldAnalyser);
            }
        });

        this.myMethodAnalysersExcludingSAMs = List.copyOf(myMethodAnalysersExcludingSAMs);
        this.myConstructors = List.copyOf(myConstructors);
        this.myMethodAnalysers = List.copyOf(myMethodAnalysers);
        this.myMethodAndConstructorAnalysersExcludingSAMs = List.copyOf(myMethodAndConstructorAnalysersExcludingSAMs);
        this.myFieldAnalysers = List.copyOf(myFieldAnalysers);

        Either<String, TypeInfo> pe = typeInfo.packageNameOrEnclosingType;
        List<TypeAnalysis> tmp = new ArrayList<>(2);
        if (pe.isRight() && !typeInfo.isStatic()) {
            tmp.add(analyserContext.getTypeAnalysis(pe.getRight()));
        }
        if (!Primitives.isJavaLangObject(typeInspection.parentClass())) {
            TypeAnalyser typeAnalyser = analyserContext.getTypeAnalyser(typeInspection.parentClass().typeInfo);
            tmp.add(typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInspection.parentClass().typeInfo.typeAnalysis.get());
        }
        parentAndOrEnclosingTypeAnalysis = List.copyOf(tmp);
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
        check(typeInfo, ExtensionClass.class, e2.extensionClass);
        check(typeInfo, Independent.class, e2.independent);
        check(typeInfo, Container.class, e2.container);
        check(typeInfo, Singleton.class, e2.singleton);

        CheckE1E2Immutable.check(messages, typeInfo, E1Immutable.class, e2.e1Immutable, typeAnalysis);
        CheckE1E2Immutable.check(messages, typeInfo, E1Container.class, e2.e1Container, typeAnalysis);
        CheckE1E2Immutable.check(messages, typeInfo, E2Immutable.class, e2.e2Immutable, typeAnalysis);
        CheckE1E2Immutable.check(messages, typeInfo, E2Container.class, e2.e2Container, typeAnalysis);

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
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(iteration);
            for (TypeAnalyserVisitor typeAnalyserVisitor : analyserContext.getConfiguration()
                    .debugConfiguration().afterTypePropertyComputations()) {
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

    private AnalysisStatus analyseImplicitlyImmutableTypes() {
        if (typeAnalysis.implicitlyImmutableDataTypes.isSet()) return DONE;

        log(E2IMMUTABLE, "Computing implicitly immutable types for {}", typeInfo.fullyQualifiedName);
        Set<ParameterizedType> typesOfFields = typeInspection.fields().stream()
                .map(fieldInfo -> fieldInfo.type).collect(Collectors.toCollection(HashSet::new));
        typesOfFields.addAll(typeInfo.typesOfMethodsAndConstructors());
        typesOfFields.addAll(typesOfFields.stream().flatMap(pt -> pt.components(false).stream()).collect(Collectors.toList()));
        log(E2IMMUTABLE, "Types of fields, methods and constructors: {}", typesOfFields);

        Map<ParameterizedType, Set<ExplicitTypes.UsedAs>> explicitTypes =
                new ExplicitTypes(analyserContext, analyserContext, typeInspection, typeInfo).getResult();
        Set<ParameterizedType> explicitTypesAsSet = explicitTypes.entrySet().stream()
                .filter(e -> !e.getValue().equals(Set.of(ExplicitTypes.UsedAs.CAST_TO_E2IMMU)))
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        log(E2IMMUTABLE, "Explicit types: {}", explicitTypes);

        typesOfFields.removeIf(type -> {
            if (type.arrays > 0) return true;

            boolean self = type.typeInfo == typeInfo;
            if (self || Primitives.isPrimitiveExcludingVoid(type) || Primitives.isBoxedExcludingVoid(type))
                return true;

            boolean explicit = explicitTypesAsSet.contains(type);
            boolean assignableFrom = !type.isUnboundParameterType() &&
                    explicitTypesAsSet.stream().anyMatch(t -> {
                        try {
                            return type.isAssignableFrom(analyserContext, t);
                        } catch (IllegalStateException illegalStateException) {
                            LOGGER.warn("Cannot determine if {} is assignable from {}", type, t);
                            /* this is a type which is implicitly present somewhere,
                             but not directly in the hierarchy */
                            return false;
                        }
                    });
            return explicit || assignableFrom;
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

        typeAnalysis.implicitlyImmutableDataTypes.set(Set.copyOf(typesOfFields));
        log(E2IMMUTABLE, "Implicitly immutable data types for {} are: [{}]", typeInfo.fullyQualifiedName, typesOfFields);
        return DONE;
    }

    protected Expression getVariableValue(Variable variable) {
        if (variable instanceof DependentVariable) {
            throw new UnsupportedOperationException("NYI");
        }
        if (variable instanceof This) {
            return new VariableExpression(variable);
        }
        throw new UnsupportedOperationException();
    }

    private AnalysisStatus computeApprovedPreconditionsE1(int iteration) {
        if (typeAnalysis.approvedPreconditionsIsFrozen(false)) {
            return DONE;
        }
        Set<MethodAnalyser> assigningMethods = determineAssigningMethods();

        boolean allPreconditionsOnAssigningMethodsSet = assigningMethods.stream()
                .allMatch(methodAnalyser -> methodAnalyser.methodAnalysis.preconditionForEventual.isSet());
        if (!allPreconditionsOnAssigningMethodsSet) {
            log(DELAYED, "Not all precondition preps on assigning methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        Optional<MethodAnalyser> oEmpty = assigningMethods.stream()
                .filter(ma -> ma.methodAnalysis.preconditionForEventual.get().isEmpty())
                .findFirst();
        if (oEmpty.isPresent()) {
            log(MARK, "Not all assigning methods have a valid precondition in {}; (findFirst) {}",
                    typeInfo.fullyQualifiedName, oEmpty.get().methodInfo.fullyQualifiedName);
            typeAnalysis.freezeApprovedPreconditionsE1();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        for (MethodAnalyser methodAnalyser : assigningMethods) {
            Optional<Precondition> precondition = methodAnalyser.methodAnalysis.preconditionForEventual.get();
            if (precondition.isPresent()) {
                List<FieldToCondition> fields = handlePrecondition(methodAnalyser, precondition.get(), iteration);
                if (fields == null) {
                    log(DELAYED, "Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
                    return DELAYS;
                }
                for (FieldToCondition fieldToCondition : fields) {
                    Expression inMap = fieldToCondition.overwrite ?
                            tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                            !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                    tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                    if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                        messages.add(Message.newMessage(new Location(fieldToCondition.fieldReference.fieldInfo),
                                Message.DUPLICATE_MARK_CONDITION, "Field: " + fieldToCondition.fieldReference));
                    }
                }
            }
        }
        tempApproved.putAll(approvedPreconditionsFromParent(typeInfo, false));

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsE1);
        typeAnalysis.freezeApprovedPreconditionsE1();
        log(MARK, "Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return DONE;
    }

    private Map<FieldReference, Expression> approvedPreconditionsFromParent(TypeInfo typeInfo, boolean e2) {
        TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
        if (!Primitives.isJavaLangObject(typeInspection.parentClass())) {
            TypeInfo parent = typeInspection.parentClass().typeInfo;
            TypeAnalysis parentAnalysis = analyserContext.getTypeAnalysis(parent);
            Map<FieldReference, Expression> map = new HashMap<>(parentAnalysis.getApprovedPreconditions(e2));
            map.putAll(approvedPreconditionsFromParent(parent, e2));
            return map;
        }
        return Map.of();
    }

    /*
    all non-private methods which assign a field, or can reach a method that assigns a field

    TODO may be slow, we should cache this?

    Rather not, if we're extending without!
     */
    private Set<MethodAnalyser> determineAssigningMethods() {
        Set<MethodInfo> assigningMethods = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> {
                    StatementAnalysis statementAnalysis = ma.methodAnalysis.getLastStatement();
                    return statementAnalysis != null && statementAnalysis.assignsToFields() &&
                            statementAnalysis.noIncompatiblePrecondition();
                })
                .map(ma -> ma.methodInfo)
                .collect(Collectors.toUnmodifiableSet());

        return myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> !ma.methodInspection.isPrivate())
                .filter(ma -> {
                    StatementAnalysis statementAnalysis = ma.methodAnalysis.getLastStatement();
                    return statementAnalysis != null && statementAnalysis.noIncompatiblePrecondition();
                })
                .filter(ma -> assigningMethods.contains(ma.methodInfo) ||
                        !Collections.disjoint(ma.methodInfo.methodResolution.get().methodsOfOwnClassReached(), assigningMethods))
                .collect(Collectors.toSet());
    }

    /*
          writes: typeAnalysis.approvedPreconditionsE2, the official marker for eventuality in the type

          when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE

         */
    private AnalysisStatus computeApprovedPreconditionsE2(int iteration) {
        if (typeAnalysis.approvedPreconditionsIsFrozen(true)) {
            return DONE;
        }
        Optional<MethodAnalyser> optModificationDelay = myMethodAnalysersExcludingSAMs.stream()
                .filter(methodAnalyser -> !methodAnalyser.methodInfo.isAbstract())
                .filter(methodAnalyser -> methodAnalyser.methodAnalysis
                        .getProperty(VariableProperty.MODIFIED_METHOD) == Level.DELAY).findFirst();
        if (optModificationDelay.isPresent()) {
            log(DELAYED, "Delaying only mark E2, modification delayed of (findFirst) {}",
                    optModificationDelay.get().methodInfo.fullyQualifiedName);
            return DELAYS;
        }

        Optional<MethodAnalyser> preconditionForEventualNotYetSet = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) == Level.TRUE)
                .filter(ma -> !ma.methodAnalysis.preconditionForEventual.isSet())
                .findFirst();
        if (preconditionForEventualNotYetSet.isPresent()) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying; findFirst: {}",
                    typeInfo.fullyQualifiedName, preconditionForEventualNotYetSet.get().methodInfo.fullyQualifiedName);
            return DELAYS;
        }
        Optional<MethodAnalyser> optEmptyPreconditions = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) == Level.TRUE &&
                        ma.methodAnalysis.preconditionForEventual.get().isEmpty())
                .findFirst();
        if (optEmptyPreconditions.isPresent()) {
            log(MARK, "Not all modifying methods have a valid precondition in {}: (findFirst) {}",
                    typeInfo.fullyQualifiedName, optEmptyPreconditions.get().methodInfo.fullyQualifiedName);
            typeAnalysis.freezeApprovedPreconditionsE2();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
            if (modified == Level.TRUE) {
                Optional<Precondition> precondition = methodAnalyser.methodAnalysis.preconditionForEventual.get();
                if (precondition.isPresent()) {
                    List<FieldToCondition> fields = handlePrecondition(methodAnalyser, precondition.get(), iteration);
                    if (fields == null) {
                        log(MARK, "Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
                        return DELAYS;
                    }
                    for (FieldToCondition fieldToCondition : fields) {
                        Expression inMap = fieldToCondition.overwrite ?
                                tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                                !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                        tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                        if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                            messages.add(Message.newMessage(new Location(fieldToCondition.fieldReference.fieldInfo),
                                    Message.DUPLICATE_MARK_CONDITION, "Field: " + fieldToCondition.fieldReference));
                        }
                    }
                }
            }
        }
        tempApproved.putAll(approvedPreconditionsFromParent(typeInfo, true));

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsE2);
        typeAnalysis.freezeApprovedPreconditionsE2();
        log(MARK, "Approved preconditions {} in {}, type can now be @E2Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return DONE;
    }

    private record FieldToCondition(FieldReference fieldReference, Expression condition, Expression negatedCondition,
                                    boolean overwrite) {
    }

    private List<FieldToCondition> handlePrecondition(MethodAnalyser methodAnalyser,
                                                      Precondition precondition,
                                                      int iteration) {
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), null);
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(precondition.expression(),
                filter.individualFieldClause(analyserContext));
        List<FieldToCondition> fieldToConditions = new ArrayList<>();

        for (Map.Entry<FieldReference, Expression> e : filterResult.accepted().entrySet()) {
            Precondition pc = new Precondition(e.getValue(), List.of());
            Boolean isMark = AssignmentIncompatibleWithPrecondition.isMark(analyserContext, pc, methodAnalyser);
            if (isMark == null) return null;
            FieldReference adjustedFieldReference = analyserContext.adjustThis(e.getKey());
            fieldToConditions.add(new FieldToCondition(adjustedFieldReference, e.getValue(),
                    Negation.negate(evaluationContext, e.getValue()), isMark));
        }
        return fieldToConditions;
    }

    private AnalysisStatus analyseContainer() {
        int container = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (container != Level.UNDEFINED) return DONE;

        AnalysisStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.CONTAINER);
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
                    int modified = parameterAnalyser.parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE);
                    if (modified == Level.DELAY && methodAnalyser.hasCode()) {
                        log(DELAYED, "Delaying container, modification of parameter {} undecided",
                                parameterAnalyser.parameterInfo.fullyQualifiedName());
                        return DELAYS; // cannot yet decide
                    }
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

        AnalysisStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.INDEPENDENT);
        if (parentOrEnclosing == DONE || parentOrEnclosing == DELAYS) return parentOrEnclosing;

        boolean variablesLinkedNotSet = myFieldAnalysers.stream()
                .anyMatch(fieldAnalyser -> !fieldAnalyser.fieldAnalysis.linkedVariables.isSet());
        if (variablesLinkedNotSet) {
            log(DELAYED, "Delay independence of type {}, not all variables linked to fields set", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        List<FieldAnalyser> fieldsLinkedToParameters =
                myFieldAnalysers.stream().filter(fieldAnalyser -> fieldAnalyser.fieldAnalysis.getLinkedVariables().variables()
                        .stream().filter(v -> v instanceof ParameterInfo)
                        .map(v -> (ParameterInfo) v).anyMatch(pi -> pi.owner.isConstructor)).collect(Collectors.toList());

        // RULE 1

        boolean modificationStatusUnknown = fieldsLinkedToParameters.stream()
                .anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis
                        .getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD) == Level.DELAY);
        if (modificationStatusUnknown) {
            log(DELAYED, "Delay independence of type {}, modification status of linked fields not yet set", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        boolean someModified = fieldsLinkedToParameters.stream()
                .anyMatch(fieldAnalyser -> fieldAnalyser.fieldAnalysis
                        .getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD) == Level.TRUE);
        if (someModified) {
            log(INDEPENDENT, "Type {} cannot be @Independent, some fields linked to parameters are modified", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
            return DONE;
        }

        // RULE 2

        List<FieldAnalyser> nonPrivateFields = fieldsLinkedToParameters.stream().filter(fieldAnalyser -> fieldAnalyser.fieldInfo.isNotPrivate()).collect(Collectors.toList());
        for (FieldAnalyser nonPrivateField : nonPrivateFields) {
            int immutable = nonPrivateField.fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
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

        Variable thisVariable = new This(analyserContext, typeInfo);
        Set<FieldReference> fieldReferencesLinkedToParameters = fieldsLinkedToParameters.stream()
                .map(fa -> new FieldReference(analyserContext, fa.fieldInfo, thisVariable))
                .collect(Collectors.toSet());

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
                boolean safeMethod = Collections.disjoint(variableInfo.getLinkedVariables().variables(), fieldReferencesLinkedToParameters);
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

    private AnalysisStatus parentOrEnclosingMustHaveTheSameProperty(VariableProperty variableProperty) {
        List<Integer> propertyValues = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> typeAnalysis.getProperty(variableProperty))
                .collect(Collectors.toList());
        if (propertyValues.stream().anyMatch(level -> level == Level.DELAY)) {
            log(DELAYED, "Waiting with {} on {}, parent or enclosing class's status not yet known",
                    variableProperty, typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (propertyValues.stream().anyMatch(level -> level != Level.TRUE)) {
            log(ANALYSER, "{} cannot be {}, parent or enclosing class is not", typeInfo.fullyQualifiedName, variableProperty);
            typeAnalysis.setProperty(variableProperty, variableProperty.falseValue);
            return DONE;
        }
        return PROGRESS;
    }

    private int allMyFieldsFinal() {
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            int effectivelyFinal = fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) {
                log(DELAYED, "Delay on type {}, field {} effectively final not known yet",
                        typeInfo.fullyQualifiedName, fieldAnalyser.fieldInfo.name);
                return Level.DELAY; // cannot decide
            }
            if (effectivelyFinal == Level.FALSE) {
                log(E1IMMUTABLE, "Type {} cannot be @E1Immutable, field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldAnalyser.fieldInfo.name);
                return Level.FALSE;
            }
        }
        return Level.TRUE;
    }

    /**
     * Important to set a value for both E1 immutable and E2 immutable (there is a system to say
     * "it is level 1, but delay on level 2", but WE ARE NOT USING THAT ANYMORE !!)
     * <p>
     * Rules as of 30 July 2020: Definition on top of @E1Immutable
     * <p>
     * RULE 1: All fields must be @NotModified.
     * <p>
     * RULE 2: All fields must be private, or their types must be level 2 immutable or implicitly immutable (replaceable by Object)
     * <p>
     * RULE 3: All methods and constructors must be independent of the non-level2 non implicitly immutable fields
     *
     * @return true if a change was made to typeAnalysis
     */
    private AnalysisStatus analyseEffectivelyEventuallyE2Immutable() {
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (typeImmutable != Level.DELAY) return DONE; // we have a decision already

        // effectively E1
        int allMyFieldsFinal = allMyFieldsFinal();
        if (allMyFieldsFinal == Level.DELAY) {
            return DELAYS;
        }
        TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
        int parentE1;
        if (Primitives.isJavaLangObject(typeInspection.parentClass())) {
            parentE1 = MultiLevel.EFFECTIVE;
        } else {
            TypeInfo parentType = typeInspection.parentClass().typeInfo;
            int parentImmutable = analyserContext.getTypeAnalysis(parentType).getProperty(VariableProperty.IMMUTABLE);
            parentE1 = MultiLevel.value(parentImmutable, MultiLevel.E1IMMUTABLE);
        }

        int fromParentOrEnclosing = parentAndOrEnclosingTypeAnalysis.stream()
                .mapToInt(typeAnalysis -> typeAnalysis.getProperty(VariableProperty.IMMUTABLE)).min()
                .orElse(VariableProperty.IMMUTABLE.best);
        if (fromParentOrEnclosing == Level.DELAY) {
            log(DELAYED, "Waiting with immutable on {} for parent or enclosing types", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (fromParentOrEnclosing == MultiLevel.MUTABLE) {
            log(E2IMMUTABLE, "{} is not an E1Immutable, E2Immutable class, because parent or enclosing is Mutable",
                    typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
            return DONE;
        }

        int myWhenE2Fails;
        int e1Component;
        boolean eventual;
        if (allMyFieldsFinal == Level.FALSE || parentE1 != MultiLevel.EFFECTIVE) {
            if (!typeAnalysis.approvedPreconditionsIsFrozen(false)) {
                log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 1 immutable", typeInfo.fullyQualifiedName);
                return DELAYS;
            }
            boolean isEventuallyE1 = typeAnalysis.approvedPreconditionsIsNotEmpty(false);
            if (!isEventuallyE1 && parentE1 != MultiLevel.EVENTUAL) {
                log(E1IMMUTABLE, "Type {} is not eventually level 1 immutable", typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
                return DONE;
            }
            myWhenE2Fails = MultiLevel.compose(MultiLevel.EVENTUAL, MultiLevel.FALSE);
            e1Component = MultiLevel.EVENTUAL;
            eventual = true;
        } else {
            if (!typeAnalysis.approvedPreconditionsIsFrozen(true)) {
                log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
                return DELAYS;
            }
            myWhenE2Fails = MultiLevel.compose(MultiLevel.EFFECTIVE, MultiLevel.FALSE);
            e1Component = MultiLevel.EFFECTIVE;
            // it is possible that all fields are final, yet some field's content is used as the precondition
            eventual = !typeAnalysis.approvedPreconditionsE2IsEmpty();
        }

        int whenE2Fails = Math.min(fromParentOrEnclosing, myWhenE2Fails);

        // E2
        // NOTE that we need to check 2x: needed in else of previous statement, but also if we get through if-side.
        if (!typeAnalysis.approvedPreconditionsIsFrozen(true)) {
            log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                    " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
            return DELAYS;
        }

        boolean haveToEnforcePrivateAndIndependenceRules = false;
        boolean checkThatTheOnlyModifyingMethodsHaveBeenMarked = false;
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.fieldAnalysis;
            FieldInfo fieldInfo = fieldAnalyser.fieldInfo;
            FieldReference thisFieldInfo = new FieldReference(analyserContext, fieldInfo, new This(analyserContext, typeInfo));
            String fieldFQN = fieldInfo.fullyQualifiedName();

            if (fieldAnalysis.isOfImplicitlyImmutableDataType() == null) {
                log(DELAYED, "Field {} not yet known if implicitly immutable, delaying @E2Immutable on type", fieldFQN);
                return DELAYS;
            }
            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or E2Immutable themselves
            // because of down-casts on non-primitives, e.g. from ImplicitlyImmutable to explicit, we cannot rely on the static type
            boolean isPrimitive = Primitives.isPrimitiveExcludingVoid(fieldInfo.type);

            int fieldImmutable = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
            int fieldE2Immutable = MultiLevel.value(fieldImmutable, MultiLevel.E2IMMUTABLE);

            // field is of the type of the class being analysed... it will not make the difference.
            if (fieldE2Immutable == MultiLevel.DELAY && typeInfo == fieldInfo.type.typeInfo) {
                fieldE2Immutable = MultiLevel.EFFECTIVE;
            }

            // field is of a type that is very closely related to the type being analysed; we're looking to break a delay
            // here by requiring the rules, and saying that it is not eventual; see FunctionInterface_0
            if (fieldE2Immutable == MultiLevel.DELAY) {
                ParameterizedType concreteType = fieldAnalysis.concreteTypeNullWhenDelayed();
                if (concreteType != null && concreteType.typeInfo != null &&
                        concreteType.typeInfo.topOfInterdependentClassHierarchy() == typeInfo.topOfInterdependentClassHierarchy()) {
                    fieldE2Immutable = MultiLevel.EVENTUAL_AFTER; // must follow rules, but is not eventual
                }
            }

            // part of rule 2: we now need to check that @NotModified is on the field
            if (fieldE2Immutable == MultiLevel.DELAY) {
                log(DELAYED, "Field {} not known yet if @E2Immutable, delaying @E2Immutable on type", fieldFQN);
                return DELAYS;
            }

            // NOTE: the 2 values that matter now are EVENTUAL and EFFECTIVE; any other will lead to a field
            // that needs to follow the additional rules

            if (fieldE2Immutable == MultiLevel.EVENTUAL) {
                eventual = true;
                if (!typeAnalysis.eventuallyImmutableFields.contains(fieldInfo)) {
                    typeAnalysis.eventuallyImmutableFields.add(fieldInfo);
                }
            } else if (!isPrimitive) {
                boolean fieldRequiresRules = !fieldAnalysis.isOfImplicitlyImmutableDataType() && fieldE2Immutable != MultiLevel.EFFECTIVE;
                haveToEnforcePrivateAndIndependenceRules |= fieldRequiresRules;

                int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!eventual && modified == Level.DELAY) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldFQN);
                    return DELAYS;
                }
                if (modified == Level.TRUE) {
                    if (eventual) {
                        if (!typeAnalysis.containsApprovedPreconditionsE2(thisFieldInfo)) {
                            log(E2IMMUTABLE, "For {} to become eventually E2Immutable, modified field {} can only be modified in methods marked @Mark or @Only(before=)");
                            checkThatTheOnlyModifyingMethodsHaveBeenMarked = true;
                        }
                    } else {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and its content is modified",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenE2Fails);
                        return DONE;
                    }
                }

                // RULE 2: ALL @SupportData FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenE2Fails);
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
                if (independent == MultiLevel.FALSE) {
                    // TODO break delay if the fields are self-references??
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because constructor is not @Independent",
                            typeInfo.fullyQualifiedName, constructor.methodInfo.name);
                    typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenE2Fails);
                    return DONE;
                }
            }

            for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                if (methodAnalyser.methodInfo.isVoid()) continue; // we're looking at return types
                int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                // in the eventual case, we only need to look at the non-modifying methods
                // calling a modifying method will result in an error
                if (modified == Level.FALSE || !typeAnalysis.isEventual()) {
                    int returnTypeImmutable = methodAnalyser.methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    int returnTypeE2Immutable = MultiLevel.value(returnTypeImmutable, MultiLevel.E2IMMUTABLE);
                    boolean returnTypeNotMyType = typeInfo != analyserContext.getMethodInspection(methodAnalyser.methodInfo).getReturnType().typeInfo;
                    if (returnTypeE2Immutable == MultiLevel.DELAY && returnTypeNotMyType) {
                        log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodAnalyser.methodInfo.distinguishingName());
                        return DELAYS;
                    }
                    if (returnTypeE2Immutable < MultiLevel.EVENTUAL) {
                        // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
                        int independent = methodAnalyser.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
                        if (independent == Level.DELAY) {
                            if (typeContainsMyselfAndE2ImmutableComponents(methodAnalyser.methodInfo.returnType())) {
                                log(E2IMMUTABLE, "Cannot decide if method {} is independent, but given that its return type is a self reference, don't care",
                                        methodAnalyser.methodInfo.fullyQualifiedName);
                            } else {
                                log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                        typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                                return DELAYS; //not decided
                            }
                        }
                        if (independent == MultiLevel.FALSE) {
                            log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                            typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenE2Fails);
                            return DONE;
                        }
                    }
                }
            }
        }

        // IMPROVE I don't think this bit of code is necessary. provide a test
        if (checkThatTheOnlyModifyingMethodsHaveBeenMarked) {
            for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                if (modified == Level.TRUE) {
                    MethodAnalysis.Eventual e = methodAnalyser.methodAnalysis.getEventual();
                    log(DELAYED, "Need confirmation that method {} is @Mark or @Only(before)", methodAnalyser.methodInfo.fullyQualifiedName);
                    if (e == MethodAnalysis.DELAYED_EVENTUAL) return DELAYS;
                    if (e.notMarkOrBefore()) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {} modifies after the precondition",
                                typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenE2Fails);
                        return DONE;
                    }
                }
            }
        }

        log(E2IMMUTABLE, "Improve @Immutable of type {} to @E2Immutable", typeInfo.fullyQualifiedName);
        int e2Component = eventual ? MultiLevel.EVENTUAL : MultiLevel.EFFECTIVE;
        int finalValue = Math.min(fromParentOrEnclosing, MultiLevel.compose(e1Component, e2Component));
        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, finalValue);
        return DONE;
    }

    private boolean typeContainsMyselfAndE2ImmutableComponents(ParameterizedType parameterizedType) {
        return parameterizedType.typeInfo == typeInfo;
        // TODO make more complicated
    }

    /* we will implement two schemas

    1/ no non-private constructors, exactly one occurrence in a non-array type field initialiser, no occurrences
       in any methods
    2/ one constructor, a static boolean with a precondition, which gets flipped inside the constructor

     */
    private AnalysisStatus analyseSingleton() {
        int singleton = typeAnalysis.getProperty(VariableProperty.SINGLETON);
        if (singleton != Level.DELAY) return DONE;

        // system 1: private constructors

        boolean allConstructorsPrivate = typeInspection.constructors().stream().allMatch(MethodInfo::isPrivate);
        if (allConstructorsPrivate) {
            // no method can call the constructor(s)
            boolean doNotCallMyOwnConstructFromMethod = typeInspection.methodStream(TypeInspection.Methods.INCLUDE_SUBTYPES)
                    .allMatch(m -> Collections.disjoint(m.methodResolution.get().methodsOfOwnClassReached(),
                            typeInspection.constructors()));
            if (doNotCallMyOwnConstructFromMethod) {
                // exactly one field has an initialiser with a constructor
                long fieldsWithInitialiser = typeInspection.fields().stream().filter(fieldInfo ->
                        fieldInfo.fieldInspection.get().fieldInitialiserIsSet() &&
                                fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser() instanceof NewObject no &&
                                no.constructor() != null &&
                                typeInspection.constructors().contains(no.constructor())).count();
                if (fieldsWithInitialiser == 1L) {
                    log(SINGLETON, "Type {} is @Singleton, found exactly one new object creation in field initialiser",
                            typeInfo.fullyQualifiedName);
                    typeAnalysis.setProperty(VariableProperty.SINGLETON, Level.TRUE);
                    return DONE;
                }
            }
        }

        // system 2: boolean precondition with static private field, single constructor

        if (typeInspection.constructors().size() == 1) {
            MethodInfo constructor = typeInspection.constructors().get(0);
            MethodAnalyser constructorAnalyser = myConstructors.stream().filter(ma -> ma.methodInfo == constructor).findFirst().orElseThrow();
            MethodAnalysisImpl.Builder constructorAnalysis = constructorAnalyser.methodAnalysis;
            if (!constructorAnalysis.precondition.isSet()) {
                log(DELAYED, "Delaying @Singleton on {} until precondition known");
                return DELAYS;
            }
            Precondition precondition = constructorAnalysis.precondition.get();
            VariableExpression ve;
            if (!precondition.isEmpty() && (ve = variableExpressionOrNegated(precondition.expression())) != null
                    && ve.variable() instanceof FieldReference fr
                    && fr.fieldInfo.isStatic()
                    && fr.fieldInfo.isPrivate()
                    && Primitives.isBoolean(fr.fieldInfo.type)) {
                // one thing that's left is that there is an assignment in the constructor, and no assignment anywhere else
                boolean wantAssignmentToTrue = precondition.expression() instanceof Negation;
                String fieldFqn = ve.variable().fullyQualifiedName();
                boolean notAssignedInMethods = typeInspection.methods().stream().noneMatch(methodInfo -> {
                    MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodInfo);
                    StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                    if (lastStatement == null) return false;
                    VariableInfo variableInfo = lastStatement.getLatestVariableInfo(fieldFqn);
                    if (variableInfo == null) return false;
                    return variableInfo.isAssigned();
                });
                if (notAssignedInMethods) {
                    StatementAnalysis lastStatement = constructorAnalysis.getLastStatement();
                    if (lastStatement == null) throw new UnsupportedOperationException("? have precondition");
                    VariableInfo variableInfo = lastStatement.getLatestVariableInfo(fieldFqn);
                    if (variableInfo == null) throw new UnsupportedOperationException("? have precondition");
                    if (variableInfo.isAssigned() && variableInfo.getValue() instanceof BooleanConstant booleanConstant &&
                            booleanConstant.getValue() == wantAssignmentToTrue) {
                        log(SINGLETON, "Type {} is a  @Singleton, found boolean variable with precondition",
                                typeInfo.fullyQualifiedName);
                        typeAnalysis.setProperty(VariableProperty.SINGLETON, Level.TRUE);
                        return DONE;
                    }
                }
            }
        }

        // no hit
        log(SINGLETON, "Type {} is not a  @Singleton", typeInfo.fullyQualifiedName);
        typeAnalysis.setProperty(VariableProperty.SINGLETON, Level.FALSE);
        return DONE;
    }

    private static VariableExpression variableExpressionOrNegated(Expression expression) {
        if (expression instanceof VariableExpression ve) return ve;
        if (expression instanceof Negation negation && negation.expression instanceof VariableExpression ve) return ve;
        return null;
    }

    private AnalysisStatus analyseExtensionClass() {
        int extensionClass = typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS);
        if (extensionClass != Level.DELAY) return DONE;

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Extension class: don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
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
                int notNull;
                if (methodInfo.hasReturnValue()) {
                    if (parameters.isEmpty()) {
                        MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodInfo);
                        notNull = methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
                    } else {
                        ParameterAnalysis p0 = analyserContext.getParameterAnalysis(parameters.get(0));
                        notNull = p0.getProperty(VariableProperty.NOT_NULL_PARAMETER);
                    }
                    if (notNull == Level.DELAY) {
                        log(DELAYED, "Delaying @ExtensionClass of {} until @NotNull of {} known", typeInfo.fullyQualifiedName,
                                methodInfo.name);
                        return DELAYS;
                    }
                    if (notNull < MultiLevel.EFFECTIVELY_NOT_NULL) {
                        log(EXTENSION_CLASS, "Type {} is not an @ExtensionClass, method {} does not have either a " +
                                        "@NotNull 1st parameter, or no parameters and returns @NotNull.", typeInfo.fullyQualifiedName,
                                methodInfo.name);
                        commonTypeOfFirstParameter = null;
                        break;
                    }
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
            log(DELAYED, "Utility class: Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
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

    /*
    IF there are private and non-private constructors, and there are no factory methods (static
    or non-static methods calling the private constructors) then we assume that the private constructor
    "helps" the non-private ones; their values will therefore be ignored.

    The field analysers will repeatedly call this method, which is rather heavy on the computation;
    therefore the result is cached in the type analysis object.
     */
    public boolean ignorePrivateConstructorsForFieldValue() {
        if (!typeAnalysis.ignorePrivateConstructorsForFieldValues.isSet()) {
            Set<MethodInfo> privateConstructors = new HashSet<>();
            boolean haveNonPrivateConstructors = false;
            for (MethodAnalyser constructorAnalyser : myConstructors) {
                if (constructorAnalyser.methodInfo.isPrivate()) {
                    privateConstructors.add(constructorAnalyser.methodInfo);
                } else {
                    haveNonPrivateConstructors = true;
                }
            }
            boolean ignore;
            if (!haveNonPrivateConstructors || privateConstructors.isEmpty()) {
                ignore = false;
            } else {
                // loop over all methods, ensure that there is no dependency on any of the constructors
                ignore = true;
                for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                    Set<MethodInfo> reached = methodAnalyser.methodInfo.methodResolution.get().methodsOfOwnClassReached();
                    if (!Collections.disjoint(reached, privateConstructors)) {
                        ignore = false;
                        break;
                    }
                }
            }
            typeAnalysis.ignorePrivateConstructorsForFieldValues.set(ignore);
        }
        return typeAnalysis.ignorePrivateConstructorsForFieldValues.get();
    }

    class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            super(iteration, conditionManager, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public String newObjectIdentifier() {
            return typeInfo.fullyQualifiedName;
        }
    }
}
