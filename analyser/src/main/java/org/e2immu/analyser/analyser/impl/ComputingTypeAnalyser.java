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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.Inconclusive;
import org.e2immu.analyser.analyser.delay.NotDelayed;
import org.e2immu.analyser.analyser.impl.util.ComputeTypeImmutable;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.AssignmentIncompatibleWithPrecondition;
import org.e2immu.analyser.analyser.util.ExplicitTypes;
import org.e2immu.analyser.analyser.util.HiddenContentTypes;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.annotation.NotModified;
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.impl.util.ComputeTypeImmutable.correctIndependentFunctionalInterface;
import static org.e2immu.analyser.analyser.impl.util.ComputeTypeImmutable.isOfOwnOrInnerClassType;
import static org.e2immu.analyser.config.AnalyserProgram.Step.ALL;
import static org.e2immu.analyser.config.AnalyserProgram.Step.TRANSPARENT;

/**
 * In the type analysis record we state whether this type has "free fields" or not.
 * Nested types will be allowed in two forms:
 * (1) non-private nested types, where (a) all non-private fields must be @E1Immutable,
 * and (b) access to private methods and fields from enclosing to nested and nested to enclosing is restricted
 * to reading fields and calling @NotModified methods in a direct hierarchical line
 * (2) private subtypes, which do not need to satisfy (1a), and which have the one additional freedom compared to (1b) that
 * the enclosing type can access private fields and methods at will as long as the types are in hierarchical line
 * <p>
 * The 'analyse' and 'check' methods are called independently for types and nested types, in an order of dependence determined
 * by the resolver, but guaranteed such that a nested type will always come before its enclosing type.
 * <p>
 * Therefore, at the end of an enclosing type's analysis, we should have decisions on @NotModified of the methods of the
 * enclosing type, and it should be possible to establish whether a nested type only reads fields (does NOT assign) and
 * calls @NotModified private methods.
 * <p>
 * Errors related to those constraints are added to the type making the violation.
 */

public class ComputingTypeAnalyser extends TypeAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputingTypeAnalyser.class);
    private static final AtomicInteger callCounterForDebugging = new AtomicInteger();

    public static final String ANALYSE_TRANSPARENT_TYPES = "analyseTransparentTypes";
    public static final String ANALYSE_IMMUTABLE_CAN_BE_INCREASED = "analyseImmutableCanBeIncreased";
    public static final String FIND_ASPECTS = "findAspects";
    public static final String COMPUTE_APPROVED_PRECONDITIONS_FINAL_FIELDS = "computeApprovedPreconditionsFinalFields";
    public static final String COMPUTE_APPROVED_PRECONDITIONS_IMMUTABLE = "computeApprovedPreconditionsImmutable";
    public static final String ANALYSE_INDEPENDENT = "analyseIndependent";
    public static final String ANALYSE_IMMUTABLE = "analyseEffectivelyEventuallyImmutable";
    public static final String ANALYSE_CONTAINER = "analyseContainer";
    public static final String ANALYSE_UTILITY_CLASS = "analyseUtilityClass";
    public static final String ANALYSE_SINGLETON = "analyseSingleton";
    public static final String ANALYSE_EXTENSION_CLASS = "analyseExtensionClass";

    // initialized in a separate method
    private List<MethodAnalyser> myMethodAnalysersExcludingSAMs;
    private List<MethodAnalyser> myMethodAndConstructorAnalysersExcludingSAMs;
    private List<MethodAnalyser> myMethodAnalysers;
    private List<MethodAnalyser> myConstructors;
    private List<TypeAnalysis> parentAndOrEnclosingTypeAnalysis;
    private List<FieldAnalyser> myFieldAnalysers;
    private ComputeTypeImmutable computeTypeImmutable;

    private final AnalyserComponents<String, SharedState> analyserComponents;

    public ComputingTypeAnalyser(@NotModified TypeInfo typeInfo,
                                 TypeInfo primaryType,
                                 AnalyserContext analyserContextInput) {
        super(typeInfo, primaryType, analyserContextInput, Analysis.AnalysisMode.COMPUTED);

        AnalyserProgram analyserProgram = analyserContextInput.getAnalyserProgram();
        AnalyserComponents.Builder<String, SharedState> builder = new AnalyserComponents.Builder<String, SharedState>(analyserProgram)
                .add(FIND_ASPECTS, iteration -> findAspects())
                .add(ANALYSE_TRANSPARENT_TYPES, iteration -> analyseExplicitAndHiddenTypes())
                .add(ANALYSE_IMMUTABLE_CAN_BE_INCREASED, iteration -> analyseImmutableDeterminedByTypeParameters());

        if (typeInfo.isInterface()) {
            typeAnalysis.freezeApprovedPreconditionsFinalFields();
            typeAnalysis.freezeApprovedPreconditionsImmutable();
        } else {
            builder.add(COMPUTE_APPROVED_PRECONDITIONS_FINAL_FIELDS, TRANSPARENT, this::computeApprovedPreconditionsFinalFields)
                    .add(COMPUTE_APPROVED_PRECONDITIONS_IMMUTABLE, this::computeApprovedPreconditionsImmutable);
        }
        builder.add(ANALYSE_IMMUTABLE, this::analyseImmutable)
                .add(ANALYSE_INDEPENDENT, this::analyseIndependent)
                .add(ANALYSE_CONTAINER, this::analyseContainer);
        if (!typeInfo.isInterface()) {
            builder.add(ANALYSE_UTILITY_CLASS, iteration -> analyseUtilityClass())
                    .add(ANALYSE_SINGLETON, iteration -> analyseSingleton())
                    .add(ANALYSE_EXTENSION_CLASS, iteration -> analyseExtensionClass());
        }

        analyserComponents = builder.setLimitCausesOfDelay(true).build();
        Analyser.AnalyserIdentification identification = typeInfo.isAbstract()
                ? Analyser.AnalyserIdentification.ABSTRACT_TYPE
                : Analyser.AnalyserIdentification.TYPE;
        analyserResultBuilder.addMessages(typeAnalysis.fromAnnotationsIntoProperties(identification,
                typeInfo.isInterface(),
                typeInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions()));
    }

    private AnalysisStatus analyseImmutable(SharedState sharedState) {
        DV typeImmutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (typeImmutable.isDone()) {
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, typeImmutable);
            return DONE; // we have a decision already
        }
        if (typeInfo.typePropertiesAreContracted()) {
            // make sure this is the same value as in AnnotatedAPIAnalyser.ensureImmutableAndContainerInShallowTypeAnalysis
            typeAnalysis.setProperty(IMMUTABLE, MultiLevel.MUTABLE_DV);
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, MultiLevel.MUTABLE_DV);
            return DONE;
        }
        return computeTypeImmutable.analyseImmutable(sharedState);
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "CTA " + typeInfo.fullyQualifiedName;
    }

    @Override
    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    // slightly ugly code, but speed is of the issue
    @Override
    public void initialize() {
        List<MethodAnalyser> myMethodAnalysersExcludingSAMs = new LinkedList<>();
        List<MethodAnalyser> myMethodAnalysers = new LinkedList<>();
        List<MethodAnalyser> myMethodAndConstructorAnalysersExcludingSAMs = new LinkedList<>();
        List<MethodAnalyser> myConstructors = new LinkedList<>();
        List<FieldAnalyser> myFieldAnalysers = new LinkedList<>();

        analyserContext.methodAnalyserStream().sorted().forEach(methodAnalyser -> {
            if (methodAnalyser.getMethodInfo().typeInfo == typeInfo) {
                if (methodAnalyser.getMethodInfo().isConstructor) {
                    myConstructors.add(methodAnalyser);
                } else {
                    myMethodAnalysers.add(methodAnalyser);
                    if (!methodAnalyser.isSAM()) {
                        myMethodAnalysersExcludingSAMs.add(methodAnalyser);
                    }
                }
                if (!methodAnalyser.isSAM()) {
                    myMethodAndConstructorAnalysersExcludingSAMs.add(methodAnalyser);
                }
            }
        });
        analyserContext.fieldAnalyserStream().sorted().forEach(fieldAnalyser -> {
            if (fieldAnalyser.getFieldInfo().owner == typeInfo) {
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
        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            TypeAnalyser typeAnalyser = analyserContext.getTypeAnalyser(parentClass.typeInfo);
            tmp.add(typeAnalyser != null ? typeAnalyser.getTypeAnalysis() : parentClass.typeInfo.typeAnalysis.get());
        }
        parentAndOrEnclosingTypeAnalysis = List.copyOf(tmp);

        // running this here saves an iteration, especially since the inclusion of currentType
        // in AnalysisProvider.defaultImmutable(...)
        analyseExplicitAndHiddenTypes();

        computeTypeImmutable = new ComputeTypeImmutable(analyserContext, typeInfo, typeInspection, typeAnalysis,
                parentAndOrEnclosingTypeAnalysis, myMethodAnalysers, myConstructors, myFieldAnalysers);
    }

    @Override
    public Stream<MethodAnalyser> allMethodAnalysersIncludingSubTypes() {
        Stream<MethodAnalyser> subTypes = typeInspection.subTypes().stream()
                .flatMap(st -> analyserContext.getTypeAnalyser(st).allMethodAnalysersIncludingSubTypes());
        return Stream.concat(myMethodAnalysers.stream(), subTypes);
    }

    @Override
    public AnalyserResult analyse(SharedState sharedState) {
        assert !isUnreachable();
        int iteration = sharedState.iteration();
        LOGGER.info("Analysing type {}, it {}, call #{}", typeInfo, iteration, callCounterForDebugging.incrementAndGet());
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);
            if (analysisStatus.isDone() && analyserContext.getConfiguration().analyserConfiguration().analyserProgram().accepts(ALL))
                typeAnalysis.internalAllDoneCheck();
            analyserResultBuilder.setAnalysisStatus(analysisStatus);
            for (TypeAnalyserVisitor typeAnalyserVisitor : analyserContext.getConfiguration()
                    .debugConfiguration().afterTypePropertyComputations()) {
                typeAnalyserVisitor.visit(new TypeAnalyserVisitor.Data(iteration,
                        analyserContext.getPrimitives(),
                        typeInfo,
                        analyserContext.getTypeInspection(typeInfo),
                        typeAnalysis,
                        analyserComponents.getStatusesAsMap(),
                        analyserContext));
            }

            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in type analyser: {}", typeInfo);
            throw rte;
        }
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
                        .filter(mi -> mi.action() == CompanionMethodName.Action.ASPECT).toList();
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
                    MethodInfo inMap = typeAnalysis.aspects.get(aspect);
                    if (!inMap.equals(mainMethod)) {
                        throw new UnsupportedOperationException("Duplicating aspect " + aspect + " in " +
                                mainMethod.fullyQualifiedName());
                    }
                }
            }
            LOGGER.debug("Found aspects {} in {}, {}", typeAnalysis.aspects.stream().map(Map.Entry::getKey).collect(Collectors.joining(",")),
                    typeAnalysis, mainMethod);
        }
    }

    /*

     */
    private AnalysisStatus analyseExplicitAndHiddenTypes() {
        if (typeAnalysis.hiddenContentAndExplicitTypeComputationDelays().isDone()) return DONE;

        // STEP 1: Ensure all my static sub-types have been processed, but wait if that's not possible

        if (!typeInspection.subTypes().isEmpty()) {
            // wait until all static subtypes have hidden content computed
            CausesOfDelay delays = typeInspection.subTypes().stream()
                    .filter(TypeInfo::isStatic)
                    .map(st -> {
                        TypeAnalysisImpl.Builder stAna = (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(st);
                        if (stAna.hiddenContentAndExplicitTypeComputationDelays().isDelayed()) {
                            ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(st);
                            typeAnalyser.analyseExplicitAndHiddenTypes();
                        }
                        return stAna.hiddenContentAndExplicitTypeComputationDelays();
                    })
                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
            if (delays.isDelayed()) {
                LOGGER.debug("Hidden content of static nested types of {} not yet known", typeInfo);
                return delays;
            }
        }

        // STEP 2: if I'm a non-static nested type (an inner class) then my enclosing class will take care of me.
        // because I have identical hidden content types.

        if (!typeInspection.isStatic()) {
            TypeInfo staticEnclosing = typeInfo;
            while (!staticEnclosing.isStatic()) {
                staticEnclosing = staticEnclosing.packageNameOrEnclosingType.getRight();
            }
            TypeAnalysisImpl.Builder typeAnalysisStaticEnclosing = (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(staticEnclosing);
            CausesOfDelay delays = typeAnalysisStaticEnclosing.hiddenContentAndExplicitTypeComputationDelays();
            if (delays.isDone()) {
                typeAnalysis.setHiddenContentTypes(typeAnalysisStaticEnclosing.getHiddenContentTypes());
                typeAnalysis.copyExplicitTypes(typeAnalysisStaticEnclosing);
            } else {
                LOGGER.debug("Hidden content of inner class {} computed together with that of enclosing class {}",
                        typeInfo.simpleName, staticEnclosing);
                return delays;
            }
            return DONE;
        }

        // STEP 3: collect from static nested types; we have ensured their presence

        Set<ParameterizedType> explicitTypesFromSubTypes = typeInspection.subTypes().stream()
                .filter(TypeInfo::isStatic)
                .flatMap(st -> {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(st);
                    return typeAnalysis.getExplicitTypes(analyserContext).types().stream();
                }).collect(Collectors.toUnmodifiableSet());

        // STEP 5: ensure + collect from parent

        SetOfTypes explicitTypesFromParent;
        {
            TypeInfo parentClass = typeInspection.parentClass().typeInfo;
            // IMPORTANT: skip aggregated, otherwise infinite loop
            if (parentClass.isJavaLangObject() || parentClass.isAggregated()) {
                explicitTypesFromParent = analyserContext.getPrimitives().explicitTypesOfJLO();
            } else {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parentClass);
                CausesOfDelay delays = typeAnalysis.hiddenContentAndExplicitTypeComputationDelays();
                // third clause to avoid cycles
                if (delays.isDelayed() && typeInfo.primaryType() == parentClass.primaryType() && !typeInfo.isEnclosedIn(parentClass)) {
                    ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(parentClass);
                    typeAnalyser.analyseExplicitAndHiddenTypes();
                }
                CausesOfDelay delays2 = typeAnalysis.hiddenContentAndExplicitTypeComputationDelays();
                if (delays2.isDone()) {
                    explicitTypesFromParent = typeAnalysis.getExplicitTypes(analyserContext);
                } else {
                    LOGGER.debug("Wait for hidden content types to arrive {}, parent {}", typeInfo, parentClass);
                    return delays;
                }
            }
        }

        // STEP 6: ensure + collect interface types

        {
            CausesOfDelay causes = CausesOfDelay.EMPTY;
            for (ParameterizedType ifType : typeInspection.interfacesImplemented()) {
                TypeInfo ifTypeInfo = ifType.typeInfo;
                // 2nd clause to avoid cycles
                if (!ifTypeInfo.isAggregated() && !typeInfo.isEnclosedIn(ifTypeInfo)) {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(ifTypeInfo);
                    CausesOfDelay delays = typeAnalysis.hiddenContentAndExplicitTypeComputationDelays();
                    if (delays.isDelayed() && typeInfo.primaryType() == ifTypeInfo.primaryType()) {
                        ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(ifTypeInfo);
                        typeAnalyser.analyseExplicitAndHiddenTypes();
                        causes = causes.merge(delays);
                    }
                    CausesOfDelay delays2 = typeAnalysis.hiddenContentAndExplicitTypeComputationDelays();
                    if (delays2.isDelayed()) {
                        LOGGER.debug("Wait for hidden content types to arrive {}, interface {}", typeInfo,
                                ifTypeInfo.simpleName);
                        causes = causes.merge(delays2);
                    }
                }
            }
            if (causes.isDelayed()) {
                LOGGER.debug("Delaying hidden content type computation of {}, delays: {}", typeInfo, causes);
                return causes;
            }
        }
        Set<ParameterizedType> explicitTypesFromInterfaces = typeInspection.interfacesImplemented()
                .stream()
                .filter(i -> !i.typeInfo.isAggregated())
                .flatMap(i -> {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(i.typeInfo);
                    SetOfTypes explicitTypes = typeAnalysis.getExplicitTypes(analyserContext);
                    if (explicitTypes == null)
                        return Stream.of(); // FIXME is this correct? if we cause a delay, will it cause cycles?
                    return explicitTypes.types().stream();
                })
                .collect(Collectors.toUnmodifiableSet());

        // STEP 7: start computation

        // first, determine the types of fields, methods and constructors

        // IMPROVE ensure we have all methods, constructors and fields of inner (non-static) nested classes
        Set<ParameterizedType> allTypes = typeInspection.typesOfFieldsMethodsConstructors(analyserContext);

        // add all type parameters of these types
        allTypes.addAll(allTypes.stream().flatMap(pt -> pt.components(false).stream()).toList());
        LOGGER.debug("Types of fields, methods and constructors: {}", allTypes);

        // then, compute the explicit types
        Map<ParameterizedType, Set<ExplicitTypes.UsedAs>> explicitTypes =
                new ExplicitTypes(analyserContext, typeInfo).go(typeInspection).getResult();

        Set<ParameterizedType> allExplicitTypes = new HashSet<>(explicitTypes.keySet());
        allExplicitTypes.addAll(explicitTypesFromInterfaces);
        allExplicitTypes.addAll(explicitTypesFromParent.types());
        allExplicitTypes.addAll(explicitTypesFromSubTypes);

        LOGGER.debug("All explicit types: {}", explicitTypes);

        Set<ParameterizedType> superTypesOfExplicitTypes = allExplicitTypes.stream()
                .flatMap(pt -> pt.concreteSuperTypes(analyserContext))
                .collect(Collectors.toUnmodifiableSet());
        allExplicitTypes.addAll(superTypesOfExplicitTypes);
        typeAnalysis.setExplicitTypes(new SetOfTypes(allExplicitTypes));

        /*
        Now for the computation of hidden content types.
        */
        SetOfTypes typeParameters = new SetOfTypes(typeInspection.typeParameters().stream()
                .map(TypeParameter::toParameterizedType).collect(Collectors.toUnmodifiableSet()));
        SetOfTypes hiddenContentTypes = new HiddenContentTypes(typeInfo, analyserContext).go(typeParameters).build();
        typeAnalysis.setHiddenContentTypes(hiddenContentTypes);

        LOGGER.debug("Transparent data types for {} are: [{}]", typeInfo, allTypes);
        return DONE;
    }

    public static Expression getVariableValue(Variable variable) {
        if (variable instanceof DependentVariable) {
            throw new UnsupportedOperationException("NYI");
        }
        if (variable instanceof This) {
            return new VariableExpression(variable);
        }
        throw new UnsupportedOperationException();
    }

    private AnalysisStatus computeApprovedPreconditionsFinalFields(SharedState sharedState) {
        if (typeAnalysis.approvedPreconditionsStatus(false).isDone()) {
            return DONE;
        }
        Set<MethodAnalyser> assigningMethods = determineAssigningMethods();

        CausesOfDelay delays = assigningMethods.stream()
                .map(methodAnalyser -> methodAnalyser.getMethodAnalysis().getPreconditionForEventual().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delays.isDelayed()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Not all precondition preps on assigning methods have been set in {}, delaying", typeInfo);
                assigningMethods.stream().filter(m -> m.getMethodAnalysis().getPreconditionForEventual().isDelayed())
                        .forEach(m -> LOGGER.debug("--> {}: {}", m, m.getMethodAnalysis().getPreconditionForEventual()
                                .causesOfDelay()));
            }
            typeAnalysis.setApprovedPreconditionsE1Delays(delays);
            return delays;
        }
        Optional<MethodAnalyser> oEmpty = assigningMethods.stream()
                .filter(ma -> ma.getMethodAnalysis().getPreconditionForEventual() != null &&
                        ma.getMethodAnalysis().getPreconditionForEventual().isEmpty())
                .findFirst();
        if (oEmpty.isPresent()) {
            LOGGER.debug("Not all assigning methods have a valid precondition in {}; (findFirst) {}", typeInfo,
                    oEmpty.get());
            typeAnalysis.freezeApprovedPreconditionsFinalFields();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        Map<FieldReference, Set<MethodInfo>> methodsForApprovedField = new HashMap<>();
        for (MethodAnalyser methodAnalyser : assigningMethods) {
            Precondition precondition = methodAnalyser.getMethodAnalysis().getPreconditionForEventual();
            if (precondition != null) {
                HandlePrecondition hp = handlePrecondition(methodAnalyser, precondition, sharedState.iteration());
                if (hp.causesOfDelay.isDelayed()) {
                    LOGGER.debug("Delaying approved preconditions (no incompatible found yet) in {}", typeInfo);
                    typeAnalysis.setApprovedPreconditionsE1Delays(hp.causesOfDelay);
                    return hp.causesOfDelay;
                }
                for (FieldToCondition fieldToCondition : hp.fieldToConditions) {
                    Expression inMap = fieldToCondition.overwrite ?
                            tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                            !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                    tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                    if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                        analyserResultBuilder.add(Message.newMessage
                                (fieldToCondition.fieldReference.fieldInfo.newLocation(),
                                        Message.Label.DUPLICATE_MARK_CONDITION, "Field: " + fieldToCondition.fieldReference));
                    }
                    methodsForApprovedField.merge(fieldToCondition.fieldReference, Set.of(methodAnalyser.getMethodInfo()),
                            (s1, s2) -> Stream.concat(s1.stream(), s2.stream()).collect(Collectors.toUnmodifiableSet()));
                }
            }
        }
        tempApproved.putAll(approvedPreconditionsFromParent(typeInfo, false));

        findFieldsGuardedByEventuallyImmutableFields(tempApproved, methodsForApprovedField);

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsE1);
        typeAnalysis.freezeApprovedPreconditionsFinalFields();
        LOGGER.debug("Approved preconditions {} in {}, type can now be @FinalFields(after=)", tempApproved.values(), typeInfo);
        return DONE;
    }

    private Map<FieldReference, Expression> approvedPreconditionsFromParent(TypeInfo typeInfo, boolean immutable) {
        TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            TypeInfo parent = parentClass.typeInfo;
            TypeAnalysis parentAnalysis = analyserContext.getTypeAnalysis(parent);
            Map<FieldReference, Expression> map = new HashMap<>(parentAnalysis.getApprovedPreconditions(immutable));
            map.putAll(approvedPreconditionsFromParent(parent, immutable));
            return map;
        }
        return Map.of();
    }

    /*
    all non-private methods which assign a field, or can reach a method that assigns a field
     */
    private Set<MethodAnalyser> determineAssigningMethods() {
        return myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> !ma.getMethodInspection().isPrivate())
                .filter(ma -> {
                    StatementAnalysis statementAnalysis = ma.getMethodAnalysis().getLastStatement();
                    return statementAnalysis != null && statementAnalysis.assignsToFields() &&
                            statementAnalysis.noIncompatiblePrecondition();
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    /*
          writes: typeAnalysis.approvedPreconditionsImmutable, the official marker for eventuality in the type

          when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE

         */
    private AnalysisStatus computeApprovedPreconditionsImmutable(SharedState sharedState) {
        if (typeAnalysis.approvedPreconditionsStatus(true).isDone()) {
            return DONE;
        }
        CausesOfDelay modificationDelays = myMethodAnalysersExcludingSAMs.stream()
                .filter(methodAnalyser -> !methodAnalyser.getMethodInfo().methodInspection.get().isAbstract())
                .map(methodAnalyser -> methodAnalyser.getMethodAnalysis()
                        .getProperty(Property.MODIFIED_METHOD_ALT_TEMP).causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        Set<MethodAnalyser> exclude;
        if (modificationDelays.isDelayed()) {
            if (sharedState.allowBreakDelay()) {
                exclude = myMethodAnalysersExcludingSAMs.stream()
                        .filter(methodAnalyser -> !methodAnalyser.getMethodInfo().methodInspection.get().isAbstract())
                        .filter(methodAnalyser -> methodAnalyser.getMethodAnalysis()
                                .getProperty(Property.MODIFIED_METHOD_ALT_TEMP).isDelayed())
                        .collect(Collectors.toUnmodifiableSet());
                LOGGER.debug("Breaking immutable delay by marking excluding methods {}",
                        exclude.stream().map(ma -> ma.getMethodInfo().name).collect(Collectors.joining(", ")));
            } else {
                LOGGER.debug("Delaying only mark immutable in {}, modification delayed", typeInfo);
                typeAnalysis.setApprovedPreconditionsImmutableDelays(modificationDelays);
                return modificationDelays;
            }
        } else {
            exclude = Set.of();
        }

        CausesOfDelay preconditionForEventualDelays = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP).valueIsTrue())
                .filter(ma -> !exclude.contains(ma))
                .map(ma -> ma.getMethodAnalysis().getPreconditionForEventual().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (preconditionForEventualDelays.isDelayed()) {
            LOGGER.debug("Not all precondition preps on modifying methods have been set in {}", typeInfo);
            typeAnalysis.setApprovedPreconditionsImmutableDelays(preconditionForEventualDelays);
            return preconditionForEventualDelays;
        }
        Optional<MethodAnalyser> optEmptyPreconditions = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP).valueIsTrue() &&
                        ma.getMethodAnalysis().getPreconditionForEventual() == null)
                .filter(ma -> !exclude.contains(ma))
                .findFirst();
        if (optEmptyPreconditions.isPresent()) {
            LOGGER.debug("Not all modifying methods have a valid precondition in {}: (findFirst) {}",
                    typeInfo, optEmptyPreconditions.get());
            typeAnalysis.freezeApprovedPreconditionsImmutable();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        Map<FieldReference, Set<MethodInfo>> methodsForApprovedField = new HashMap<>();
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (exclude.contains(methodAnalyser)) continue;
            DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
            if (modified.valueIsTrue()) {
                Precondition precondition = methodAnalyser.getMethodAnalysis().getPreconditionForEventual();
                if (precondition != null) {
                    HandlePrecondition hp = handlePrecondition(methodAnalyser, precondition, sharedState.iteration());
                    if (hp.causesOfDelay.isDelayed()) {
                        LOGGER.debug("Delaying approved preconditions (no incompatible found yet) in {}", typeInfo);
                        typeAnalysis.setApprovedPreconditionsImmutableDelays(hp.causesOfDelay);
                        return hp.causesOfDelay;
                    }
                    for (FieldToCondition fieldToCondition : hp.fieldToConditions) {
                        Expression inMap = fieldToCondition.overwrite ?
                                tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                                !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                        tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                        if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                            analyserResultBuilder.add(Message.newMessage(fieldToCondition.fieldReference.fieldInfo.newLocation(),
                                    Message.Label.DUPLICATE_MARK_CONDITION, fieldToCondition.fieldReference.fullyQualifiedName()));
                        }
                        methodsForApprovedField.merge(fieldToCondition.fieldReference, Set.of(methodAnalyser.getMethodInfo()),
                                (s1, s2) -> Stream.concat(s1.stream(), s2.stream()).collect(Collectors.toUnmodifiableSet()));
                    }
                }
            }
        }
        tempApproved.putAll(approvedPreconditionsFromParent(typeInfo, true));

        // adds them to typeAnalysis.guardedByEvImmFields, not to tempApproved!
        findFieldsGuardedByEventuallyImmutableFields(tempApproved, methodsForApprovedField);

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsImmutable);
        typeAnalysis.freezeApprovedPreconditionsImmutable();
        LOGGER.debug("Approved preconditions {} in {}, type can now be @Immutable(after=)", tempApproved.values(), typeInfo);
        return DONE;
    }

    private void findFieldsGuardedByEventuallyImmutableFields(Map<FieldReference, Expression> tempApproved,
                                                              Map<FieldReference, Set<MethodInfo>> methodsForApprovedField) {
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldInfo fieldInfo = fieldAnalyser.getFieldInfo();
            FieldReference fieldReference = new FieldReference(analyserContext, fieldInfo);
            if (fieldInfo.fieldInspection.get().isPrivate() && !tempApproved.containsKey(fieldReference)) {
                Set<MethodInfo> methodsAssigned;
                DV finalDv = fieldAnalyser.getFieldAnalysis().getProperty(Property.FINAL);
                if (finalDv.valueIsFalse()) {
                    // we have a variable field, without preconditions. Is it guarded by a field with preconditions?
                    // see e.g. EventuallyFinal, where value is only written while covered by the preconditions of isFinal
                    // conditions are: it must be assigned in some method, and the methods it is assigned in must be
                    // contained in those of the field with preconditions
                    methodsAssigned = methodsWhereFieldIsAssigned(fieldInfo);
                } else {
                    assert finalDv.valueIsTrue();
                    methodsAssigned = methodsWhereFieldIsModified(fieldInfo);
                }
                if (!methodsAssigned.isEmpty()) {
                    Optional<FieldReference> guard = methodsForApprovedField.entrySet().stream()
                            .filter(e -> e.getValue().containsAll(methodsAssigned))
                            .map(Map.Entry::getKey)
                            .findFirst();
                    if (guard.isPresent()) {
                        typeAnalysis.addGuardedByEventuallyImmutableField(fieldInfo);
                        LOGGER.debug("Field {} joins the preconditions of guarding field {} in type {}", fieldInfo,
                                guard.get().fieldInfo, typeInfo);
                    }
                }
            }
        }
    }

    private Set<MethodInfo> methodsWhereFieldIsModified(FieldInfo fieldInfo) {
        return myMethodAnalysers.stream()
                .filter(ma -> !ma.getMethodInfo().inConstruction())
                .filter(ma -> ma.getMethodAnalysis().getFieldAsVariable(fieldInfo).stream()
                        .filter(vi -> ComputingMethodAnalyser.connectedToMyTypeHierarchy((FieldReference) vi.variable()).valueIsTrue())
                        .anyMatch(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).valueIsTrue()))
                .map(MethodAnalyser::getMethodInfo)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<MethodInfo> methodsWhereFieldIsAssigned(FieldInfo fieldInfo) {
        return myMethodAnalysers.stream()
                .filter(ma -> !ma.getMethodInfo().inConstruction())
                .filter(ma -> ma.getMethodAnalysis().getFieldAsVariable(fieldInfo).stream()
                        .filter(vi -> ComputingMethodAnalyser.connectedToMyTypeHierarchy((FieldReference) vi.variable()).valueIsTrue())
                        .anyMatch(VariableInfo::isAssigned))
                .map(MethodAnalyser::getMethodInfo)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record HandlePrecondition(List<FieldToCondition> fieldToConditions, CausesOfDelay causesOfDelay) {
    }

    private record FieldToCondition(FieldReference fieldReference, Expression condition, Expression negatedCondition,
                                    boolean overwrite) {
    }

    private HandlePrecondition handlePrecondition(MethodAnalyser methodAnalyser,
                                                  Precondition precondition,
                                                  int iteration) {
        EvaluationResult context = EvaluationResult.from(new EvaluationContextImpl(iteration, false,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), null));
        Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(precondition.expression(),
                filter.individualFieldClause(analyserContext));
        List<FieldToCondition> fieldToConditions = new ArrayList<>();
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;

        for (Map.Entry<FieldReference, Expression> e : filterResult.accepted().entrySet()) {
            Precondition pc = new Precondition(e.getValue(), List.of());
            DV isMark = AssignmentIncompatibleWithPrecondition.isMark(analyserContext, pc, methodAnalyser);
            if (isMark.isDelayed()) {
                causesOfDelay = causesOfDelay.merge(isMark.causesOfDelay());
            } else {
                fieldToConditions.add(new FieldToCondition(e.getKey(), e.getValue(),
                        Negation.negate(context, e.getValue()), isMark.valueIsTrue()));
            }
        }
        return new HandlePrecondition(fieldToConditions, causesOfDelay);
    }

    private AnalysisStatus analyseContainer(SharedState sharedState) {
        DV container = typeAnalysis.getProperty(CONTAINER);
        if (container.isDone()) {
            return DONE;
        }
        if (typeInfo.typePropertiesAreContracted()) {
            // make sure this is the same value as in AnnotatedAPIAnalyser.ensureImmutableAndContainerInShallowTypeAnalysis
            typeAnalysis.setProperty(CONTAINER, MultiLevel.NOT_CONTAINER_DV);
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
            return DONE;
        }

        Property ALT_CONTAINER;
        AnalysisStatus ALT_DONE;

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(CONTAINER);
        if (MARKER != parentOrEnclosing.status) {
            if (parentOrEnclosing.status.isDelayed()) {
                ALT_CONTAINER = Property.PARTIAL_CONTAINER;
                ALT_DONE = AnalysisStatus.of(parentOrEnclosing.status.causesOfDelay());
            } else {
                DV current = typeAnalysis.getProperty(CONTAINER);
                assert current.isDone();
                typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_CONTAINER, current);
                return parentOrEnclosing.status;
            }
        } else {
            ALT_CONTAINER = CONTAINER;
            ALT_DONE = DONE;
        }

        CausesOfDelay allCauses = CausesOfDelay.EMPTY;
        for (MethodAnalyser methodAnalyser : myMethodAndConstructorAnalysersExcludingSAMs) {
            if (methodAnalyser.getMethodInfo().isAccessibleOutsidePrimaryType()) {
                for (ParameterInfo parameterInfo : methodAnalyser.getMethodInspection().getParameters()) {
                    ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                    DV modified = parameterAnalysis.getProperty(Property.MODIFIED_VARIABLE);
                    if (modified.isDelayed() && methodAnalyser.hasCode()) {
                        LOGGER.debug("Delaying container, modification of parameter {} undecided", parameterInfo);
                        allCauses = allCauses.merge(modified.causesOfDelay());
                    }
                    if (modified.valueIsTrue()) {
                        LOGGER.debug("{} is not a @Container: the content of {} is modified in {}",
                                typeInfo, parameterInfo, methodAnalyser);
                        typeAnalysis.setProperty(CONTAINER, MultiLevel.NOT_CONTAINER_DV);
                        typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
                        return DONE;
                    }
                }
            }
        }
        if (allCauses.isDelayed()) {
            if (sharedState.allowBreakDelay()) {
                LOGGER.debug("Breaking delay in @Container on type {}", typeInfo);
                typeAnalysis.setProperty(CONTAINER, MultiLevel.NOT_CONTAINER_INCONCLUSIVE);
                typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_CONTAINER, MultiLevel.NOT_CONTAINER_INCONCLUSIVE);
                return DONE;
            }
            CausesOfDelay marker = typeInfo.delay(CauseOfDelay.Cause.CONTAINER);
            CausesOfDelay merge = allCauses.causesOfDelay().merge(marker);
            typeAnalysis.setProperty(CONTAINER, merge);
            if (ALT_CONTAINER != CONTAINER) {
                typeAnalysis.setProperty(ALT_CONTAINER, merge);
            }
            LOGGER.debug("Delaying container {}, delays: {}", typeInfo, merge);
            return AnalysisStatus.of(merge);
        }
        typeAnalysis.setProperty(ALT_CONTAINER, MultiLevel.CONTAINER_DV);
        LOGGER.debug("Mark {} as {}", typeInfo.fullyQualifiedName, ALT_CONTAINER);
        if (ALT_CONTAINER == CONTAINER) {
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_CONTAINER, MultiLevel.CONTAINER_DV);
        }
        return ALT_DONE;
    }

    /**
     * Compute:
     * <p>
     * ZERO: the minimal value of parent classes and interfaces.
     * <p>
     * ONE: The minimum independence value of all return values and parameters of non-private methods or constructors
     * is computed.
     * <p>
     * TWO: any non-private field that is not immutable is dependent; if it is immutable, the hidden content
     * is transferred.
     * <p>
     * Return the minimum value of ZERO, ONE and TWO.
     *
     * @return true if a decision was made
     */
    private AnalysisStatus analyseIndependent(SharedState sharedState) {
        DV typeIndependent = typeAnalysis.getProperty(Property.INDEPENDENT);
        if (typeIndependent.isDone()) return DONE;
        if (typeInfo.typePropertiesAreContracted()) {
            Message message = AnnotatedAPIAnalyser.simpleComputeIndependent(analyserContext, typeAnalysis,
                    m -> !m.methodInspection.get().isPrivate());
            if (message != null) analyserResultBuilder.add(message);
            return DONE;
        }

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(Property.INDEPENDENT);
        if (MARKER != parentOrEnclosing.status) return parentOrEnclosing.status;

        boolean inconclusive = false;
        DV valueFromFields = myFieldAnalysers.stream()
                .filter(fa -> !fa.getFieldInfo().fieldInspection.get().isPrivate())
                .filter(fa -> !typeInfo.isMyself(fa.getFieldInfo().type, InspectionProvider.DEFAULT))
                .map(fa -> independenceOfField(fa.getFieldAnalysis()))
                .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
        if (valueFromFields.isDelayed()) {
            if (sharedState.allowBreakDelay()) {
                valueFromFields = myFieldAnalysers.stream()
                        .filter(fa -> !fa.getFieldInfo().fieldInspection.get().isPrivate())
                        .filter(fa -> !typeInfo.isMyself(fa.getFieldInfo().type, InspectionProvider.DEFAULT))
                        .map(fa -> independenceOfField(fa.getFieldAnalysis()))
                        .filter(DV::isDone)
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                LOGGER.debug("Breaking delay in INDEPENDENT of type {}, ignoring some fields", typeInfo);
                inconclusive = true;
            } else {
                LOGGER.debug("Independence of type {} delayed, waiting for field independence", typeInfo);
                return delayIndependent(valueFromFields.causesOfDelay());
            }
        }
        DV valueFromMethodParameters;
        if (valueFromFields.equals(MultiLevel.DEPENDENT_DV)) {
            valueFromMethodParameters = MultiLevel.DEPENDENT_DV; // no need to compute anymore, at bottom anyway
        } else {
            valueFromMethodParameters = myMethodAndConstructorAnalysersExcludingSAMs.stream()
                    .filter(ma -> !analyserContext.getMethodInspection(ma.getMethodInfo()).isPrivate())
                    .flatMap(ma -> ma.getParameterAnalyses().stream())
                    .map(pa -> correctIndependentFunctionalInterface(pa, pa.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT)))
                    .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            if (valueFromMethodParameters.isDelayed()) {
                if (sharedState.allowBreakDelay()) {
                    valueFromMethodParameters = myMethodAndConstructorAnalysersExcludingSAMs.stream()
                            .filter(ma -> !analyserContext.getMethodInspection(ma.getMethodInfo()).isPrivate())
                            .flatMap(ma -> ma.getParameterAnalyses().stream())
                            .map(pa -> correctIndependentFunctionalInterface(pa, pa.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT)))
                            .filter(DV::isDone)
                            .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                    LOGGER.debug("Breaking delay in INDEPENDENT of type {}, ignoring some method parameters", typeInfo);
                    inconclusive = true;
                } else {
                    LOGGER.debug("Independence of type {} delayed, waiting for parameter independence", typeInfo);
                    return delayIndependent(valueFromMethodParameters.causesOfDelay());
                }
            }
        }
        DV valueFromMethodReturnValue;
        if (valueFromMethodParameters.equals(MultiLevel.DEPENDENT_DV)) {
            valueFromMethodReturnValue = MultiLevel.DEPENDENT_DV;
        } else {
            valueFromMethodReturnValue = myMethodAnalysersExcludingSAMs.stream()
                    .filter(ma -> !ma.getMethodInfo().methodInspection.get().isPrivate()
                            && ma.getMethodInfo().hasReturnValue()
                            && !isOfOwnOrInnerClassType(ma.getMethodInspection().getReturnType(), typeInfo))
                    .map(ma -> ma.getMethodAnalysis().getProperty(Property.INDEPENDENT))
                    .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            if (valueFromMethodReturnValue.isDelayed()) {
                if (sharedState.allowBreakDelay()) {
                    valueFromMethodReturnValue = myMethodAnalysersExcludingSAMs.stream()
                            .filter(ma -> !ma.getMethodInfo().methodInspection.get().isPrivate()
                                    && ma.getMethodInfo().hasReturnValue()
                                    && !isOfOwnOrInnerClassType(ma.getMethodInspection().getReturnType(), typeInfo))
                            .map(ma -> ma.getMethodAnalysis().getProperty(Property.INDEPENDENT))
                            .filter(DV::isDone)
                            .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                    LOGGER.debug("Breaking delay in INDEPENDENT of type {}, ignoring some methods", typeInfo);
                    inconclusive = true;
                } else {
                    LOGGER.debug("Independence of type {} delayed, waiting for method independence", typeInfo);
                    return delayIndependent(valueFromMethodReturnValue.causesOfDelay());
                }
            }
        }
        DV finalValue = parentOrEnclosing.maxValue
                .min(valueFromMethodReturnValue)
                .min(valueFromFields)
                .min(valueFromMethodParameters);
        assert finalValue.isDone();

        DV potentiallyInconclusive;
        if (inconclusive) {
            potentiallyInconclusive = new Inconclusive(finalValue);
            LOGGER.debug("Setting inconclusive INDEPENDENT value for type {}: {}", typeInfo, potentiallyInconclusive);
        } else {
            potentiallyInconclusive = finalValue;
            LOGGER.debug("Set independence of type {} to {}", typeInfo, potentiallyInconclusive);
        }
        typeAnalysis.setProperty(Property.INDEPENDENT, potentiallyInconclusive);
        return DONE;
    }

    private AnalysisStatus delayIndependent(CausesOfDelay causesOfDelay) {
        typeAnalysis.setProperty(INDEPENDENT, causesOfDelay);
        return causesOfDelay;
    }

    /*
    The independence of a non-private field is completely determined by its immutability,
    no need to look at the INDEPENDENT property, which has a different meaning (i.e., the independence
    traveling from method or constructor parameters)
     */
    private DV independenceOfField(FieldAnalysis fieldAnalysis) {
        DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        if (immutable.lt(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV)) return MultiLevel.DEPENDENT_DV;
        TypeInfo bestType = fieldAnalysis.getFieldInfo().type.bestTypeInfo(analyserContext);
        if (bestType == null) {
            // unbound type parameter
            return MultiLevel.INDEPENDENT_HC_DV;
        }
        int immutableLevel = MultiLevel.level(immutable);
        return MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
    }

    private record MaxValueStatus(DV maxValue, AnalysisStatus status) {
    }

    private static final AnalysisStatus MARKER = new NotDelayed(4, "MARKER"); // temporary marker

    private MaxValueStatus parentOrEnclosingMustHaveTheSameProperty(Property property) {
        DV[] propertyValues = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> typeAnalysis.getProperty(property))
                .toArray(DV[]::new);
        if (propertyValues.length == 0) return new MaxValueStatus(property.bestDv, MARKER);
        DV min = Arrays.stream(propertyValues).reduce(DV.MAX_INT_DV, DV::min);
        if (min.isDelayed()) {
            LOGGER.debug("Waiting with {} on {}, parent or enclosing class's status not yet known", property, typeInfo);
            typeAnalysis.setProperty(property, min.causesOfDelay());
            return new MaxValueStatus(min, min.causesOfDelay());
        }
        if (min.equals(property.falseDv)) {
            LOGGER.debug("{} set to least value for {}, because of parent", typeInfo, property);
            typeAnalysis.setProperty(property, property.falseDv);
            return new MaxValueStatus(property.falseDv, DONE);
        }
        return new MaxValueStatus(min, MARKER);
    }


    /* we will implement two schemas

    1/ no non-private constructors, exactly one occurrence in a non-array type field initialiser, no occurrences
       in any methods
    2/ one constructor, a static boolean with a precondition, which gets flipped inside the constructor

     */
    private AnalysisStatus analyseSingleton() {
        DV singleton = typeAnalysis.getProperty(Property.SINGLETON);
        if (singleton.isDone()) return DONE;

        // system 1: private constructors

        boolean allConstructorsPrivate = typeInspection.constructors().stream()
                .allMatch(m -> m.methodInspection.get().isPrivate());
        if (allConstructorsPrivate) {
            // no method can call the constructor(s)
            boolean doNotCallMyOwnConstructFromMethod = typeInspection.methodStream(TypeInspection.Methods.INCLUDE_SUBTYPES)
                    .allMatch(m -> Collections.disjoint(m.methodResolution.get().methodsOfOwnClassReached(),
                            typeInspection.constructors()));
            if (doNotCallMyOwnConstructFromMethod) {
                // exactly one field has an initialiser with a constructor
                long fieldsWithInitialiser = typeInspection.fields().stream().filter(fieldInfo -> {
                    ConstructorCall cc;
                    return fieldInfo.fieldInspection.get().fieldInitialiserIsSet() &&
                            (cc = fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser().asInstanceOf(ConstructorCall.class)) != null &&
                            cc.constructor() != null &&
                            typeInspection.constructors().contains(cc.constructor());
                }).count();
                if (fieldsWithInitialiser == 1L) {
                    LOGGER.debug("Type {} is @Singleton, found exactly one new object creation in field initialiser",
                            typeInfo);
                    typeAnalysis.setProperty(Property.SINGLETON, DV.TRUE_DV);
                    return DONE;
                }
            }
        }

        // system 2: boolean precondition with static private field, single constructor

        if (typeInspection.constructors().size() == 1) {
            MethodInfo constructor = typeInspection.constructors().get(0);
            MethodAnalyser constructorAnalyser = myConstructors.stream().filter(ma -> ma.getMethodInfo() == constructor).findFirst().orElseThrow();
            MethodAnalysis constructorAnalysis = constructorAnalyser.getMethodAnalysis();
            CausesOfDelay preconditionDelays = constructorAnalysis.preconditionStatus();
            if (preconditionDelays.isDelayed()) {
                LOGGER.debug("Delaying @Singleton on {} until precondition known", typeInfo);
                return preconditionDelays;
            }
            Precondition precondition = constructorAnalysis.getPrecondition();
            VariableExpression ve;
            if (!precondition.isEmpty() && (ve = variableExpressionOrNegated(precondition.expression())) != null
                    && ve.variable() instanceof FieldReference fr
                    && fr.fieldInfo.fieldInspection.get().isStatic()
                    && fr.fieldInfo.fieldInspection.get().isPrivate()
                    && fr.fieldInfo.type.isBoolean()) {
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
                        LOGGER.debug("Type {} is a  @Singleton, found boolean variable with precondition", typeInfo);
                        typeAnalysis.setProperty(Property.SINGLETON, DV.TRUE_DV);
                        return DONE;
                    }
                }
            }
        }

        // no hit
        LOGGER.debug("Type {} is not a  @Singleton", typeInfo);
        typeAnalysis.setProperty(Property.SINGLETON, DV.FALSE_DV);
        return DONE;
    }

    private static VariableExpression variableExpressionOrNegated(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) return ve;
        if (expression instanceof Negation negation &&
                ((ve = negation.expression.asInstanceOf(VariableExpression.class)) != null)) return ve;
        return null;
    }

    private AnalysisStatus analyseExtensionClass() {
        DV extensionClass = typeAnalysis.getProperty(Property.EXTENSION_CLASS);
        if (extensionClass.isDone()) return DONE;

        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) {
            LOGGER.debug("Extension class: don't know yet about @Immutable on {}, delaying", typeInfo);
            return immutable.causesOfDelay();
        }
        if (immutable.lt(MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV)) {
            LOGGER.debug("Type {} is not an @ExtensionClass, not (eventually) @Immutable", typeInfo);
            typeAnalysis.setProperty(Property.EXTENSION_CLASS, DV.FALSE_DV);
            return DONE;
        }

        boolean haveFirstParameter = false;
        ParameterizedType commonTypeOfFirstParameter = null;
        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (methodInfo.methodInspection.get().isStatic() && !methodInfo.methodInspection.get().isPrivate()) {
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
                    LOGGER.debug("Type {} is not an @ExtensionClass, it has no common type for the first " +
                                    "parameter (or return type, if no parameters) of static methods, seeing {} vs {}",
                            typeInfo, commonTypeOfFirstParameter.detailedString(), typeOfFirstParameter.detailedString());
                    commonTypeOfFirstParameter = null;
                    break;
                }
                DV notNull;
                if (methodInfo.hasReturnValue()) {
                    if (parameters.isEmpty()) {
                        MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodInfo);
                        notNull = methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION);
                    } else {
                        ParameterAnalysis p0 = analyserContext.getParameterAnalysis(parameters.get(0));
                        notNull = p0.getProperty(Property.NOT_NULL_PARAMETER);
                    }
                    if (notNull.isDelayed()) {
                        LOGGER.debug("Delaying @ExtensionClass of {} until @NotNull of {} known", typeInfo, methodInfo);
                        return notNull.causesOfDelay();
                    }
                    if (notNull.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                        LOGGER.debug("Type {} is not an @ExtensionClass, method {} does not have either a " +
                                        "@NotNull 1st parameter, or no parameters and returns @NotNull", typeInfo,
                                methodInfo);
                        commonTypeOfFirstParameter = null;
                        break;
                    }
                }
            }
        }
        boolean isExtensionClass = commonTypeOfFirstParameter != null && haveFirstParameter;
        typeAnalysis.setProperty(Property.EXTENSION_CLASS, DV.fromBoolDv(isExtensionClass));
        LOGGER.debug("Type {} marked {} @ExtensionClass", typeInfo, isExtensionClass ? "" : "not ");
        return DONE;
    }

    /*
    two criteria implemented:
    1- recursively immutable
    2- no public means of generating instances through constructors or methods
     */
    private AnalysisStatus analyseUtilityClass() {
        DV utilityClass = typeAnalysis.getProperty(Property.UTILITY_CLASS);
        if (utilityClass.isDone()) return DONE;

        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) {
            LOGGER.debug("Utility class: Don't know yet about @Immutable on {}, delaying", typeInfo);
            return immutable.causesOfDelay();
        }
        if (MultiLevel.EFFECTIVELY_IMMUTABLE_DV.equals(immutable)) {
            LOGGER.debug("Type {} is not a @UtilityClass, not recursively @Immutable", typeInfo);
            typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
            return DONE;
        }

        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInspection.constructors()) {
            if (!constructor.methodInspection.get().isPrivate()) {
                LOGGER.debug("Type {} looks like a @UtilityClass, but its constructors are not all private", typeInfo);
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }

        if (typeInspection.constructors().isEmpty()) {
            LOGGER.debug("Type {} is not a @UtilityClass: it has no private constructors", typeInfo);
            typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
            return DONE;
        }

        // and there should be no means of generating an object: these private constructors are never called!
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (methodAnalyser.getMethodInfo().methodResolution.get()
                    .methodsOfOwnClassReached().stream().anyMatch(m -> m.isConstructor && m.typeInfo == typeInfo)) {
                LOGGER.debug("Type {} looks like a @UtilityClass, but an object of the class is created in method {}",
                        typeInfo, methodAnalyser);
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldAnalyser.getFieldInfo().fieldInspection.get()
                    .getFieldInitialiser();
            if (fieldInitialiser.initialiser() instanceof ConstructorCall cc && cc.constructor().typeInfo == this.typeInfo) {
                LOGGER.debug("Type {} looks like a @UtilityClass, but an object of the class is created in field {}",
                        typeInfo, fieldAnalyser);
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }

        typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.TRUE_DV);
        LOGGER.debug("Type {} marked @UtilityClass", typeInfo);
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
                if (constructorAnalyser.getMethodInfo().methodInspection.get().isPrivate()) {
                    privateConstructors.add(constructorAnalyser.getMethodInfo());
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
                    Set<MethodInfo> reached = methodAnalyser.getMethodInfo().methodResolution.get().methodsOfOwnClassReached();
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

        protected EvaluationContextImpl(int iteration,
                                        boolean allowBreakDelay,
                                        ConditionManager conditionManager,
                                        EvaluationContext closure) {
            super(closure == null ? 1 : closure.getDepth() + 1, iteration, allowBreakDelay, conditionManager, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }
    }
}
