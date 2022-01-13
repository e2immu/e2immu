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
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.config.AnalyserProgram.Step.TRANSPARENT;
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
                    .add(ANALYSE_INDEPENDENT, iteration -> analyseIndependent())
                    .add(ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE, iteration -> analyseEffectivelyEventuallyE2Immutable())
                    .add(ANALYSE_CONTAINER, iteration -> analyseContainer())
                    .add(ANALYSE_UTILITY_CLASS, iteration -> analyseUtilityClass())
                    .add(ANALYSE_SINGLETON, iteration -> analyseSingleton())
                    .add(ANALYSE_EXTENSION_CLASS, iteration -> analyseExtensionClass());
        } else {
            typeAnalysis.freezeApprovedPreconditionsE1();
            typeAnalysis.freezeApprovedPreconditionsE2();
        }

        analyserComponents = builder.build();

        messages.addAll(typeAnalysis.fromAnnotationsIntoProperties(AnalyserIdentification.TYPE,
                typeInfo.isInterface(),
                typeInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions()));
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

        analyserContext.methodAnalyserStream().forEach(methodAnalyser -> {
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
        analyserContext.fieldAnalyserStream().forEach(fieldAnalyser -> {
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
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(iteration);
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

            return analysisStatus;
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
            log(COMPANION, "Found aspects {} in {}, {}", typeAnalysis.aspects.stream().map(Map.Entry::getKey).collect(Collectors.joining(",")),
                    typeAnalysis.typeInfo.fullyQualifiedName, mainMethod.fullyQualifiedName);
        }
    }

    private AnalysisStatus analyseImmutableCanBeIncreasedByTypeParameters() {
        CausesOfDelay hiddenContentStatus = typeAnalysis.hiddenContentTypeStatus();
        if (typeAnalysis.immutableCanBeIncreasedByTypeParameters().isDone()) return DONE;
        if (hiddenContentStatus.isDelayed()) {
            typeAnalysis.setImmutableCanBeIncreasedByTypeParameters(hiddenContentStatus);
            return hiddenContentStatus;
        }

        boolean res = typeAnalysis.getTransparentTypes().types()
                .stream().anyMatch(t -> t.bestTypeInfo(analyserContext) == null);

        log(IMMUTABLE_LOG, "Immutable can be increased for {}? {}", typeInfo.fullyQualifiedName, res);
        typeAnalysis.setImmutableCanBeIncreasedByTypeParameters(res);
        return DONE;
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
                log(DELAYED, "Hidden content of static nested types of {} not yet known", typeInfo.fullyQualifiedName);
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
                log(DELAYED, "Hidden content of inner class {} computed together with that of enclosing class {}",
                        typeInfo.simpleName, staticEnclosing.fullyQualifiedName);
                return delays;
            }
            return DONE;
        }

        log(IMMUTABLE_LOG, "Computing transparent types for type {}", typeInfo.fullyQualifiedName);

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
            if (parentClass.isJavaLangObject()) {
                explicitTypesFromParent = analyserContext.getPrimitives().explicitTypesOfJLO();
            } else {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parentClass);
                CausesOfDelay delays = typeAnalysis.hiddenContentTypeStatus();
                if (delays.isDelayed() && typeInfo.primaryType() == parentClass.primaryType()) {
                    ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(parentClass);
                    typeAnalyser.analyseTransparentTypes();
                }
                CausesOfDelay delays2 = typeAnalysis.hiddenContentTypeStatus();
                if (delays2.isDone()) {
                    explicitTypesFromParent = typeAnalysis.getExplicitTypes(analyserContext);
                } else {
                    log(DELAYED, "Wait for hidden content types to arrive {}, parent {}", typeInfo.fullyQualifiedName,
                            parentClass.simpleName);
                    return delays;
                }
            }
        }

        // STEP 6: ensure + collect interface types

        {
            for (ParameterizedType ifType : typeInspection.interfacesImplemented()) {
                TypeInfo ifTypeInfo = ifType.typeInfo;
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(ifTypeInfo);
                CausesOfDelay delays = typeAnalysis.hiddenContentTypeStatus();
                if (delays.isDelayed() && typeInfo.primaryType() == ifTypeInfo.primaryType()) {
                    ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(ifTypeInfo);
                    typeAnalyser.analyseTransparentTypes();
                }
                CausesOfDelay delays2 = typeAnalysis.hiddenContentTypeStatus();
                if (delays2.isDelayed()) {
                    log(DELAYED, "Wait for hidden content types to arrive {}, interface {}", typeInfo.fullyQualifiedName,
                            ifTypeInfo.simpleName);
                    return delays2;
                }
            }
        }
        Set<ParameterizedType> explicitTypesFromInterfaces = typeInspection.interfacesImplemented()
                .stream().flatMap(i -> {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(i.typeInfo);
                    return typeAnalysis.getExplicitTypes(analyserContext).stream();
                })
                .collect(Collectors.toUnmodifiableSet());

        // STEP 7: start computation

        // first, determine the types of fields, methods and constructors

        // FIXME ensure we have all methods, constructors and fields of inner (non-static) nested classes
        Set<ParameterizedType> allTypes = typeInspection.typesOfFieldsMethodsConstructors(analyserContext);

        // add all type parameters of these types
        allTypes.addAll(allTypes.stream().flatMap(pt -> pt.components(false).stream()).collect(Collectors.toList()));
        log(IMMUTABLE_LOG, "Types of fields, methods and constructors: {}", allTypes);

        // then, compute the explicit types
        Map<ParameterizedType, Set<ExplicitTypes.UsedAs>> explicitTypes =
                new ExplicitTypes(analyserContext, typeInfo).go(typeInspection).getResult();

        Set<ParameterizedType> allExplicitTypes = new HashSet<>(explicitTypes.keySet());
        allExplicitTypes.addAll(explicitTypesFromInterfaces);
        allExplicitTypes.addAll(explicitTypesFromParent);
        allExplicitTypes.addAll(explicitTypesFromSubTypes);

        log(IMMUTABLE_LOG, "All explicit types: {}", explicitTypes);

        Set<ParameterizedType> superTypesOfExplicitTypes = allExplicitTypes.stream()
                .flatMap(pt -> pt.concreteSuperTypes(analyserContext))
                .collect(Collectors.toUnmodifiableSet());
        allExplicitTypes.addAll(superTypesOfExplicitTypes);

        allTypes.removeAll(allExplicitTypes);
        allTypes.removeIf(pt -> pt.isPrimitiveExcludingVoid() || pt.typeInfo == typeInfo || pt.isUnboundWildcard());

        typeAnalysis.setExplicitTypes(new SetOfTypes(allExplicitTypes));
        typeAnalysis.setTransparentTypes(new SetOfTypes(allTypes));
        log(IMMUTABLE_LOG, "Transparent data types for {} are: [{}]", typeInfo.fullyQualifiedName, allTypes);
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
                .map(methodAnalyser -> methodAnalyser.getMethodAnalysis().preconditionForEventualStatus())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delays.isDelayed()) {
            log(DELAYED, "Not all precondition preps on assigning methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            typeAnalysis.setApprovedPreconditionsE1Delays(delays);
            return delays;
        }
        Optional<MethodAnalyser> oEmpty = assigningMethods.stream()
                .filter(ma -> ma.getMethodAnalysis().getPreconditionForEventual() != null &&
                        ma.getMethodAnalysis().getPreconditionForEventual().isEmpty())
                .findFirst();
        if (oEmpty.isPresent()) {
            log(EVENTUALLY, "Not all assigning methods have a valid precondition in {}; (findFirst) {}",
                    typeInfo.fullyQualifiedName, oEmpty.get().getMethodInfo().fullyQualifiedName);
            typeAnalysis.freezeApprovedPreconditionsE1();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        for (MethodAnalyser methodAnalyser : assigningMethods) {
            Precondition precondition = methodAnalyser.getMethodAnalysis().getPreconditionForEventual();
            if (precondition != null) {
                HandlePrecondition hp = handlePrecondition(methodAnalyser, precondition, iteration);
                if (hp.causesOfDelay.isDelayed()) {
                    log(DELAYED, "Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
                    typeAnalysis.setApprovedPreconditionsE1Delays(hp.causesOfDelay);
                    return hp.causesOfDelay;
                }
                for (FieldToCondition fieldToCondition : hp.fieldToConditions) {
                    Expression inMap = fieldToCondition.overwrite ?
                            tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                            !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                    tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                    if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                        messages.add(Message.newMessage(fieldToCondition.fieldReference.fieldInfo.newLocation(),
                                Message.Label.DUPLICATE_MARK_CONDITION, "Field: " + fieldToCondition.fieldReference));
                    }
                }
            }
        }
        tempApproved.putAll(approvedPreconditionsFromParent(typeInfo, false));

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsE1);
        typeAnalysis.freezeApprovedPreconditionsE1();
        log(EVENTUALLY, "Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
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

    TODO may be slow, we should cache this?

    Rather not, if we're extending without!
     */
    private Set<MethodAnalyser> determineAssigningMethods() {
        Set<MethodInfo> assigningMethods = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> {
                    StatementAnalysis statementAnalysis = ma.getMethodAnalysis().getLastStatement();
                    return statementAnalysis != null && statementAnalysis.assignsToFields() &&
                            statementAnalysis.noIncompatiblePrecondition();
                })
                .map(MethodAnalyser::getMethodInfo)
                .collect(Collectors.toUnmodifiableSet());

        return myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> !ma.getMethodInspection().isPrivate())
                .filter(ma -> {
                    StatementAnalysis statementAnalysis = ma.getMethodAnalysis().getLastStatement();
                    return statementAnalysis != null && statementAnalysis.noIncompatiblePrecondition();
                })
                .filter(ma -> assigningMethods.contains(ma.getMethodInfo()) ||
                        !Collections.disjoint(ma.getMethodInfo().methodResolution.get().methodsOfOwnClassReached(), assigningMethods))
                .collect(Collectors.toSet());
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
                        .getProperty(Property.MODIFIED_METHOD).causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (modificationDelays.isDelayed()) {
            log(DELAYED, "Delaying only mark E2 in {}, modification delayed", typeInfo.fullyQualifiedName);
            typeAnalysis.setApprovedPreconditionsE2Delays(modificationDelays);
            return modificationDelays;
        }

        CausesOfDelay preconditionForEventualDelays = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD).valueIsTrue())
                .map(ma -> ma.getMethodAnalysis().preconditionForEventualStatus())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (preconditionForEventualDelays.isDelayed()) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}",
                    typeInfo.fullyQualifiedName);
            typeAnalysis.setApprovedPreconditionsE2Delays(preconditionForEventualDelays);
            return preconditionForEventualDelays;
        }
        Optional<MethodAnalyser> optEmptyPreconditions = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD).valueIsTrue() &&
                        ma.getMethodAnalysis().getPreconditionForEventual() == null)
                .findFirst();
        if (optEmptyPreconditions.isPresent()) {
            log(EVENTUALLY, "Not all modifying methods have a valid precondition in {}: (findFirst) {}",
                    typeInfo.fullyQualifiedName, optEmptyPreconditions.get().getMethodInfo().fullyQualifiedName);
            typeAnalysis.freezeApprovedPreconditionsE2();
            return DONE;
        }

        Map<FieldReference, Expression> tempApproved = new HashMap<>();
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD);
            if (modified.valueIsTrue()) {
                Precondition precondition = methodAnalyser.getMethodAnalysis().getPreconditionForEventual();
                if (precondition != null) {
                    HandlePrecondition hp = handlePrecondition(methodAnalyser, precondition, iteration);
                    if (hp.causesOfDelay.isDelayed()) {
                        log(EVENTUALLY, "Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
                        typeAnalysis.setApprovedPreconditionsE2Delays(hp.causesOfDelay);
                        return hp.causesOfDelay;
                    }
                    for (FieldToCondition fieldToCondition : hp.fieldToConditions) {
                        Expression inMap = fieldToCondition.overwrite ?
                                tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                                !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                        tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                        if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                            messages.add(Message.newMessage(fieldToCondition.fieldReference.fieldInfo.newLocation(),
                                    Message.Label.DUPLICATE_MARK_CONDITION, fieldToCondition.fieldReference.fullyQualifiedName()));
                        }
                    }
                }
            }
        }
        tempApproved.putAll(approvedPreconditionsFromParent(typeInfo, true));

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis::putInApprovedPreconditionsE2);
        typeAnalysis.freezeApprovedPreconditionsE2();
        log(EVENTUALLY, "Approved preconditions {} in {}, type can now be @E2Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return DONE;
    }

    private record HandlePrecondition(List<FieldToCondition> fieldToConditions, CausesOfDelay causesOfDelay) {
    }

    private record FieldToCondition(FieldReference fieldReference, Expression condition, Expression negatedCondition,
                                    boolean overwrite) {
    }

    private HandlePrecondition handlePrecondition(MethodAnalyser methodAnalyser,
                                                  Precondition precondition,
                                                  int iteration) {
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), null);
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
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
                FieldReference adjustedFieldReference = analyserContext.adjustThis(e.getKey());
                fieldToConditions.add(new FieldToCondition(adjustedFieldReference, e.getValue(),
                        Negation.negate(evaluationContext, e.getValue()), isMark.valueIsTrue()));
            }
        }
        return new HandlePrecondition(fieldToConditions, causesOfDelay);
    }

    private AnalysisStatus analyseContainer() {
        DV container = typeAnalysis.getProperty(Property.CONTAINER);
        if (container.isDone()) return DONE;

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(Property.CONTAINER);
        if (MARKER != parentOrEnclosing.status) return parentOrEnclosing.status;

        CausesOfDelay fieldsReady = myFieldAnalysers.stream()
                .map(fa -> fa.getFieldAnalysis().getProperty(Property.FINAL).valueIsFalse()
                        ? CausesOfDelay.EMPTY : fa.getFieldAnalysis().getValue().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (fieldsReady.isDelayed()) {
            log(DELAYED, "Delaying container, need effectively final value to be known for final fields");
            typeAnalysis.setProperty(Property.CONTAINER, fieldsReady);
            return fieldsReady;
        }
        CausesOfDelay allReady = myMethodAndConstructorAnalysersExcludingSAMs.stream()
                .map(MethodAnalyser::fromFieldToParametersStatus)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (allReady.isDelayed()) {
            log(DELAYED, "Delaying container, variables linked to fields and params not yet set");
            typeAnalysis.setProperty(Property.CONTAINER, allReady);
            return allReady;
        }
        for (MethodAnalyser methodAnalyser : myMethodAndConstructorAnalysersExcludingSAMs) {
            if (!methodAnalyser.getMethodInfo().isPrivate()) {
                for (ParameterInfo parameterInfo : methodAnalyser.getMethodInspection().getParameters()) {
                    ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                    DV modified = parameterAnalysis.getProperty(Property.MODIFIED_VARIABLE);
                    if (modified.isDelayed() && methodAnalyser.hasCode()) {
                        log(DELAYED, "Delaying container, modification of parameter {} undecided",
                                parameterInfo.fullyQualifiedName());
                        typeAnalysis.setProperty(Property.CONTAINER, modified);
                        return modified.causesOfDelay(); // cannot yet decide
                    }
                    if (modified.valueIsTrue()) {
                        log(TYPE_ANALYSER, "{} is not a @Container: the content of {} is modified in {}",
                                typeInfo.fullyQualifiedName,
                                parameterInfo.fullyQualifiedName(),
                                methodAnalyser.getMethodInfo().distinguishingName());
                        typeAnalysis.setProperty(Property.CONTAINER, DV.FALSE_DV);
                        return DONE;
                    }
                }
            }
        }
        typeAnalysis.setProperty(Property.CONTAINER, DV.TRUE_DV);
        log(TYPE_ANALYSER, "Mark {} as @Container", typeInfo.fullyQualifiedName);
        return DONE;
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

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(Property.INDEPENDENT);
        if (MARKER != parentOrEnclosing.status) return parentOrEnclosing.status;

        DV valueFromFields = myFieldAnalysers.stream()
                .filter(fa -> !fa.getFieldInfo().isPrivate())
                .map(fa -> independenceOfField(fa.getFieldAnalysis()))
                .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
        if (valueFromFields.isDelayed()) {
            log(DELAYED, "Independence of type {} delayed, waiting for field independence",
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
                    .map(pa -> pa.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT))
                    .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            if (valueFromMethodParameters.isDelayed()) {
                log(DELAYED, "Independence of type {} delayed, waiting for parameter independence",
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
                    .filter(ma -> !ma.getMethodInfo().isPrivate() && ma.getMethodInfo().hasReturnValue())
                    .map(ma -> ma.getMethodAnalysis().getProperty(Property.INDEPENDENT))
                    .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            if (valueFromMethodReturnValue.isDelayed()) {
                log(DELAYED, "Independence of type {} delayed, waiting for method independence",
                        typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(Property.INDEPENDENT, valueFromMethodReturnValue);
                return valueFromMethodReturnValue.causesOfDelay();
            }
        }
        DV finalValue = parentOrEnclosing.maxValue
                .min(valueFromMethodReturnValue)
                .min(valueFromFields)
                .min(valueFromMethodParameters);
        log(INDEPENDENCE, "Set independence of type {} to {}", typeInfo.fullyQualifiedName, finalValue);
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
            log(DELAYED, "Waiting with {} on {}, parent or enclosing class's status not yet known",
                    property, typeInfo.fullyQualifiedName);
            return new MaxValueStatus(min, min.causesOfDelay());
        }
        if (min.equals(property.falseDv)) {
            log(ANALYSER, "{} set to least value for {}, because of parent", typeInfo.fullyQualifiedName, property);
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
                log(DELAYED, "Delay on type {}, field {} effectively final not known yet",
                        typeInfo.fullyQualifiedName, fieldAnalyser.getFieldInfo().name);
                causes = causes.merge(effectivelyFinal.causesOfDelay());
            }
            if (effectivelyFinal.valueIsFalse()) {
                log(IMMUTABLE_LOG, "Type {} cannot be @E1Immutable, field {} is not effectively final",
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
        if (typeImmutable.isDone()) return DONE; // we have a decision already

        // effectively E1
        DV allMyFieldsFinal = allMyFieldsFinal();
        if (allMyFieldsFinal.isDelayed()) {
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
            parentEffective = MultiLevel.effectiveAtLevel(parentImmutable, MultiLevel.Level.IMMUTABLE_1);
        }

        DV fromParentOrEnclosing = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> typeAnalysis.getProperty(Property.IMMUTABLE))
                .reduce(Property.IMMUTABLE.bestDv, DV::min);
        if (fromParentOrEnclosing.isDelayed()) {
            log(DELAYED, "Waiting with immutable on {} for parent or enclosing types", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.IMMUTABLE, fromParentOrEnclosing);
            return fromParentOrEnclosing.causesOfDelay();
        }

        if (fromParentOrEnclosing.equals(MultiLevel.MUTABLE_DV)) {
            log(IMMUTABLE_LOG, "{} is not an E1Immutable, E2Immutable class, because parent or enclosing is Mutable",
                    typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.MUTABLE_DV);
            return DONE;
        }

        DV myWhenEXFails;
        boolean eventual;
        if (allMyFieldsFinal.valueIsFalse() || parentEffective != MultiLevel.Effective.EFFECTIVE) {
            CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(false);
            if (approvedDelays.isDelayed()) {
                log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 1 immutable", typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(Property.IMMUTABLE, approvedDelays);
                return approvedDelays;
            }

            boolean isEventuallyE1 = typeAnalysis.approvedPreconditionsIsNotEmpty(false);
            if (!isEventuallyE1 && parentEffective != MultiLevel.Effective.EVENTUAL) {
                log(IMMUTABLE_LOG, "Type {} is not eventually level 1 immutable", typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.MUTABLE_DV);
                return DONE;
            }
            myWhenEXFails = MultiLevel.EVENTUALLY_E1IMMUTABLE_DV;
            eventual = true;
        } else {
            CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(true);
            if (approvedDelays.isDelayed()) {
                log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(Property.IMMUTABLE, approvedDelays);
                return approvedDelays;
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
            log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                    " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.IMMUTABLE, approvedDelays);
            return approvedDelays;
        }

        int minLevel = MultiLevel.Level.IMMUTABLE_R.level; // can only go down!

        boolean haveToEnforcePrivateAndIndependenceRules = false;
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
                log(DELAYED, "Field {} not yet known if of transparent type, delaying @E2Immutable on type", fieldFQN);
                typeAnalysis.setProperty(Property.IMMUTABLE, transparentType);
                return transparentType.causesOfDelay();
            }
            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or E2+Immutable themselves
            // because of down-casts on non-primitives, e.g. from transparent type to explicit, we cannot rely on the static type
            DV fieldImmutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
            MultiLevel.Effective fieldE2Immutable;
            if (fieldIsOfOwnOrInnerClassType(fieldInfo)) {
                fieldE2Immutable = MultiLevel.Effective.EFFECTIVE; // FIXME as a consequence, anonymous types/lambda's etc can be non-private
            } else {
                fieldE2Immutable = MultiLevel.effectiveAtLevel(fieldImmutable, MultiLevel.Level.IMMUTABLE_2);

                // field is of the type of the class being analysed... it will not make the difference.
                if (fieldImmutable.isDelayed()) {

                    // field is of a type that is very closely related to the type being analysed; we're looking to break a delay
                    // here by requiring the rules, and saying that it is not eventual; see FunctionInterface_0
                    ParameterizedType concreteType = fieldAnalysis.concreteTypeNullWhenDelayed();
                    if (concreteType != null && concreteType.typeInfo != null &&
                            concreteType.typeInfo.topOfInterdependentClassHierarchy() == typeInfo.topOfInterdependentClassHierarchy()) {
                        fieldE2Immutable = MultiLevel.Effective.EVENTUAL_AFTER; // must follow rules, but is not eventual
                    } else {
                        log(DELAYED, "Field {} not known yet if @E2Immutable, delaying @E2Immutable on type", fieldFQN);
                        typeAnalysis.setProperty(Property.IMMUTABLE, fieldImmutable);
                        return fieldImmutable.causesOfDelay();
                    }
                }
            }

            // NOTE: the 2 values that matter now are EVENTUAL and EFFECTIVE; any other will lead to a field
            // that needs to follow the additional rules
            boolean isPrimitive = fieldInfo.type.isPrimitiveExcludingVoid();

            if (fieldE2Immutable == MultiLevel.Effective.EVENTUAL) {
                eventual = true;
                if (!typeAnalysis.eventuallyImmutableFields.contains(fieldInfo)) {
                    typeAnalysis.eventuallyImmutableFields.add(fieldInfo);
                }
            } else if (!isPrimitive) {
                boolean fieldRequiresRules = fieldAnalysis.isTransparentType().valueIsFalse()
                        && fieldE2Immutable != MultiLevel.Effective.EFFECTIVE;
                haveToEnforcePrivateAndIndependenceRules |= fieldRequiresRules;

                DV modified = fieldAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!eventual && modified.isDelayed()) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldFQN);
                    typeAnalysis.setProperty(Property.IMMUTABLE, modified);
                    return modified.causesOfDelay();
                }
                if (modified.valueIsTrue()) {
                    if (eventual) {
                        if (!typeAnalysis.containsApprovedPreconditionsE2(thisFieldInfo)) {
                            log(IMMUTABLE_LOG, "For {} to become eventually E2Immutable, modified field {} can only be modified in methods marked @Mark or @Only(before=)");
                            //checkThatTheOnlyModifyingMethodsHaveBeenMarked = true;
                        }
                    } else {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and its content is modified",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(Property.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                }

                // RULE 2: ALL @SupportData FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(Property.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                } else {
                    log(IMMUTABLE_LOG, "Ignoring private modifier check of {}, self-referencing", fieldFQN);
                }

                // we need to know the immutability level of the hidden content of the field
                Set<ParameterizedType> hiddenContent = typeAnalysis.hiddenContentLinkedTo(fieldInfo);
                DV minHiddenContentImmutable = hiddenContent.stream()
                        .map(pt -> analyserContext.defaultImmutable(pt, true))
                        .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                int immutableLevel = MultiLevel.oneLevelMoreFrom(minHiddenContentImmutable);
                minLevel = Math.min(minLevel, immutableLevel);
            }
        }

        if (haveToEnforcePrivateAndIndependenceRules) {

            for (MethodAnalyser constructor : myConstructors) {
                for (ParameterAnalysis parameterAnalysis : constructor.getParameterAnalyses()) {
                    DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                    if (independent.isDelayed()) {
                        log(DELAYED, "Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}",
                                constructor.getMethodInfo().distinguishingName());
                        typeAnalysis.setProperty(Property.IMMUTABLE, independent);
                        return independent.causesOfDelay(); //not decided
                    }
                    if (independent.equals(MultiLevel.DEPENDENT_DV)) {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because constructor is @Dependent",
                                typeInfo.fullyQualifiedName, constructor.getMethodInfo().name);
                        typeAnalysis.setProperty(Property.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                    int independentLevel = MultiLevel.oneLevelMoreFrom(independent);
                    minLevel = Math.min(minLevel, independentLevel);
                }
            }

            for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                if (methodAnalyser.getMethodInfo().isVoid()) continue; // we're looking at return types
                DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD);
                // in the eventual case, we only need to look at the non-modifying methods
                // calling a modifying method will result in an error
                if (modified.valueIsFalse() || !typeAnalysis.isEventual()) {
                    DV returnTypeImmutable = methodAnalyser.getMethodAnalysis().getProperty(Property.IMMUTABLE);

                    ParameterizedType returnType;
                    Expression srv = methodAnalyser.getMethodAnalysis().getSingleReturnValue();
                    if (srv != null) {
                        // concrete
                        returnType = srv.returnType();
                    } else {
                        // formal; this one may come earlier, but that's OK; the only thing it can do is facilitate a delay
                        returnType = analyserContext.getMethodInspection(methodAnalyser.getMethodInfo()).getReturnType();
                    }
                    boolean returnTypePartOfMyself = fieldIsOfOwnOrInnerClassType(returnType);
                    if (returnTypeImmutable.isDelayed() && !returnTypePartOfMyself) {
                        log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodAnalyser.getMethodInfo().distinguishingName());
                        typeAnalysis.setProperty(Property.IMMUTABLE, returnTypeImmutable);
                        return returnTypeImmutable.causesOfDelay();
                    }
                    MultiLevel.Effective returnTypeE2Immutable = MultiLevel.effectiveAtLevel(returnTypeImmutable, MultiLevel.Level.IMMUTABLE_2);
                    if (returnTypeE2Immutable.lt(MultiLevel.Effective.EVENTUAL)) {
                        // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
                        DV independent = methodAnalyser.getMethodAnalysis().getProperty(Property.INDEPENDENT);
                        if (independent.isDelayed()) {
                            if (returnType.typeInfo == typeInfo) {
                                log(IMMUTABLE_LOG, "Cannot decide if method {} is independent, but given that its return type is a self reference, don't care",
                                        methodAnalyser.getMethodInfo().fullyQualifiedName);
                            } else {
                                log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                        typeInfo.fullyQualifiedName, methodAnalyser.getMethodInfo().name);
                                typeAnalysis.setProperty(Property.IMMUTABLE, independent);
                                return independent.causesOfDelay(); //not decided
                            }
                        }
                        if (independent.equals(MultiLevel.DEPENDENT_DV)) {
                            log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.getMethodInfo().name);
                            typeAnalysis.setProperty(Property.IMMUTABLE, whenEXFails);
                            return DONE;
                        }
                        int independentLevel = MultiLevel.oneLevelMoreFrom(independent);
                        minLevel = Math.min(minLevel, independentLevel);
                    }
                }
            }
        }

        /*
        // IMPROVE I don't think this bit of code is necessary. provide a test
        if (checkThatTheOnlyModifyingMethodsHaveBeenMarked) {
            for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                if (modified == Level.TRUE) {
                    MethodAnalysis.Eventual e = methodAnalyser.methodAnalysis.getEventual();
                    log(DELAYED, "Need confirmation that method {} is @Mark or @Only(before)", methodAnalyser.methodInfo.fullyQualifiedName);
                    if (e == MethodAnalysis.DELAYED_EVENTUAL) return DELAYS;
                    if (e.notMarkOrBefore()) {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because method {} modifies after the precondition",
                                typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                }
            }
        }*/

        MultiLevel.Effective effective = eventual ? MultiLevel.Effective.EVENTUAL : MultiLevel.Effective.EFFECTIVE;
        DV finalValue = fromParentOrEnclosing.min(MultiLevel.composeImmutable(effective, minLevel));
        log(IMMUTABLE_LOG, "Set @Immutable of type {} to {}", typeInfo.fullyQualifiedName, finalValue);
        typeAnalysis.setProperty(Property.IMMUTABLE, finalValue);
        return DONE;
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

    private boolean fieldIsOfOwnOrInnerClassType(ParameterizedType type) {
        return type.typeInfo != null && type.typeInfo.isEnclosedIn(typeInfo);
    }

    private boolean fieldIsOfOwnOrInnerClassType(FieldInfo fieldInfo) {
        if (fieldIsOfOwnOrInnerClassType(fieldInfo.type)) {
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
                    log(TYPE_ANALYSER, "Type {} is @Singleton, found exactly one new object creation in field initialiser",
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
                log(DELAYED, "Delaying @Singleton on {} until precondition known");
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
                        log(TYPE_ANALYSER, "Type {} is a  @Singleton, found boolean variable with precondition",
                                typeInfo.fullyQualifiedName);
                        typeAnalysis.setProperty(Property.SINGLETON, DV.TRUE_DV);
                        return DONE;
                    }
                }
            }
        }

        // no hit
        log(TYPE_ANALYSER, "Type {} is not a  @Singleton", typeInfo.fullyQualifiedName);
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
            log(DELAYED, "Extension class: don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return e2Immutable.causesOfDelay();
        }
        if (e2Immutable.lt(MultiLevel.EVENTUALLY_E2IMMUTABLE_DV)) {
            log(TYPE_ANALYSER, "Type {} is not an @ExtensionClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
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
                    log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
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
                        log(DELAYED, "Delaying @ExtensionClass of {} until @NotNull of {} known", typeInfo.fullyQualifiedName,
                                methodInfo.name);
                        return notNull.causesOfDelay();
                    }
                    if (notNull.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                        log(TYPE_ANALYSER, "Type {} is not an @ExtensionClass, method {} does not have either a " +
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
        log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return DONE;
    }

    private AnalysisStatus analyseUtilityClass() {
        DV utilityClass = typeAnalysis.getProperty(Property.UTILITY_CLASS);
        if (utilityClass.isDone()) return DONE;

        DV e2Immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (e2Immutable.isDelayed()) {
            log(DELAYED, "Utility class: Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return e2Immutable.causesOfDelay();
        }
        if (e2Immutable.lt(MultiLevel.EVENTUALLY_E2IMMUTABLE_DV)) {
            log(TYPE_ANALYSER, "Type {} is not a @UtilityClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
            return DONE;
        }

        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (!methodInfo.methodInspection.get().isStatic()) {
                log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInspection.constructors()) {
            if (!constructor.isPrivate()) {
                log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }

        if (typeInspection.constructors().isEmpty()) {
            log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
            return DONE;
        }

        // and there should be no means of generating an object
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (methodAnalyser.getMethodInfo().methodResolution.get()
                    .methodsOfOwnClassReached().stream().anyMatch(m -> m.isConstructor && m.typeInfo == typeInfo)) {
                log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but an object of the class is created in method "
                        + methodAnalyser.getMethodInfo().fullyQualifiedName());
                typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.FALSE_DV);
                return DONE;
            }
        }

        typeAnalysis.setProperty(Property.UTILITY_CLASS, DV.TRUE_DV);
        log(TYPE_ANALYSER, "Type {} marked @UtilityClass", typeInfo.fullyQualifiedName);
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
            super(iteration, conditionManager, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }
    }
}
