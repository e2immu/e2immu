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
import org.e2immu.analyser.analyser.delay.NotDelayed;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.AssignmentIncompatibleWithPrecondition;
import org.e2immu.analyser.analyser.util.ExplicitTypes;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
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
    public static final String COMPUTE_APPROVED_PRECONDITIONS_E1 = "computeApprovedPreconditionsE1";
    public static final String COMPUTE_APPROVED_PRECONDITIONS_E2 = "computeApprovedPreconditionsE2";
    public static final String ANALYSE_INDEPENDENT = "analyseIndependent";
    public static final String ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE = "analyseEffectivelyEventuallyE2Immutable";
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

    private final AnalyserComponents<String, Integer> analyserComponents;

    public ComputingTypeAnalyser(@NotModified TypeInfo typeInfo,
                                 TypeInfo primaryType,
                                 AnalyserContext analyserContextInput) {
        super(typeInfo, primaryType, analyserContextInput, Analysis.AnalysisMode.COMPUTED);

        AnalyserProgram analyserProgram = analyserContextInput.getAnalyserProgram();
        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>(analyserProgram)
                .add(FIND_ASPECTS, iteration -> findAspects())
                .add(ANALYSE_TRANSPARENT_TYPES, iteration -> analyseTransparentTypes())
                .add(ANALYSE_IMMUTABLE_CAN_BE_INCREASED, iteration -> analyseImmutableCanBeIncreasedByTypeParameters());

        if (!typeInfo.isInterface()) {
            builder.add(COMPUTE_APPROVED_PRECONDITIONS_E1, TRANSPARENT, this::computeApprovedPreconditionsE1)
                    .add(COMPUTE_APPROVED_PRECONDITIONS_E2, this::computeApprovedPreconditionsE2)
                    .add(ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE, iteration -> analyseEffectivelyEventuallyE2Immutable())
                    .add(ANALYSE_INDEPENDENT, iteration -> analyseIndependent())
                    .add(ANALYSE_CONTAINER, iteration -> analyseContainer())
                    .add(ANALYSE_UTILITY_CLASS, iteration -> analyseUtilityClass())
                    .add(ANALYSE_SINGLETON, iteration -> analyseSingleton())
                    .add(ANALYSE_EXTENSION_CLASS, iteration -> analyseExtensionClass());
        } else {
            typeAnalysis.freezeApprovedPreconditionsE1();
            typeAnalysis.freezeApprovedPreconditionsE2();
        }

        analyserComponents = builder.setLimitCausesOfDelay(true).build();

        analyserResultBuilder.addMessages(typeAnalysis.fromAnnotationsIntoProperties(AnalyserIdentification.TYPE,
                typeInfo.isInterface(),
                typeInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions()));
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "CTA " + typeInfo.fullyQualifiedName;
    }

    @Override
    public AnalyserComponents<String, Integer> getAnalyserComponents() {
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
    }

    @Override
    public AnalyserResult analyse(int iteration, EvaluationContext closure) {
        assert !isUnreachable();
        LOGGER.info("Analysing type {}, it {}, call #{}", typeInfo.fullyQualifiedName, iteration,
                callCounterForDebugging.incrementAndGet());
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(iteration);
            if (analysisStatus.isDone() && analyserContext.getConfiguration().analyserConfiguration().analyserProgram().accepts(ALL)) typeAnalysis.internalAllDoneCheck();
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
            LOGGER.warn("Caught exception in type analyser: {}", typeInfo.fullyQualifiedName);
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
                    throw new UnsupportedOperationException("Duplicating aspect " + aspect + " in " +
                            mainMethod.fullyQualifiedName());
                }
            }
            LOGGER.debug("Found aspects {} in {}, {}", typeAnalysis.aspects.stream().map(Map.Entry::getKey).collect(Collectors.joining(",")),
                    typeAnalysis.typeInfo.fullyQualifiedName, mainMethod.fullyQualifiedName);
        }
    }

    /*

     */
    private AnalysisStatus analyseTransparentTypes() {
        if (typeAnalysis.hiddenContentTypeStatus().isDone()) return DONE;

        // STEP 1: Ensure all my static sub-types have been processed, but wait if that's not possible

        if (!typeInspection.subTypes().isEmpty()) {
            // wait until all static subtypes have hidden content computed
            CausesOfDelay delays = typeInspection.subTypes().stream()
                    .filter(TypeInfo::isStatic)
                    .map(st -> {
                        TypeAnalysisImpl.Builder stAna = (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(st);
                        if (stAna.hiddenContentTypeStatus().isDelayed()) {
                            ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(st);
                            typeAnalyser.analyseTransparentTypes();
                        }
                        return stAna.hiddenContentTypeStatus();
                    })
                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
            if (delays.isDelayed()) {
                LOGGER.debug("Hidden content of static nested types of {} not yet known", typeInfo.fullyQualifiedName);
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
            CausesOfDelay delays = typeAnalysisStaticEnclosing.hiddenContentTypeStatus();
            if (delays.isDone()) {
                typeAnalysis.setTransparentTypes(typeAnalysisStaticEnclosing.getTransparentTypes());
                typeAnalysis.copyExplicitTypes(typeAnalysisStaticEnclosing);
            } else {
                LOGGER.debug("Hidden content of inner class {} computed together with that of enclosing class {}",
                        typeInfo.simpleName, staticEnclosing.fullyQualifiedName);
                return delays;
            }
            return DONE;
        }

        LOGGER.debug("Computing transparent types for type {}", typeInfo.fullyQualifiedName);

        // STEP 3: collect from static nested types; we have ensured their presence

        Set<ParameterizedType> explicitTypesFromSubTypes = typeInspection.subTypes().stream()
                .filter(TypeInfo::isStatic)
                .flatMap(st -> {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(st);
                    return typeAnalysis.getExplicitTypes(analyserContext).stream();
                }).collect(Collectors.toUnmodifiableSet());

        // STEP 5: ensure + collect from parent

        Set<ParameterizedType> explicitTypesFromParent;
        {
            TypeInfo parentClass = typeInspection.parentClass().typeInfo;
            // IMPORTANT: skip aggregated, otherwise infinite loop
            if (parentClass.isJavaLangObject() || parentClass.isAggregated()) {
                explicitTypesFromParent = analyserContext.getPrimitives().explicitTypesOfJLO();
            } else {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parentClass);
                CausesOfDelay delays = typeAnalysis.hiddenContentTypeStatus();
                // third clause to avoid cycles
                if (delays.isDelayed() && typeInfo.primaryType() == parentClass.primaryType() && !typeInfo.isEnclosedIn(parentClass)) {
                    ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(parentClass);
                    typeAnalyser.analyseTransparentTypes();
                }
                CausesOfDelay delays2 = typeAnalysis.hiddenContentTypeStatus();
                if (delays2.isDone()) {
                    explicitTypesFromParent = typeAnalysis.getExplicitTypes(analyserContext);
                } else {
                    LOGGER.debug("Wait for hidden content types to arrive {}, parent {}", typeInfo.fullyQualifiedName,
                            parentClass.simpleName);
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
                    CausesOfDelay delays = typeAnalysis.hiddenContentTypeStatus();
                    if (delays.isDelayed() && typeInfo.primaryType() == ifTypeInfo.primaryType()) {
                        ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(ifTypeInfo);
                        typeAnalyser.analyseTransparentTypes();
                        causes = causes.merge(delays);
                    }
                    CausesOfDelay delays2 = typeAnalysis.hiddenContentTypeStatus();
                    if (delays2.isDelayed()) {
                        LOGGER.debug("Wait for hidden content types to arrive {}, interface {}", typeInfo.fullyQualifiedName,
                                ifTypeInfo.simpleName);
                        causes = causes.merge(delays2);
                    }
                }
            }
            if (causes.isDelayed()) {
                LOGGER.debug("Delaying transparent type computation of {}, delays: {}", typeInfo.fullyQualifiedName,
                        causes);
                return causes;
            }
        }
        Set<ParameterizedType> explicitTypesFromInterfaces = typeInspection.interfacesImplemented()
                .stream()
                .filter(i -> !i.typeInfo.isAggregated())
                .flatMap(i -> {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(i.typeInfo);
                    Set<ParameterizedType> explicitTypes = typeAnalysis.getExplicitTypes(analyserContext);
                    if (explicitTypes == null)
                        return Stream.of(); // FIXME is this correct? if we cause a delay, will it cause cycles?
                    return explicitTypes.stream();
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
        allExplicitTypes.addAll(explicitTypesFromParent);
        allExplicitTypes.addAll(explicitTypesFromSubTypes);

        LOGGER.debug("All explicit types: {}", explicitTypes);

        Set<ParameterizedType> superTypesOfExplicitTypes = allExplicitTypes.stream()
                .flatMap(pt -> pt.concreteSuperTypes(analyserContext))
                .collect(Collectors.toUnmodifiableSet());
        allExplicitTypes.addAll(superTypesOfExplicitTypes);

        allTypes.removeAll(allExplicitTypes);
        allTypes.removeIf(pt -> pt.isPrimitiveExcludingVoid() || pt.isBoxedExcludingVoid()
                || pt.typeInfo == typeInfo || pt.isUnboundWildcard());

        typeAnalysis.setExplicitTypes(new SetOfTypes(allExplicitTypes));
        typeAnalysis.setTransparentTypes(new SetOfTypes(allTypes));
        LOGGER.debug("Transparent data types for {} are: [{}]", typeInfo.fullyQualifiedName, allTypes);
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

    private AnalysisStatus computeApprovedPreconditionsE1(int iteration) {
        if (typeAnalysis.approvedPreconditionsStatus(false).isDone()) {
            return DONE;
        }
        Set<MethodAnalyser> assigningMethods = determineAssigningMethods();

        CausesOfDelay delays = assigningMethods.stream()
                .map(methodAnalyser -> methodAnalyser.getMethodAnalysis().getPreconditionForEventual().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delays.isDelayed()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Not all precondition preps on assigning methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
                assigningMethods.stream().filter(m -> m.getMethodAnalysis().getPreconditionForEventual().isDelayed())
                        .forEach(m -> LOGGER.debug("--> {}: {}", m.getMethodInfo().fullyQualifiedName,
                                m.getMethodAnalysis().getPreconditionForEventual().causesOfDelay()));
            }
            typeAnalysis.setApprovedPreconditionsE1Delays(delays);
            return delays;
        }
        Optional<MethodAnalyser> oEmpty = assigningMethods.stream()
                .filter(ma -> ma.getMethodAnalysis().getPreconditionForEventual() != null &&
                        ma.getMethodAnalysis().getPreconditionForEventual().isEmpty())
                .findFirst();
        if (oEmpty.isPresent()) {
            LOGGER.debug("Not all assigning methods have a valid precondition in {}; (findFirst) {}",
                    typeInfo.fullyQualifiedName, oEmpty.get().getMethodInfo().fullyQualifiedName);
            typeAnalysis.freezeApprovedPreconditionsE1();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        Map<FieldReference, Set<MethodInfo>> methodsForApprovedField = new HashMap<>();
        for (MethodAnalyser methodAnalyser : assigningMethods) {
            Precondition precondition = methodAnalyser.getMethodAnalysis().getPreconditionForEventual();
            if (precondition != null) {
                HandlePrecondition hp = handlePrecondition(methodAnalyser, precondition, iteration);
                if (hp.causesOfDelay.isDelayed()) {
                    LOGGER.debug("Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
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
        typeAnalysis.freezeApprovedPreconditionsE1();
        LOGGER.debug("Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return DONE;
    }

    private Map<FieldReference, Expression> approvedPreconditionsFromParent(TypeInfo typeInfo, boolean e2) {
        TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            TypeInfo parent = parentClass.typeInfo;
            TypeAnalysis parentAnalysis = analyserContext.getTypeAnalysis(parent);
            Map<FieldReference, Expression> map = new HashMap<>(parentAnalysis.getApprovedPreconditions(e2));
            map.putAll(approvedPreconditionsFromParent(parent, e2));
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
          writes: typeAnalysis.approvedPreconditionsE2, the official marker for eventuality in the type

          when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE

         */
    private AnalysisStatus computeApprovedPreconditionsE2(int iteration) {
        if (typeAnalysis.approvedPreconditionsStatus(true).isDone()) {
            return DONE;
        }
        CausesOfDelay modificationDelays = myMethodAnalysersExcludingSAMs.stream()
                .filter(methodAnalyser -> !methodAnalyser.getMethodInfo().isAbstract())
                .map(methodAnalyser -> methodAnalyser.getMethodAnalysis()
                        .getProperty(Property.MODIFIED_METHOD_ALT_TEMP).causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (modificationDelays.isDelayed()) {
            LOGGER.debug("Delaying only mark E2 in {}, modification delayed", typeInfo.fullyQualifiedName);
            typeAnalysis.setApprovedPreconditionsE2Delays(modificationDelays);
            return modificationDelays;
        }

        CausesOfDelay preconditionForEventualDelays = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP).valueIsTrue())
                .map(ma -> ma.getMethodAnalysis().getPreconditionForEventual().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (preconditionForEventualDelays.isDelayed()) {
            LOGGER.debug("Not all precondition preps on modifying methods have been set in {}",
                    typeInfo.fullyQualifiedName);
            typeAnalysis.setApprovedPreconditionsE2Delays(preconditionForEventualDelays);
            return preconditionForEventualDelays;
        }
        Optional<MethodAnalyser> optEmptyPreconditions = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP).valueIsTrue() &&
                        ma.getMethodAnalysis().getPreconditionForEventual() == null)
                .findFirst();
        if (optEmptyPreconditions.isPresent()) {
            LOGGER.debug("Not all modifying methods have a valid precondition in {}: (findFirst) {}",
                    typeInfo.fullyQualifiedName, optEmptyPreconditions.get().getMethodInfo().fullyQualifiedName);
            typeAnalysis.freezeApprovedPreconditionsE2();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        Map<FieldReference, Set<MethodInfo>> methodsForApprovedField = new HashMap<>();
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
            if (modified.valueIsTrue()) {
                Precondition precondition = methodAnalyser.getMethodAnalysis().getPreconditionForEventual();
                if (precondition != null) {
                    HandlePrecondition hp = handlePrecondition(methodAnalyser, precondition, iteration);
                    if (hp.causesOfDelay.isDelayed()) {
                        LOGGER.debug("Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
                        typeAnalysis.setApprovedPreconditionsE2Delays(hp.causesOfDelay);
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
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsE2);
        typeAnalysis.freezeApprovedPreconditionsE2();
        LOGGER.debug("Approved preconditions {} in {}, type can now be @E2Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return DONE;
    }

    private void findFieldsGuardedByEventuallyImmutableFields(Map<FieldReference, Expression> tempApproved,
                                                              Map<FieldReference, Set<MethodInfo>> methodsForApprovedField) {
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldInfo fieldInfo = fieldAnalyser.getFieldInfo();
            FieldReference fieldReference = new FieldReference(analyserContext, fieldInfo);
            if (fieldInfo.isPrivate() && !tempApproved.containsKey(fieldReference)) {
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
                        LOGGER.debug("Field {} joins the preconditions of guarding field {} in type {}", fieldInfo.name,
                                guard.get().fieldInfo.name, typeInfo.fullyQualifiedName);
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
        EvaluationResult context = EvaluationResult.from(new EvaluationContextImpl(iteration,
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

    private AnalysisStatus analyseContainer() {
        DV container = typeAnalysis.getProperty(CONTAINER);
        if (container.isDone()) {
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
                        LOGGER.debug("Delaying container, modification of parameter {} undecided",
                                parameterInfo.fullyQualifiedName());
                        allCauses = allCauses.merge(modified.causesOfDelay());
                    }
                    if (modified.valueIsTrue()) {
                        LOGGER.debug("{} is not a @Container: the content of {} is modified in {}",
                                typeInfo.fullyQualifiedName,
                                parameterInfo.fullyQualifiedName(),
                                methodAnalyser.getMethodInfo().distinguishingName());
                        typeAnalysis.setProperty(CONTAINER, MultiLevel.NOT_CONTAINER_DV);
                        typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
                        return DONE;
                    }
                }
            }
        }
        if (allCauses.isDelayed()) {
            CausesOfDelay marker = typeInfo.delay(CauseOfDelay.Cause.CONTAINER);
            CausesOfDelay merge = allCauses.causesOfDelay().merge(marker);
            typeAnalysis.setProperty(CONTAINER, merge);
            if (ALT_CONTAINER != CONTAINER) {
                typeAnalysis.setProperty(ALT_CONTAINER, merge);
            }
            LOGGER.debug("Delaying container {}, delays: {}", typeInfo.fullyQualifiedName, merge);
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
     * TWO: any non-private field that is not @E2Immutable -> @Dependent
     * any non-private field that is @E2Immutable, not @Independent -> @Dependent1
     * otherwise @Independent.
     * <p>
     * Return the minimum value of ZERO, ONE and TWO.
     *
     * @return true if a decision was made
     */
    private AnalysisStatus analyseIndependent() {
        DV typeIndependent = typeAnalysis.getProperty(Property.INDEPENDENT);
        if (typeIndependent.isDone()) return DONE;

        DV typeImmutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (typeImmutable.ge(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV)) {
            int immutableLevel = MultiLevel.level(typeImmutable);
            DV independent = MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
            LOGGER.debug("Type @Independent: have high immutability value, from which independence follows: {}",
                    independent);
            typeAnalysis.setProperty(Property.INDEPENDENT, independent);
            return DONE;
        }
        if (typeImmutable.isDelayed()) {
            LOGGER.debug("Independence of type {} delayed, waiting for type immutability", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.INDEPENDENT, typeImmutable);
            return typeImmutable.causesOfDelay();
        }

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(Property.INDEPENDENT);
        if (MARKER != parentOrEnclosing.status) return parentOrEnclosing.status;

        DV valueFromFields = myFieldAnalysers.stream()
                .filter(fa -> !fa.getFieldInfo().isPrivate())
                .map(fa -> independenceOfField(fa.getFieldAnalysis()))
                .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
        if (valueFromFields.isDelayed()) {
            LOGGER.debug("Independence of type {} delayed, waiting for field independence",
                    typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.INDEPENDENT, valueFromFields);
            return valueFromFields.causesOfDelay();
        }
        DV valueFromMethodParameters;
        if (valueFromFields.equals(MultiLevel.DEPENDENT_DV)) {
            valueFromMethodParameters = MultiLevel.DEPENDENT_DV; // no need to compute anymore, at bottom anyway
        } else {
            valueFromMethodParameters = myMethodAndConstructorAnalysersExcludingSAMs.stream()
                    .filter(ma -> !ma.getMethodInfo().isPrivate(analyserContext))
                    .flatMap(ma -> ma.getParameterAnalyses().stream())
                    .map(pa -> correctIndependentFunctionalInterface(pa, pa.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT)))
                    .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            if (valueFromMethodParameters.isDelayed()) {
                LOGGER.debug("Independence of type {} delayed, waiting for parameter independence",
                        typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(Property.INDEPENDENT, valueFromMethodParameters);
                return valueFromMethodParameters.causesOfDelay();
            }
        }
        DV valueFromMethodReturnValue;
        if (valueFromMethodParameters.equals(MultiLevel.DEPENDENT_DV)) {
            valueFromMethodReturnValue = MultiLevel.DEPENDENT_DV;
        } else {
            valueFromMethodReturnValue = myMethodAnalysersExcludingSAMs.stream()
                    .filter(ma -> !ma.getMethodInfo().isPrivate()
                            && ma.getMethodInfo().hasReturnValue()
                            && !isOfOwnOrInnerClassType(ma.getMethodInspection().getReturnType()))
                    .map(ma -> ma.getMethodAnalysis().getProperty(Property.INDEPENDENT))
                    .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            if (valueFromMethodReturnValue.isDelayed()) {
                LOGGER.debug("Independence of type {} delayed, waiting for method independence",
                        typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(Property.INDEPENDENT, valueFromMethodReturnValue);
                return valueFromMethodReturnValue.causesOfDelay();
            }
        }
        DV finalValue = parentOrEnclosing.maxValue
                .min(valueFromMethodReturnValue)
                .min(valueFromFields)
                .min(valueFromMethodParameters);
        LOGGER.debug("Set independence of type {} to {}", typeInfo.fullyQualifiedName, finalValue);
        typeAnalysis.setProperty(Property.INDEPENDENT, finalValue);
        return DONE;
    }

    private DV independenceOfField(FieldAnalysis fieldAnalysis) {
        DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        if (immutable.lt(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV)) return MultiLevel.DEPENDENT_DV;
        TypeInfo bestType = fieldAnalysis.getFieldInfo().type.bestTypeInfo(analyserContext);
        if (bestType == null) {
            return MultiLevel.INDEPENDENT_1_DV;
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
            LOGGER.debug("Waiting with {} on {}, parent or enclosing class's status not yet known",
                    property, typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(property, min.causesOfDelay());
            return new MaxValueStatus(min, min.causesOfDelay());
        }
        if (min.equals(property.falseDv)) {
            LOGGER.debug("{} set to least value for {}, because of parent", typeInfo.fullyQualifiedName, property);
            typeAnalysis.setProperty(property, property.falseDv);
            return new MaxValueStatus(property.falseDv, DONE);
        }
        return new MaxValueStatus(min, MARKER);
    }

    private DV allMyFieldsFinal() {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            DV effectivelyFinal = fieldAnalyser.getFieldAnalysis().getProperty(Property.FINAL);
            if (effectivelyFinal.isDelayed()) {
                LOGGER.debug("Delay on type {}, field {} effectively final not known yet",
                        typeInfo.fullyQualifiedName, fieldAnalyser.getFieldInfo().name);
                causes = causes.merge(effectivelyFinal.causesOfDelay());
            }
            if (effectivelyFinal.valueIsFalse()) {
                LOGGER.debug("Type {} cannot be @E1Immutable, field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldAnalyser.getFieldInfo().name);
                return DV.FALSE_DV;
            }
        }
        if (causes.isDelayed()) return causes;
        return DV.TRUE_DV;
    }

    /**
     * Important to set a value for both E1 immutable and E2 immutable (there is a system to say
     * "it is level 1, but delay on level 2", but WE ARE NOT USING THAT ANYMORE !!)
     * <p>
     * Rules as of 30 July 2020: Definition on top of @E1Immutable
     * <p>
     * RULE 1: All fields must be @NotModified.
     * <p>
     * RULE 2: All fields must be private, or their types must be level 2 immutable (incl. unbound, transparent)
     * <p>
     * RULE 3: All methods and constructors must be independent of the non-level 2 immutable fields
     *
     * @return true if a change was made to typeAnalysis
     */
    private AnalysisStatus analyseEffectivelyEventuallyE2Immutable() {
        DV typeImmutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (typeImmutable.isDone()) {
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, typeImmutable);
            return DONE; // we have a decision already
        }

        // effectively E1
        DV allMyFieldsFinal = allMyFieldsFinal();
        if (allMyFieldsFinal.isDelayed()) {
            typeAnalysis.setProperty(PARTIAL_IMMUTABLE, allMyFieldsFinal);
            typeAnalysis.setProperty(Property.IMMUTABLE, allMyFieldsFinal);
            return allMyFieldsFinal.causesOfDelay();
        }
        TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
        MultiLevel.Effective parentEffective;
        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && parentClass.isJavaLangObject()) {
            parentEffective = MultiLevel.Effective.EFFECTIVE;
        } else {
            assert parentClass != null;
            TypeInfo parentType = parentClass.typeInfo;
            DV parentImmutable = analyserContext.getTypeAnalysis(parentType).getProperty(Property.IMMUTABLE);
            parentEffective = MultiLevel.effectiveAtLevel1Immutable(parentImmutable);
        }

        Property ALT_IMMUTABLE;
        AnalysisStatus ALT_DONE;

        DV partialImmutable = typeAnalysis.getProperty(Property.PARTIAL_IMMUTABLE);
        DV fromParentOrEnclosing = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> typeAnalysis.getProperty(Property.IMMUTABLE))
                .reduce(Property.IMMUTABLE.bestDv, DV::min);
        if (fromParentOrEnclosing.isDelayed()) {
            typeAnalysis.setProperty(Property.IMMUTABLE, fromParentOrEnclosing);
            if (partialImmutable.isDone()) {
                LOGGER.debug("We've done what we can, waiting for parent-enclosing now");
                return AnalysisStatus.of(fromParentOrEnclosing);
            }
            LOGGER.debug("Continuing in {}, ignore parent-enclosing", typeInfo.fullyQualifiedName);
            ALT_IMMUTABLE = Property.PARTIAL_IMMUTABLE;
            ALT_DONE = AnalysisStatus.of(fromParentOrEnclosing);
            fromParentOrEnclosing = Property.IMMUTABLE.bestDv;
            parentEffective = MultiLevel.Effective.EFFECTIVE;
        } else {
            if (fromParentOrEnclosing.equals(MultiLevel.MUTABLE_DV)) {
                LOGGER.debug("{} is not an E1Immutable, E2Immutable class, because parent or enclosing is Mutable",
                        typeInfo.fullyQualifiedName);
                return doneImmutable(IMMUTABLE, MultiLevel.MUTABLE_DV, DONE);
            }
            if (partialImmutable.isDone()) {
                DV min = fromParentOrEnclosing.min(partialImmutable);
                typeAnalysis.setProperty(Property.IMMUTABLE, min);
                LOGGER.debug("We had already done the work without parent/enclosing, now its there: {}", min);
                return DONE;
            }
            ALT_IMMUTABLE = Property.IMMUTABLE;
            ALT_DONE = AnalysisStatus.DONE;
        }


        Set<FieldInfo> fieldsThatMustBeGuarded = new HashSet<>();

        DV myWhenEXFails;
        boolean eventual;
        if (allMyFieldsFinal.valueIsFalse() || parentEffective != MultiLevel.Effective.EFFECTIVE) {
            CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(false);
            if (approvedDelays.isDelayed()) {
                LOGGER.debug("Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 1 immutable", typeInfo.fullyQualifiedName);
                return delayImmutable(approvedDelays);
            }
            List<FieldReference> nonFinalFields = myFieldAnalysers.stream()
                    .filter(fa -> DV.FALSE_DV.equals(fa.getFieldAnalysis().getProperty(Property.FINAL)))
                    .map(fa -> new FieldReference(analyserContext, fa.getFieldInfo())).toList();
            Set<FieldInfo> fieldsNotE1 = typeAnalysis.nonFinalFieldsNotApprovedOrGuarded(nonFinalFields);
            if (!fieldsNotE1.isEmpty()) {
                myWhenEXFails = MultiLevel.MUTABLE_DV;
                fieldsThatMustBeGuarded.addAll(fieldsNotE1);
            } else {
                myWhenEXFails = MultiLevel.EVENTUALLY_E1IMMUTABLE_DV;
            }
            eventual = true;

            if (parentEffective == MultiLevel.Effective.EVENTUAL) {
                TypeAnalysis parentTypeAnalysis = analyserContext.getTypeAnalysis(parentClass.typeInfo);
                Set<FieldInfo> parentFields = parentTypeAnalysis.getEventuallyImmutableFields();
                assert !parentFields.isEmpty() ||
                        !parentTypeAnalysis.getApprovedPreconditionsE1().isEmpty() ||
                        !parentTypeAnalysis.getApprovedPreconditionsE2().isEmpty();
                parentFields.forEach(fieldInfo -> {
                    if (typeAnalysis.eventuallyImmutableFieldNotYetSet(fieldInfo)) {
                        typeAnalysis.addEventuallyImmutableField(fieldInfo);
                    }
                });
            }
        } else {
            CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(true);
            if (approvedDelays.isDelayed()) {
                LOGGER.debug("Type {} is effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
                return delayImmutable(approvedDelays);
            }
            myWhenEXFails = MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV;
            // it is possible that all fields are final, yet some field's content is used as the precondition
            eventual = !typeAnalysis.approvedPreconditionsE2IsEmpty();
        }

        DV whenEXFails = fromParentOrEnclosing.min(myWhenEXFails);

        // E2
        // NOTE that we need to check 2x: needed in else of previous statement, but also if we get through if-side.
        CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(true);
        if (approvedDelays.isDelayed()) {
            LOGGER.debug("Type {} is not effectively level 1 immutable, waiting for" +
                    " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
            return delayImmutable(approvedDelays);
        }

        int minLevel = MultiLevel.Level.IMMUTABLE_R.level; // can only go down!
        CausesOfDelay causesFields = CausesOfDelay.EMPTY;

        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.getFieldAnalysis();
            FieldInfo fieldInfo = fieldAnalyser.getFieldInfo();
            if (fieldInfo.type.bestTypeInfo() == typeInfo) {
                // "self" type, ignored
                continue;
            }

            FieldReference thisFieldInfo = new FieldReference(analyserContext, fieldInfo);
            String fieldFQN = fieldInfo.fullyQualifiedName();

            DV transparentType = fieldAnalysis.isTransparentType();
            if (transparentType.isDelayed()) {
                LOGGER.debug("Field {} not yet known if of transparent type, delaying @E2Immutable on type", fieldFQN);
                causesFields = causesFields.merge(transparentType.causesOfDelay());
                continue;
            }
            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or E2+Immutable themselves
            // because of down-casts on non-primitives, e.g. from transparent type to explicit, we cannot rely on the static type
            DV fieldImmutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
            MultiLevel.Effective fieldE2Immutable = MultiLevel.effectiveAtLevel2PlusImmutable(fieldImmutable);
            if (fieldImmutable.isDelayed()) {
                if (fieldIsOfOwnOrInnerClassType(fieldInfo)) {
                    TypeInfo ownOrInner = fieldInfo.type.typeInfo;
                    assert ownOrInner != null;
                    // non-static nested types (inner types such as lambda's, anonymous)
                    if (ownOrInner == typeInfo) {
                        fieldE2Immutable = MultiLevel.Effective.EFFECTIVE; // doesn't matter at all
                    } else {
                        // inner type, try partial
                        DV partial = analyserContext.getTypeAnalysis(ownOrInner).getProperty(Property.PARTIAL_IMMUTABLE);
                        if (partial.isDelayed()) {
                            LOGGER.debug("Field {} of nested type has no PARTIAL_IMMUTABLE yet", fieldFQN);
                            causesFields = causesFields.merge(fieldImmutable.causesOfDelay());
                            continue;
                        }
                        fieldE2Immutable = MultiLevel.effectiveAtLevel2PlusImmutable(partial);
                    }
                } else {
                    // field is of a type that is very closely related to the type being analysed; we're looking to break a delay
                    // here by requiring the rules, and saying that it is not eventual; see FunctionInterface_0
                    ParameterizedType concreteType = fieldAnalysis.concreteTypeNullWhenDelayed();
                    if (concreteType != null && concreteType.typeInfo != null &&
                            concreteType.typeInfo.topOfInterdependentClassHierarchy() == typeInfo.topOfInterdependentClassHierarchy()) {
                        fieldE2Immutable = MultiLevel.Effective.EVENTUAL_AFTER; // must follow rules, but is not eventual
                    } else {
                        LOGGER.debug("Field {} not known yet if @E2Immutable, delaying @E2Immutable on type", fieldFQN);
                        causesFields = causesFields.merge(fieldImmutable.causesOfDelay());
                    }
                }
            }

            // NOTE: the 2 values that matter now are EVENTUAL and EFFECTIVE; any other will lead to a field
            // that needs to follow the additional rules
            boolean isPrimitive = fieldInfo.type.isPrimitiveExcludingVoid();

            if (fieldE2Immutable == MultiLevel.Effective.EVENTUAL || fieldE2Immutable == MultiLevel.Effective.EVENTUAL_BEFORE) {
                eventual = true;
                if (typeAnalysis.eventuallyImmutableFieldNotYetSet(fieldInfo)) {
                    typeAnalysis.addEventuallyImmutableField(fieldInfo);
                }
            } else if (typeAnalysis.getGuardedByEventuallyImmutableFields().contains(fieldInfo)) {
                LOGGER.debug("Field {} is guarded by preconditions", fieldFQN);

            } else if (!isPrimitive) {
                boolean fieldRequiresRules = fieldAnalysis.isTransparentType().valueIsFalse()
                        && fieldE2Immutable != MultiLevel.Effective.EFFECTIVE;

                DV modified = fieldAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!eventual && modified.isDelayed()) {
                    LOGGER.debug("Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldFQN);
                    causesFields = causesFields.merge(modified.causesOfDelay());
                    continue;
                }
                if (modified.valueIsTrue()) {
                    if (typeAnalysis.containsApprovedPreconditionsE2(thisFieldInfo)) {
                        LOGGER.debug("Modified field {} has the approved preconditions for {} to be eventually immutable",
                                fieldInfo.name, typeInfo.simpleName);
                    } else {
                        LOGGER.debug("For {} to become eventually E2Immutable, modified field {} can only be modified " +
                                "in methods marked @Mark or @Only(before=)", typeInfo.fullyQualifiedName, fieldInfo.name);
                        fieldsThatMustBeGuarded.add(fieldInfo);
                    }
                }

                // RULE 2: ALL NON-TRANSPARENT NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        LOGGER.debug("{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        return doneImmutable(ALT_IMMUTABLE, whenEXFails, ALT_DONE);
                    }
                } else {
                    LOGGER.debug("Ignoring private modifier check of {}, self-referencing", fieldFQN);
                }
            }
        }
        if (causesFields.isDelayed()) {
            LOGGER.debug("Delaying immutable of {} because of fields, delays: {}", typeInfo.fullyQualifiedName, causesFields);
            return delayImmutable(causesFields);
        }

        CausesOfDelay causesConstructor = CausesOfDelay.EMPTY;
        for (MethodAnalyser constructor : myConstructors) {
            for (ParameterAnalysis parameterAnalysis : constructor.getParameterAnalyses()) {
                DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                if (independent.isDelayed()) {
                    if (parameterAnalysis.getParameterInfo().parameterizedType.typeInfo == typeInfo) {
                        continue;
                    }
                    LOGGER.debug("Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}",
                            constructor.getMethodInfo().distinguishingName());
                    causesConstructor = causesConstructor.merge(independent.causesOfDelay()); //not decided
                } else {
                    DV correctedIndependent = correctIndependentFunctionalInterface(parameterAnalysis, independent);
                    if (correctedIndependent.equals(MultiLevel.DEPENDENT_DV)) {
                        LOGGER.debug("{} is not an E2Immutable class, because constructor is @Dependent",
                                typeInfo.fullyQualifiedName);
                        return doneImmutable(ALT_IMMUTABLE, whenEXFails, ALT_DONE);
                    }
                    int independentLevel = MultiLevel.oneLevelMoreFrom(correctedIndependent);
                    minLevel = Math.min(minLevel, independentLevel);
                }
            }
        }
        if (causesConstructor.isDelayed()) {
            return delayImmutable(causesConstructor);
        }

        CausesOfDelay causesMethods = CausesOfDelay.EMPTY;
        for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
            DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
            // in the eventual case, we only need to look at the non-modifying methods
            // calling a modifying method will result in an error
            if (modified.valueIsFalse()) {
                if (methodAnalyser.getMethodInfo().isVoid()) continue; // we're looking at return types
                DV returnTypeImmutable = methodAnalyser.getMethodAnalysis().getProperty(Property.IMMUTABLE);

                ParameterizedType returnType;
                Expression srv = methodAnalyser.getMethodAnalysis().getSingleReturnValue();
                if (srv.isDone()) {
                    // concrete
                    returnType = srv.returnType();
                } else {
                    // formal; this one may come earlier, but that's OK; the only thing it can do is facilitate a delay
                    returnType = analyserContext.getMethodInspection(methodAnalyser.getMethodInfo()).getReturnType();
                }
                boolean returnTypePartOfMyself = isOfOwnOrInnerClassType(returnType);
                if (returnTypePartOfMyself) continue;
                if (returnTypeImmutable.isDelayed()) {
                    if (srv.causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.BREAK_IMMUTABLE_DELAY)) {
                        LOGGER.debug("Breaking @Immutable delay self reference on {}", methodAnalyser.getMethodInfo().distinguishingName());
                        continue;
                    }
                    LOGGER.debug("Return type of {} not known if @E2Immutable, delaying", methodAnalyser.getMethodInfo().distinguishingName());
                    CausesOfDelay marker = methodAnalyser.getMethodInfo().delay(CauseOfDelay.Cause.BREAK_IMMUTABLE_DELAY);
                    CausesOfDelay marked = returnTypeImmutable.causesOfDelay().merge(marker);
                    causesMethods = causesMethods.merge(marked);
                    continue;
                }
                // TODO while it works at the moment, the code is a bit of a mess (indep checks only for identical types, check on srv and returnTypeImmutable, ...)
                MultiLevel.Effective returnTypeE2Immutable = MultiLevel.effectiveAtLevel2PlusImmutable(returnTypeImmutable);
                if (returnTypeE2Immutable.lt(MultiLevel.Effective.EVENTUAL)) {
                    // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
                    DV independent = methodAnalyser.getMethodAnalysis().getProperty(Property.INDEPENDENT);
                    if (independent.isDelayed()) {
                        if (returnType.typeInfo == typeInfo) {
                            LOGGER.debug("Cannot decide if method {} is independent, but given that its return type is a self reference, don't care",
                                    methodAnalyser.getMethodInfo().fullyQualifiedName);
                        } else {
                            LOGGER.debug("Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.getMethodInfo().name);
                            causesMethods = causesMethods.merge(independent.causesOfDelay());
                            continue;
                        }
                    }
                    if (independent.equals(MultiLevel.DEPENDENT_DV)) {
                        LOGGER.debug("{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                typeInfo.fullyQualifiedName, methodAnalyser.getMethodInfo().name);
                        return doneImmutable(ALT_IMMUTABLE, whenEXFails, ALT_DONE);
                    }
                    int independentLevel = MultiLevel.oneLevelMoreFrom(independent);
                    minLevel = Math.min(minLevel, independentLevel);
                } else {
                    minLevel = Math.min(minLevel, MultiLevel.level(returnTypeImmutable));
                }
            } else if (modified.valueIsTrue()) {
                if (typeAnalysis.isEventual()) {
                    // code identical to that of constructors
                    for (ParameterAnalysis parameterAnalysis : methodAnalyser.getParameterAnalyses()) {
                        DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                        if (independent.isDelayed()) {
                            if (parameterAnalysis.getParameterInfo().parameterizedType.typeInfo != typeInfo) {
                                LOGGER.debug("Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}",
                                        methodAnalyser.getMethodInfo().distinguishingName());
                                typeAnalysis.setProperty(ALT_IMMUTABLE, independent);
                                causesMethods = causesMethods.merge(independent.causesOfDelay()); //not decided
                            }
                        } else {
                            DV correctedIndependent = correctIndependentFunctionalInterface(parameterAnalysis, independent);
                            if (correctedIndependent.equals(MultiLevel.DEPENDENT_DV)) {
                                LOGGER.debug("{} is not an E2Immutable class, because constructor is @Dependent",
                                        typeInfo.fullyQualifiedName);
                                return doneImmutable(ALT_IMMUTABLE, whenEXFails, ALT_DONE);
                            }
                            int independentLevel = MultiLevel.oneLevelMoreFrom(correctedIndependent);
                            minLevel = Math.min(minLevel, independentLevel);
                        }
                    }
                } else {
                    // contracted @Modified, see e.g. InlinedMethod_AAPI_3
                    LOGGER.debug("{} is not an E2Immutable class, because method {} is modifying (even though none of our fields are)",
                            typeInfo.fullyQualifiedName, methodAnalyser.getMethodInfo().name);
                    return doneImmutable(ALT_IMMUTABLE, whenEXFails, ALT_DONE);
                }
            } else throw new UnsupportedOperationException("?");
        }
        if (causesMethods.isDelayed()) {
            return delayImmutable(causesMethods);
        }

        if (!fieldsThatMustBeGuarded.isEmpty()) {
            // check that these fields occur only in tandem to eventually immutable fields; if not, return failure
            Set<FieldInfo> eventuallyImmutable = typeAnalysis.getEventuallyImmutableFields();
            if (!eventuallyImmutable.isEmpty()) {
                Map<FieldInfo, Set<MethodInfo>> methodsOfEventuallyImmutableFields =
                        eventuallyImmutable.stream().collect(Collectors.toUnmodifiableMap(f -> f, this::methodsOf));
                fieldsThatMustBeGuarded.removeIf(f -> {
                    Set<MethodInfo> methodsOfField = methodsOf(f);
                    boolean remove = methodsOfEventuallyImmutableFields.values().stream().anyMatch(
                            ev -> ev.containsAll(methodsOfField));
                    if (remove) {
                        typeAnalysis.addGuardedByEventuallyImmutableField(f);
                    }
                    return remove;
                });
            }
            if (!fieldsThatMustBeGuarded.isEmpty()) {
                LOGGER.debug("Set @Immutable of type {} to {}, fieldsThatMustBeGuarded not empty", typeInfo.fullyQualifiedName,
                        myWhenEXFails);
                return doneImmutable(ALT_IMMUTABLE, myWhenEXFails, ALT_DONE);
            }
        }

        MultiLevel.Effective effective = eventual ? MultiLevel.Effective.EVENTUAL : MultiLevel.Effective.EFFECTIVE;
        DV finalValue = fromParentOrEnclosing.min(MultiLevel.composeImmutable(effective, minLevel));
        LOGGER.debug("Set @Immutable of type {} to {}", typeInfo.fullyQualifiedName, finalValue);
        return doneImmutable(ALT_IMMUTABLE, finalValue, ALT_DONE);
    }

    /*
    property == IMMUTABLE -> also write PARTIAL_IMMUTABLE
    property == PARTIAL_IMMUTABLE -> only write partial
     */
    private AnalysisStatus doneImmutable(Property property, DV value, AnalysisStatus analysisStatus) {
        typeAnalysis.setProperty(property, value);
        if (property == IMMUTABLE) {
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, value);
        }
        return analysisStatus;
    }

    private CausesOfDelay delayImmutable(CausesOfDelay delays) {
        typeAnalysis.setProperty(IMMUTABLE, delays);
        typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, delays);
        return delays;
    }

    /*
    See Lazy; other code in SAEvaluationContext.cannotBeModified and StatementAnalysisImpl.initializeParameter.
    A functional interface comes in as the parameter of a non-private method. Modifications on its single, modifying
    method are ignored. As a consequence, we treat the object as at least e2immutable - independent_1.
     */
    private DV correctIndependentFunctionalInterface(ParameterAnalysis parameterAnalysis, DV independent) {
        DV correctedIndependent;
        DV ignoreModification = parameterAnalysis.getProperty(Property.IGNORE_MODIFICATIONS);
        if (ignoreModification.equals(MultiLevel.IGNORE_MODS_DV)
                && parameterAnalysis.getParameterInfo().parameterizedType.isFunctionalInterface()
                && !parameterAnalysis.getParameterInfo().getMethod().isPrivate()) {
            LOGGER.debug("Incoming functional interface on non-private method");
            correctedIndependent = independent.max(MultiLevel.INDEPENDENT_1_DV);
        } else {
            correctedIndependent = independent;
        }
        return correctedIndependent;
    }

    private Set<MethodInfo> methodsOf(FieldInfo fieldInfo) {
        return myMethodAnalysers.stream()
                .filter(ma -> ma.getFieldAsVariableStream(fieldInfo).anyMatch(ComputingTypeAnalyser::isModified) ||
                        ma.getMethodAnalysis().getPreconditionForEventual().guardsField(analyserContext, fieldInfo))
                .map(MethodAnalyser::getMethodInfo)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isModified(VariableInfo vi) {
        return vi.isAssigned() || vi.getProperty(Property.CONTEXT_MODIFIED).valueIsTrue();
    }

    private TypeInfo initializerAssignedToAnonymousType(FieldInfo fieldInfo) {
        FieldInspection.FieldInitialiser initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
        if (initialiser == null) return null;
        Expression expression = initialiser.initialiser();
        if (expression == null || expression == EmptyExpression.EMPTY_EXPRESSION) return null;
        ParameterizedType type = expression.returnType();
        if (type.isFunctionalInterface()) {
            if (expression instanceof ConstructorCall cc && cc.anonymousClass() != null) {
                return cc.anonymousClass();
            }
            if (expression instanceof Lambda lambda) {
                return lambda.definesType();
            }
        }
        return type.typeInfo;
    }

    private boolean isOfOwnOrInnerClassType(ParameterizedType type) {
        return type.typeInfo != null && type.typeInfo.isEnclosedIn(typeInfo);
    }

    private boolean fieldIsOfOwnOrInnerClassType(FieldInfo fieldInfo) {
        if (isOfOwnOrInnerClassType(fieldInfo.type)) {
            return true;
        }
        // the field can be assigned to an anonymous type, which has a static functional interface type
        // we want to catch the newly created type
        TypeInfo anonymousType = initializerAssignedToAnonymousType(fieldInfo);
        return anonymousType != null && anonymousType.isEnclosedIn(typeInfo);
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

        boolean allConstructorsPrivate = typeInspection.constructors().stream().allMatch(MethodInfo::isPrivate);
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
                            typeInfo.fullyQualifiedName);
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
                LOGGER.debug("Delaying @Singleton on {} until precondition known", typeInfo.fullyQualifiedName);
                return preconditionDelays;
            }
            Precondition precondition = constructorAnalysis.getPrecondition();
            VariableExpression ve;
            if (!precondition.isEmpty() && (ve = variableExpressionOrNegated(precondition.expression())) != null
                    && ve.variable() instanceof FieldReference fr
                    && fr.fieldInfo.isStatic()
                    && fr.fieldInfo.isPrivate()
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
                        LOGGER.debug("Type {} is a  @Singleton, found boolean variable with precondition",
                                typeInfo.fullyQualifiedName);
                        typeAnalysis.setProperty(Property.SINGLETON, DV.TRUE_DV);
                        return DONE;
                    }
                }
            }
        }

        // no hit
        LOGGER.debug("Type {} is not a  @Singleton", typeInfo.fullyQualifiedName);
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

        DV e2Immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (e2Immutable.isDelayed()) {
            LOGGER.debug("Extension class: don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return e2Immutable.causesOfDelay();
        }
        if (e2Immutable.lt(MultiLevel.EVENTUALLY_E2IMMUTABLE_DV)) {
            LOGGER.debug("Type {} is not an @ExtensionClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.EXTENSION_CLASS, DV.FALSE_DV);
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
                    LOGGER.debug("Type " + typeInfo.fullyQualifiedName +
                            " is not an @ExtensionClass, it has no common type for the first " +
                            "parameter (or return type, if no parameters) of static methods, seeing " +
                            commonTypeOfFirstParameter.detailedString() + " vs " + typeOfFirstParameter.detailedString());
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
                        LOGGER.debug("Delaying @ExtensionClass of {} until @NotNull of {} known", typeInfo.fullyQualifiedName,
                                methodInfo.name);
                        return notNull.causesOfDelay();
                    }
                    if (notNull.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                        LOGGER.debug("Type {} is not an @ExtensionClass, method {} does not have either a " +
                                        "@NotNull 1st parameter, or no parameters and returns @NotNull.", typeInfo.fullyQualifiedName,
                                methodInfo.name);
                        commonTypeOfFirstParameter = null;
                        break;
                    }
                }
            }
        }
        boolean isExtensionClass = commonTypeOfFirstParameter != null && haveFirstParameter;
        typeAnalysis.setProperty(Property.EXTENSION_CLASS, DV.fromBoolDv(isExtensionClass));
        LOGGER.debug("Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return DONE;
    }

    private AnalysisStatus analyseUtilityClass() {
        DV utilityClass = typeAnalysis.getProperty(Property.UTILITY_CLASS);
        if (utilityClass.isDone()) return DONE;

        DV e2Immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (e2Immutable.isDelayed()) {
            LOGGER.debug("Utility class: Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return e2Immutable.causesOfDelay();
        }
        if (e2Immutable.lt(MultiLevel.EVENTUALLY_E2IMMUTABLE_DV)) {
            LOGGER.debug("Type {} is not a @UtilityClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
            return DONE;
        }

        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (!methodInfo.methodInspection.get().isStatic()) {
                LOGGER.debug("Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInspection.constructors()) {
            if (!constructor.isPrivate()) {
                LOGGER.debug("Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }

        if (typeInspection.constructors().isEmpty()) {
            LOGGER.debug("Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
            return DONE;
        }

        // and there should be no means of generating an object
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (methodAnalyser.getMethodInfo().methodResolution.get()
                    .methodsOfOwnClassReached().stream().anyMatch(m -> m.isConstructor && m.typeInfo == typeInfo)) {
                LOGGER.debug("Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but an object of the class is created in method "
                        + methodAnalyser.getMethodInfo().fullyQualifiedName());
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }

        typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.TRUE_DV);
        LOGGER.debug("Type {} marked @UtilityClass", typeInfo.fullyQualifiedName);
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
                if (constructorAnalyser.getMethodInfo().isPrivate()) {
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

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            super(closure == null ? 1 : closure.getDepth() + 1, iteration, conditionManager, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }
    }
}
