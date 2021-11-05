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

import org.e2immu.analyser.analyser.util.AssignmentIncompatibleWithPrecondition;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.analyser.util.ExplicitTypes;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.annotation.NotModified;
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

public class ComputingTypeAnalyser extends TypeAnalyser {
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

        String fqn = typeInfo.fullyQualifiedName;
        assert createDelay(fqn, fqn + D_ASPECTS);

        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>()
                .add(FIND_ASPECTS, iteration -> findAspects())
                .add(ANALYSE_TRANSPARENT_TYPES, iteration -> analyseTransparentTypes())
                .add(ANALYSE_IMMUTABLE_CAN_BE_INCREASED, iteration -> analyseImmutableCanBeIncreasedByTypeParameters());

        if (!typeInfo.isInterface()) {
            builder.add(COMPUTE_APPROVED_PRECONDITIONS_E1, this::computeApprovedPreconditionsE1)
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
        if (typeAnalysis.immutableCanBeIncreasedByTypeParameters.isSet()) return DONE;
        if (!typeAnalysis.hiddenContentTypes.isSet()) return DELAYS;

        boolean res = typeAnalysis.hiddenContentTypes.get().types()
                .stream().anyMatch(t -> t.bestTypeInfo(analyserContext) == null);

        log(IMMUTABLE_LOG, "Immutable can be increased for {}? {}", typeInfo.fullyQualifiedName, res);
        typeAnalysis.immutableCanBeIncreasedByTypeParameters.set(res);
        return DONE;
    }

    /*

     */
    private AnalysisStatus analyseTransparentTypes() {
        if (typeAnalysis.hiddenContentTypes.isSet()) return DONE;

        // STEP 1: Ensure all my static sub-types have been processed, but wait if that's not possible

        if (!typeInspection.subTypes().isEmpty()) {
            // wait until all static subtypes have hidden content computed
            Optional<TypeInfo> opt = typeInspection.subTypes().stream()
                    .filter(TypeInfo::isStatic)
                    .filter(st -> {
                        TypeAnalysisImpl.Builder stAna = (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(st);
                        if (!stAna.hiddenContentTypes.isSet()) {
                            ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(st);
                            typeAnalyser.analyseTransparentTypes();
                        }
                        return !stAna.hiddenContentTypes.isSet();
                    })
                    .findFirst();
            if (opt.isPresent()) {
                log(DELAYED, "Hidden content of static nested class {} needs to be computed before that of enclosing class {}",
                        opt.get().simpleName, typeInfo.fullyQualifiedName);
                return DELAYS;
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
            if (typeAnalysisStaticEnclosing.hiddenContentTypes.isSet()) {
                typeAnalysis.hiddenContentTypes.copy(typeAnalysisStaticEnclosing.hiddenContentTypes);
                typeAnalysis.explicitTypes.copy(typeAnalysisStaticEnclosing.explicitTypes);
            } else {
                log(DELAYED, "Hidden content of inner class {} computed together with that of enclosing class {}",
                        typeInfo.simpleName, staticEnclosing.fullyQualifiedName);
                return DELAYS;
            }
            return DONE;
        }

        log(IMMUTABLE_LOG, "Computing transparent types for type {}", typeInfo.fullyQualifiedName);

        // STEP 3: collect from static nested types; we have ensured their presence

        Set<ParameterizedType> explicitTypesFromSubTypes = typeInspection.subTypes().stream()
                .filter(TypeInfo::isStatic)
                .flatMap(st -> {
                    TypeAnalysisImpl.Builder stAna = (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(st);
                    return stAna.explicitTypes.get().types().stream();
                }).collect(Collectors.toUnmodifiableSet());

        // STEP 5: ensure + collect from parent

        Set<ParameterizedType> explicitTypesFromParent;
        {
            TypeInfo parentClass = typeInspection.parentClass().typeInfo;
            if (Primitives.isJavaLangObject(parentClass)) {
                explicitTypesFromParent = analyserContext.getPrimitives().explicitTypesOfJLO();
            } else {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parentClass);
                if (!typeAnalysis.haveTransparentTypes() && typeInfo.primaryType() == parentClass.primaryType()) {
                    ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(parentClass);
                    typeAnalyser.analyseTransparentTypes();
                }
                if (typeAnalysis.haveTransparentTypes()) {
                    explicitTypesFromParent = typeAnalysis.getExplicitTypes(analyserContext);
                } else {
                    log(DELAYED, "Wait for hidden content types to arrive {}, parent {}", typeInfo.fullyQualifiedName,
                            parentClass.simpleName);
                    return DELAYS;
                }
            }
        }

        // STEP 6: ensure + collect interface types

        {
            for (ParameterizedType ifType : typeInspection.interfacesImplemented()) {
                TypeInfo ifTypeInfo = ifType.typeInfo;
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(ifTypeInfo);
                if (!typeAnalysis.haveTransparentTypes() && typeInfo.primaryType() == ifTypeInfo.primaryType()) {
                    ComputingTypeAnalyser typeAnalyser = (ComputingTypeAnalyser) analyserContext.getTypeAnalyser(ifTypeInfo);
                    typeAnalyser.analyseTransparentTypes();
                }
                if (!typeAnalysis.haveTransparentTypes()) {
                    log(DELAYED, "Wait for hidden content types to arrive {}, interface {}", typeInfo.fullyQualifiedName,
                            ifTypeInfo.simpleName);
                    return DELAYS;
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
        allTypes.removeIf(pt -> Primitives.isPrimitiveExcludingVoid(pt) || pt.typeInfo == typeInfo || pt.isUnboundWildcard());

        typeAnalysis.explicitTypes.set(new SetOfTypes(allExplicitTypes));
        typeAnalysis.hiddenContentTypes.set(new SetOfTypes(allTypes));
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
        if (typeAnalysis.approvedPreconditionsIsFrozen(false)) {
            return DONE;
        }
        Set<MethodAnalyser> assigningMethods = determineAssigningMethods();

        Optional<MethodAnalyser> allPreconditionsOnAssigningMethodsSet = assigningMethods.stream()
                .filter(methodAnalyser -> !methodAnalyser.methodAnalysis.preconditionForEventual.isSet()).findFirst();
        if (allPreconditionsOnAssigningMethodsSet.isPresent()) {
            assert translatedDelay(COMPUTE_APPROVED_PRECONDITIONS_E1,
                    allPreconditionsOnAssigningMethodsSet.get().methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL,
                    typeInfo.fullyQualifiedName + D_APPROVED_PRECONDITIONS_E1);
            log(DELAYED, "Not all precondition preps on assigning methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        Optional<MethodAnalyser> oEmpty = assigningMethods.stream()
                .filter(ma -> ma.methodAnalysis.preconditionForEventual.get().isEmpty())
                .findFirst();
        if (oEmpty.isPresent()) {
            log(EVENTUALLY, "Not all assigning methods have a valid precondition in {}; (findFirst) {}",
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
            assert translatedDelay(COMPUTE_APPROVED_PRECONDITIONS_E2,
                    optModificationDelay.get().methodInfo.fullyQualifiedName + D_MODIFIED_METHOD,
                    typeInfo.fullyQualifiedName + D_APPROVED_PRECONDITIONS_E2);
            log(DELAYED, "Delaying only mark E2, modification delayed of (findFirst) {}",
                    optModificationDelay.get().methodInfo.fullyQualifiedName);
            return DELAYS;
        }

        Optional<MethodAnalyser> preconditionForEventualNotYetSet = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) == Level.TRUE)
                .filter(ma -> !ma.methodAnalysis.preconditionForEventual.isSet())
                .findFirst();
        if (preconditionForEventualNotYetSet.isPresent()) {
            assert translatedDelay(COMPUTE_APPROVED_PRECONDITIONS_E2,
                    preconditionForEventualNotYetSet.get().methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL,
                    typeInfo.fullyQualifiedName + D_APPROVED_PRECONDITIONS_E2);

            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying; findFirst: {}",
                    typeInfo.fullyQualifiedName, preconditionForEventualNotYetSet.get().methodInfo.fullyQualifiedName);
            return DELAYS;
        }
        Optional<MethodAnalyser> optEmptyPreconditions = myMethodAnalysersExcludingSAMs.stream()
                .filter(ma -> ma.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) == Level.TRUE &&
                        ma.methodAnalysis.preconditionForEventual.get().isEmpty())
                .findFirst();
        if (optEmptyPreconditions.isPresent()) {
            log(EVENTUALLY, "Not all modifying methods have a valid precondition in {}: (findFirst) {}",
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
                        log(EVENTUALLY, "Delaying approved preconditions (no incompatible found yet) in {}", typeInfo.fullyQualifiedName);
                        return DELAYS;
                    }
                    for (FieldToCondition fieldToCondition : fields) {
                        Expression inMap = fieldToCondition.overwrite ?
                                tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) :
                                !tempApproved.containsKey(fieldToCondition.fieldReference) ?
                                        tempApproved.put(fieldToCondition.fieldReference, fieldToCondition.condition) : null;
                        if (inMap != null && !inMap.equals(fieldToCondition.condition) && !inMap.equals(fieldToCondition.negatedCondition)) {
                            messages.add(Message.newMessage(new Location(fieldToCondition.fieldReference.fieldInfo),
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

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.CONTAINER);
        if (parentOrEnclosing.status != PROGRESS) return parentOrEnclosing.status;

        boolean fieldsReady = myFieldAnalysers.stream().allMatch(
                fieldAnalyser -> fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE ||
                        fieldAnalyser.fieldAnalysis.getEffectivelyFinalValue() != null);
        if (!fieldsReady) {
            log(DELAYED, "Delaying container, need effectively final value to be known for final fields");
            return DELAYS;
        }
        boolean allReady = myMethodAndConstructorAnalysersExcludingSAMs.stream()
                .allMatch(MethodAnalyser::fromFieldToParametersIsDone);
        if (!allReady) {
            log(DELAYED, "Delaying container, variables linked to fields and params not yet set");
            return DELAYS;
        }
        for (MethodAnalyser methodAnalyser : myMethodAndConstructorAnalysersExcludingSAMs) {
            if (!methodAnalyser.methodInfo.isPrivate()) {
                for (ParameterInfo parameterInfo : methodAnalyser.methodInspection.getParameters()) {
                    ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                    int modified = parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE);
                    if (modified == Level.DELAY && methodAnalyser.hasCode()) {
                        log(DELAYED, "Delaying container, modification of parameter {} undecided",
                                parameterInfo.fullyQualifiedName());
                        return DELAYS; // cannot yet decide
                    }
                    if (modified == Level.TRUE) {
                        log(TYPE_ANALYSER, "{} is not a @Container: the content of {} is modified in {}",
                                typeInfo.fullyQualifiedName,
                                parameterInfo.fullyQualifiedName(),
                                methodAnalyser.methodInfo.distinguishingName());
                        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.FALSE);
                        return DONE;
                    }
                }
            }
        }
        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.TRUE);
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
        int typeIndependent = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
        if (typeIndependent != Level.DELAY) return DONE;

        MaxValueStatus parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(VariableProperty.INDEPENDENT);
        if (parentOrEnclosing.status != PROGRESS) return parentOrEnclosing.status;

        int valueFromFields = myFieldAnalysers.stream()
                .filter(fa -> !fa.fieldInfo.isPrivate())
                .mapToInt(fa -> independenceOfField(fa.fieldAnalysis))
                .min()
                .orElse(MultiLevel.INDEPENDENT);
        if (valueFromFields == Level.DELAY) {
            log(DELAYED, "Independence of type {} delayed, waiting for field independence",
                    typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        int valueFromMethodParameters;
        if (valueFromFields == MultiLevel.DEPENDENT) {
            valueFromMethodParameters = MultiLevel.DEPENDENT; // no need to compute anymore, at bottom anyway
        } else {
            valueFromMethodParameters = myMethodAndConstructorAnalysersExcludingSAMs.stream()
                    .filter(ma -> !ma.methodInfo.isPrivate(analyserContext))
                    .flatMap(ma -> ma.parameterAnalyses.stream())
                    .mapToInt(pa -> pa.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT))
                    .min()
                    .orElse(MultiLevel.INDEPENDENT);
            if (valueFromMethodParameters == Level.DELAY) {
                log(DELAYED, "Independence of type {} delayed, waiting for parameter independence",
                        typeInfo.fullyQualifiedName);
                return DELAYS;
            }
        }
        int valueFromMethodReturnValue;
        if (valueFromMethodParameters == MultiLevel.DEPENDENT) {
            valueFromMethodReturnValue = MultiLevel.DEPENDENT;
        } else {
            valueFromMethodReturnValue = myMethodAnalysersExcludingSAMs.stream()
                    .filter(ma -> !ma.methodInfo.isPrivate() && ma.methodInfo.hasReturnValue())
                    .mapToInt(ma -> ma.methodAnalysis.getProperty(VariableProperty.INDEPENDENT))
                    .min()
                    .orElse(MultiLevel.INDEPENDENT);
            if (valueFromMethodReturnValue == Level.DELAY) {
                log(DELAYED, "Independence of type {} delayed, waiting for method independence",
                        typeInfo.fullyQualifiedName);
                return DELAYS;
            }
        }
        int finalValue = Math.min(parentOrEnclosing.maxValue, Math.min(valueFromMethodReturnValue,
                Math.min(valueFromFields, valueFromMethodParameters)));
        log(INDEPENDENCE, "Set independence of type {} to {}", typeInfo.fullyQualifiedName,
                MultiLevel.niceIndependent(finalValue));
        typeAnalysis.setProperty(VariableProperty.INDEPENDENT, finalValue);
        return DONE;
    }

    private int independenceOfField(FieldAnalysis fieldAnalysis) {
        int immutable = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
        if (immutable < MultiLevel.EFFECTIVELY_E2IMMUTABLE) return MultiLevel.DEPENDENT;
        TypeInfo bestType = fieldAnalysis.getFieldInfo().type.bestTypeInfo(analyserContext);
        if (bestType == null) {
            return MultiLevel.INDEPENDENT_1;
        }
        int immutableLevel = MultiLevel.level(immutable);
        return MultiLevel.independentCorrespondingToImmutableLevel(immutableLevel);
    }

    private record MaxValueStatus(int maxValue, AnalysisStatus status) {
    }

    private MaxValueStatus parentOrEnclosingMustHaveTheSameProperty(VariableProperty variableProperty) {
        int[] propertyValues = parentAndOrEnclosingTypeAnalysis.stream()
                .mapToInt(typeAnalysis -> typeAnalysis.getProperty(variableProperty))
                .toArray();
        if (propertyValues.length == 0) return new MaxValueStatus(variableProperty.best, PROGRESS);
        int min = Arrays.stream(propertyValues).min().orElseThrow();
        if (min == Level.DELAY) {
            log(DELAYED, "Waiting with {} on {}, parent or enclosing class's status not yet known",
                    variableProperty, typeInfo.fullyQualifiedName);
            return new MaxValueStatus(Level.DELAY, DELAYS);
        }
        if (min == variableProperty.falseValue) {
            log(ANALYSER, "{} set to least value for {}, because of parent", typeInfo.fullyQualifiedName, variableProperty);
            typeAnalysis.setProperty(variableProperty, variableProperty.falseValue);
            return new MaxValueStatus(variableProperty.falseValue, DONE);
        }
        return new MaxValueStatus(min, PROGRESS);
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
                log(IMMUTABLE_LOG, "Type {} cannot be @E1Immutable, field {} is not effectively final",
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
     * RULE 2: All fields must be private, or their types must be level 2 immutable (incl. unbound, transparent)
     * <p>
     * RULE 3: All methods and constructors must be independent of the non-level 2 immutable fields
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
        int parentEffective;
        if (Primitives.isJavaLangObject(typeInspection.parentClass())) {
            parentEffective = MultiLevel.EFFECTIVE;
        } else {
            TypeInfo parentType = typeInspection.parentClass().typeInfo;
            int parentImmutable = analyserContext.getTypeAnalysis(parentType).getProperty(VariableProperty.IMMUTABLE);
            parentEffective = MultiLevel.effectiveAtLevel(parentImmutable, MultiLevel.LEVEL_1_IMMUTABLE);
        }

        int fromParentOrEnclosing = parentAndOrEnclosingTypeAnalysis.stream()
                .mapToInt(typeAnalysis -> typeAnalysis.getProperty(VariableProperty.IMMUTABLE)).min()
                .orElse(VariableProperty.IMMUTABLE.best);
        if (fromParentOrEnclosing == Level.DELAY) {
            assert translatedDelay(ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE,
                    parentAndOrEnclosingTypeAnalysis.stream()
                            .filter(t -> t.getProperty(VariableProperty.IMMUTABLE) == Level.DELAY)
                            .findFirst().orElseThrow().getTypeInfo().fullyQualifiedName + D_IMMUTABLE,
                    typeInfo.fullyQualifiedName + D_IMMUTABLE);
            log(DELAYED, "Waiting with immutable on {} for parent or enclosing types", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (fromParentOrEnclosing == MultiLevel.MUTABLE) {
            log(IMMUTABLE_LOG, "{} is not an E1Immutable, E2Immutable class, because parent or enclosing is Mutable",
                    typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
            return DONE;
        }

        int myWhenEXFails;
        int e1Component;
        boolean eventual;
        if (allMyFieldsFinal == Level.FALSE || parentEffective != MultiLevel.EFFECTIVE) {
            if (!typeAnalysis.approvedPreconditionsIsFrozen(false)) {
                log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 1 immutable", typeInfo.fullyQualifiedName);
                assert translatedDelay(ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE,
                        typeInfo.fullyQualifiedName + D_APPROVED_PRECONDITIONS_E1,
                        typeInfo.fullyQualifiedName + D_IMMUTABLE);
                return DELAYS;
            }
            boolean isEventuallyE1 = typeAnalysis.approvedPreconditionsIsNotEmpty(false);
            if (!isEventuallyE1 && parentEffective != MultiLevel.EVENTUAL) {
                log(IMMUTABLE_LOG, "Type {} is not eventually level 1 immutable", typeInfo.fullyQualifiedName);
                typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
                return DONE;
            }
            myWhenEXFails = MultiLevel.EVENTUALLY_E1IMMUTABLE;
            e1Component = MultiLevel.EVENTUAL;
            eventual = true;
        } else {
            if (!typeAnalysis.approvedPreconditionsIsFrozen(true)) {
                log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
                assert translatedDelay(ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE,
                        typeInfo.fullyQualifiedName + D_APPROVED_PRECONDITIONS_E2,
                        typeInfo.fullyQualifiedName + D_IMMUTABLE);
                return DELAYS;
            }
            myWhenEXFails = MultiLevel.EFFECTIVELY_E1IMMUTABLE;
            e1Component = MultiLevel.EFFECTIVE;
            // it is possible that all fields are final, yet some field's content is used as the precondition
            eventual = !typeAnalysis.approvedPreconditionsE2IsEmpty();
        }

        int whenEXFails = Math.min(fromParentOrEnclosing, myWhenEXFails);

        // E2
        // NOTE that we need to check 2x: needed in else of previous statement, but also if we get through if-side.
        if (!typeAnalysis.approvedPreconditionsIsFrozen(true)) {
            log(DELAYED, "Type {} is not effectively level 1 immutable, waiting for" +
                    " preconditions to find out if it is eventually level 2 immutable", typeInfo.fullyQualifiedName);
            assert translatedDelay(ANALYSE_EFFECTIVELY_EVENTUALLY_E2IMMUTABLE,
                    typeInfo.fullyQualifiedName + D_APPROVED_PRECONDITIONS_E2,
                    typeInfo.fullyQualifiedName + D_IMMUTABLE);
            return DELAYS;
        }

        int minLevel = MultiLevel.LEVEL_R_IMMUTABLE; // can only go down!

        boolean haveToEnforcePrivateAndIndependenceRules = false;
        boolean checkThatTheOnlyModifyingMethodsHaveBeenMarked = false;
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.fieldAnalysis;
            FieldInfo fieldInfo = fieldAnalyser.fieldInfo;
            if (fieldInfo.type.bestTypeInfo() == typeInfo) {
                // "self" type, ignored
                continue;
            }

            FieldReference thisFieldInfo = new FieldReference(analyserContext, fieldInfo);
            String fieldFQN = fieldInfo.fullyQualifiedName();

            if (fieldAnalysis.isTransparentType() == null) {
                log(DELAYED, "Field {} not yet known if of transparent type, delaying @E2Immutable on type", fieldFQN);
                return DELAYS;
            }
            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or E2+Immutable themselves
            // because of down-casts on non-primitives, e.g. from transparent type to explicit, we cannot rely on the static type
            int fieldImmutable = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
            int fieldE2Immutable = MultiLevel.effectiveAtLevel(fieldImmutable, MultiLevel.LEVEL_2_IMMUTABLE);

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
            boolean isPrimitive = Primitives.isPrimitiveExcludingVoid(fieldInfo.type);

            if (fieldE2Immutable == MultiLevel.EVENTUAL) {
                eventual = true;
                if (!typeAnalysis.eventuallyImmutableFields.contains(fieldInfo)) {
                    typeAnalysis.eventuallyImmutableFields.add(fieldInfo);
                }
            } else if (!isPrimitive) {
                boolean fieldRequiresRules = !fieldAnalysis.isTransparentType() && fieldE2Immutable != MultiLevel.EFFECTIVE;
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
                            log(IMMUTABLE_LOG, "For {} to become eventually E2Immutable, modified field {} can only be modified in methods marked @Mark or @Only(before=)");
                            checkThatTheOnlyModifyingMethodsHaveBeenMarked = true;
                        }
                    } else {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and its content is modified",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                }

                // RULE 2: ALL @SupportData FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                } else {
                    log(IMMUTABLE_LOG, "Ignoring private modifier check of {}, self-referencing", fieldFQN);
                }

                // we need to know the immutability level of the hidden content of the field
                Set<ParameterizedType> hiddenContent = typeAnalysis.hiddenContentLinkedTo(fieldInfo);
                int minHiddenContentImmutable = hiddenContent.stream()
                        .mapToInt(pt -> pt.defaultImmutable(analyserContext, true))
                        .min().orElse(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE);
                int immutableLevel = MultiLevel.oneLevelMoreFromValue(minHiddenContentImmutable);
                minLevel = Math.min(minLevel, immutableLevel);
            }
        }

        if (haveToEnforcePrivateAndIndependenceRules) {

            for (MethodAnalyser constructor : myConstructors) {
                for (ParameterAnalysis parameterAnalysis : constructor.parameterAnalyses) {
                    int independent = parameterAnalysis.getProperty(VariableProperty.INDEPENDENT);
                    if (independent == Level.DELAY) {
                        log(DELAYED, "Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}",
                                constructor.methodInfo.distinguishingName());
                        return DELAYS; //not decided
                    }
                    if (independent == MultiLevel.DEPENDENT) {
                        log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because constructor is @Dependent",
                                typeInfo.fullyQualifiedName, constructor.methodInfo.name);
                        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenEXFails);
                        return DONE;
                    }
                    int independentLevel = MultiLevel.oneLevelMoreFromValue(independent);
                    minLevel = Math.min(minLevel, independentLevel);
                }
            }

            for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
                if (methodAnalyser.methodInfo.isVoid()) continue; // we're looking at return types
                int modified = methodAnalyser.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                // in the eventual case, we only need to look at the non-modifying methods
                // calling a modifying method will result in an error
                if (modified == Level.FALSE || !typeAnalysis.isEventual()) {
                    int returnTypeImmutable = methodAnalyser.methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    int returnTypeE2Immutable = MultiLevel.effectiveAtLevel(returnTypeImmutable, MultiLevel.LEVEL_2_IMMUTABLE);

                    ParameterizedType returnType;
                    Expression srv = methodAnalyser.methodAnalysis.getSingleReturnValue();
                    if (srv != null) {
                        // concrete
                        returnType = srv.returnType();
                    } else {
                        // formal; this one may come earlier, but that's OK; the only thing it can do is facilitate a delay
                        returnType = analyserContext.getMethodInspection(methodAnalyser.methodInfo).getReturnType();
                    }
                    boolean returnTypePartOfMyself = returnTypeNestedOrChild(returnType);
                    if (returnTypeE2Immutable == MultiLevel.DELAY && !returnTypePartOfMyself) {
                        log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodAnalyser.methodInfo.distinguishingName());
                        return DELAYS;
                    }
                    if (returnTypeE2Immutable < MultiLevel.EVENTUAL) {
                        // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
                        int independent = methodAnalyser.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
                        if (independent == Level.DELAY) {
                            if (returnType.typeInfo == typeInfo) {
                                log(IMMUTABLE_LOG, "Cannot decide if method {} is independent, but given that its return type is a self reference, don't care",
                                        methodAnalyser.methodInfo.fullyQualifiedName);
                            } else {
                                log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                        typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                                return DELAYS; //not decided
                            }
                        }
                        if (independent == MultiLevel.DEPENDENT) {
                            log(IMMUTABLE_LOG, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                    typeInfo.fullyQualifiedName, methodAnalyser.methodInfo.name);
                            typeAnalysis.setProperty(VariableProperty.IMMUTABLE, whenEXFails);
                            return DONE;
                        }
                        int independentLevel = MultiLevel.oneLevelMoreFromValue(independent);
                        minLevel = Math.min(minLevel, independentLevel);
                    }

                    // FIXME parameters of functional/abstract type which expose data?

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

        int effective = eventual ? MultiLevel.EVENTUAL : MultiLevel.EFFECTIVE;
        int finalValue = Math.min(fromParentOrEnclosing, MultiLevel.compose(effective, minLevel));
        log(IMMUTABLE_LOG, "Set @Immutable of type {} to {}", typeInfo.fullyQualifiedName,
                MultiLevel.niceImmutable(finalValue));
        typeAnalysis.setProperty(VariableProperty.IMMUTABLE, finalValue);
        return DONE;
    }

    private boolean returnTypeNestedOrChild(ParameterizedType returnType) {
        if (returnType.typeInfo == null) return false;
        return returnTypeNestedOrChild(returnType.typeInfo);
    }

    private boolean returnTypeNestedOrChild(TypeInfo returnType) {
        if (returnType == typeInfo) return true;
        TypeInspection typeInspection = analyserContext.getTypeInspection(returnType);
        if (typeInspection.isStatic()) return false; // must be a nested (non-static) type
        return returnType.packageNameOrEnclosingType.isRight() &&
                returnTypeNestedOrChild(returnType.packageNameOrEnclosingType.getRight());
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
                        log(TYPE_ANALYSER, "Type {} is a  @Singleton, found boolean variable with precondition",
                                typeInfo.fullyQualifiedName);
                        typeAnalysis.setProperty(VariableProperty.SINGLETON, Level.TRUE);
                        return DONE;
                    }
                }
            }
        }

        // no hit
        log(TYPE_ANALYSER, "Type {} is not a  @Singleton", typeInfo.fullyQualifiedName);
        typeAnalysis.setProperty(VariableProperty.SINGLETON, Level.FALSE);
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
        int extensionClass = typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS);
        if (extensionClass != Level.DELAY) return DONE;

        int e2Immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (e2Immutable == Level.DELAY) {
            log(DELAYED, "Extension class: don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (e2Immutable < MultiLevel.EVENTUALLY_E2IMMUTABLE) {
            log(TYPE_ANALYSER, "Type {} is not an @ExtensionClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
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
                    log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
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
        typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, Level.fromBool(isExtensionClass));
        log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return DONE;
    }

    private AnalysisStatus analyseUtilityClass() {
        int utilityClass = typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS);
        if (utilityClass != Level.DELAY) return DELAYS;

        int e2Immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (e2Immutable == Level.DELAY) {
            log(DELAYED, "Utility class: Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return DELAYS;
        }
        if (e2Immutable < MultiLevel.EVENTUALLY_E2IMMUTABLE) {
            log(TYPE_ANALYSER, "Type {} is not a @UtilityClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return DONE;
        }

        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (!methodInfo.methodInspection.get().isStatic()) {
                log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return DONE;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInspection.constructors()) {
            if (!constructor.isPrivate()) {
                log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return DONE;
            }
        }

        if (typeInspection.constructors().isEmpty()) {
            log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return DONE;
        }

        // and there should be no means of generating an object
        for (MethodAnalyser methodAnalyser : myMethodAnalysersExcludingSAMs) {
            if (methodAnalyser.methodInfo.methodResolution.get().createObjectOfSelf()) {
                log(TYPE_ANALYSER, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but an object of the class is created in method "
                        + methodAnalyser.methodInfo.fullyQualifiedName());
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return DONE;
            }
        }

        typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.TRUE);
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
        public Stream<DelayDebugNode> streamNodes() {
            throw new UnsupportedOperationException();
        }
    }
}
