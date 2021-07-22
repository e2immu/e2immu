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

import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.analyser.util.DetectEventual;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class ComputingMethodAnalyser extends MethodAnalyser implements HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputingMethodAnalyser.class);

    public static final String STATEMENT_ANALYSER = "StatementAnalyser";
    public static final String OBTAIN_MOST_COMPLETE_PRECONDITION = "obtainMostCompletePrecondition";
    public static final String COMPUTE_MODIFIED = "computeModified";
    public static final String COMPUTE_MODIFIED_CYCLES = "computeModifiedCycles";
    public static final String COMPUTE_RETURN_VALUE = "computeReturnValue";
    public static final String COMPUTE_IMMUTABLE = "computeImmutable";
    public static final String DETECT_MISSING_STATIC_MODIFIER = "detectMissingStaticModifier";
    public static final String EVENTUAL_PREP_WORK = "eventualPrepWork";
    public static final String ANNOTATE_EVENTUAL = "annotateEventual";
    public static final String METHOD_IS_INDEPENDENT = "methodIsIndependent";

    private final TypeAnalysis typeAnalysis;
    public final StatementAnalyser firstStatementAnalyser;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final Set<PrimaryTypeAnalyser> locallyCreatedPrimaryTypeAnalysers = new HashSet<>();

    private Map<FieldInfo, FieldAnalyser> myFieldAnalysers;

    /*
    Note that MethodLevelData is not part of the shared state, as the "lastStatement", where it resides,
    is only computed in the first step of the analyser components.
     */
    private record SharedState(EvaluationContext evaluationContext) {
    }


    public ComputingMethodAnalyser(MethodInfo methodInfo,
                                   TypeAnalysis typeAnalysis,
                                   MethodAnalysisImpl.Builder methodAnalysis,
                                   List<? extends ParameterAnalyser> parameterAnalysers,
                                   List<ParameterAnalysis> parameterAnalyses,
                                   Map<CompanionMethodName, CompanionAnalyser> companionAnalysers,
                                   boolean isSAM,
                                   AnalyserContext analyserContextInput) {
        super(methodInfo, methodAnalysis, parameterAnalysers,
                parameterAnalyses, companionAnalysers, isSAM, analyserContextInput);
        assert methodAnalysis.analysisMode == Analysis.AnalysisMode.COMPUTED;
        this.typeAnalysis = typeAnalysis;

        Block block = methodInspection.getMethodBody();
        if (block == Block.EMPTY_BLOCK) {
            firstStatementAnalyser = null;
        } else {
            firstStatementAnalyser = StatementAnalyser.recursivelyCreateAnalysisObjects(analyserContext,
                    this, null, block.structure.statements(), "", true,
                    methodInfo.isSynchronized() || methodInfo.isConstructor ||
                            methodInfo.methodResolution.get().partOfConstruction() == MethodResolution.CallStatus.PART_OF_CONSTRUCTION);
            methodAnalysis.setFirstStatement(firstStatementAnalyser.statementAnalysis);
        }

        AnalyserComponents.Builder<String, SharedState> builder = new AnalyserComponents.Builder<>();
        if (firstStatementAnalyser != null) {

            // order: Companion analyser, Parameter analysers, Statement analysers, Method analyser parts
            // rest of the order (as determined in PrimaryTypeAnalyser): fields, types

            for (CompanionAnalyser companionAnalyser : companionAnalysers.values()) {
                builder.add(companionAnalyser.companionMethodName.toString(), (sharedState ->
                        companionAnalyser.analyse(sharedState.evaluationContext.getIteration())));
            }

            for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
                builder.add("Parameter " + parameterAnalyser.parameterInfo.name,
                        sharedState -> parameterAnalyser.analyse(sharedState.evaluationContext.getIteration()));
            }

            AnalysisStatus.AnalysisResultSupplier<SharedState> statementAnalyser = (sharedState) -> {
                StatementAnalyserResult result = firstStatementAnalyser.analyseAllStatementsInBlock(sharedState.evaluationContext.getIteration(),
                        ForwardAnalysisInfo.startOfMethod(analyserContext.getPrimitives()),
                        sharedState.evaluationContext.getClosure());
                this.messages.addAll(result.messages());
                this.locallyCreatedPrimaryTypeAnalysers.addAll(result.localAnalysers());
                return result.analysisStatus();
            };

            builder.add(STATEMENT_ANALYSER, statementAnalyser)
                    .add(OBTAIN_MOST_COMPLETE_PRECONDITION, (sharedState) -> obtainMostCompletePrecondition())
                    .add(COMPUTE_MODIFIED, (sharedState) -> methodInfo.isConstructor ? DONE : computeModified())
                    .add(COMPUTE_MODIFIED_CYCLES, (sharedState -> methodInfo.isConstructor ? DONE : computeModifiedInternalCycles()))
                    .add(COMPUTE_RETURN_VALUE, (sharedState) -> methodInfo.noReturnValue() ? DONE : computeReturnValue(sharedState))
                    .add(COMPUTE_IMMUTABLE, sharedState -> methodInfo.noReturnValue() ? DONE : computeImmutable())
                    .add(DETECT_MISSING_STATIC_MODIFIER, (iteration) -> methodInfo.isConstructor ? DONE : detectMissingStaticModifier())
                    .add(EVENTUAL_PREP_WORK, (sharedState) -> methodInfo.isConstructor ? DONE : eventualPrepWork(sharedState))
                    .add(ANNOTATE_EVENTUAL, (sharedState) -> methodInfo.isConstructor ? DONE : annotateEventual(sharedState))
                    .add(METHOD_IS_INDEPENDENT, this::methodIsIndependent);

        } else {
            methodAnalysis.minimalInfoForEmptyMethod(methodAnalysis.primitives);
            for(ParameterAnalyser parameterAnalyser: parameterAnalysers) {
                parameterAnalyser.parameterAnalysis.minimalInfoForEmptyMethod();
            }
        }
        analyserComponents = builder.build();
    }

    @Override
    public Stream<DelayDebugNode> streamNodes() {
        Stream<DelayDebugNode> localTypes = locallyCreatedPrimaryTypeAnalysers.stream()
                .flatMap(PrimaryTypeAnalyser::streamNodes);
        Stream<DelayDebugNode> statementStream = firstStatementAnalyser == null ? Stream.of() :
                firstStatementAnalyser.streamNodes();
        return Stream.concat(Stream.concat(super.streamNodes(), localTypes), statementStream);
    }

    @Override
    protected String where(String componentName) {
        return methodInfo.fullyQualifiedName + ":" + componentName;
    }

    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        ExpandableAnalyserContextImpl expandable = (ExpandableAnalyserContextImpl) analyserContext;
        typeAnalysers.forEach(expandable::addPrimaryTypeAnalyser);
    }

    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return locallyCreatedPrimaryTypeAnalysers.stream();
    }

    @Override
    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    @Override
    public void initialize() {
        super.initialize();

        Map<FieldInfo, FieldAnalyser> myFieldAnalysers = new HashMap<>();
        analyserContext.fieldAnalyserStream().forEach(analyser -> {
            if (analyser.fieldInfo.owner == methodInfo.typeInfo) {
                myFieldAnalysers.put(analyser.fieldInfo, analyser);
            }
        });
        this.myFieldAnalysers = Map.copyOf(myFieldAnalysers);
    }

    // called from primary type analyser
    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), closure);
        SharedState sharedState = new SharedState(evaluationContext);

        try {
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);

            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                    .debugConfiguration().afterMethodAnalyserVisitors();
            if (!visitors.isEmpty()) {
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                            evaluationContext, methodInfo, methodAnalysis,
                            parameterAnalyses, analyserComponents.getStatusesAsMap(),
                            this::getMessageStream));
                }
            }
            return analysisStatus;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    @Override
    public void makeImmutable() {
        if (firstStatementAnalyser != null) {
            firstStatementAnalyser.makeImmutable();
        }
    }

    private AnalysisStatus detectMissingStaticModifier() {
        if (!methodInfo.methodInspection.get().isStatic() && !methodInfo.typeInfo.isInterface() && methodInfo.isNotATestMethod()) {
            // we need to check if there's fields being read/assigned/
            if (absentUnlessStatic(VariableInfo::isRead) &&
                    absentUnlessStatic(VariableInfo::isAssigned) &&
                    !getThisAsVariable().isRead() &&
                    methodInfo.isNotOverridingAnyOtherMethod() &&
                    !methodInfo.methodInspection.get().isDefault()) {
                MethodResolution methodResolution = methodInfo.methodResolution.get();
                if (methodResolution.staticMethodCallsOnly()) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.Label.METHOD_SHOULD_BE_MARKED_STATIC));
                    return DONE;
                }
            }
        }
        return DONE;
    }

    private boolean absentUnlessStatic(Predicate<VariableInfo> variableProperty) {
        return methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fieldReference && fieldReference.scopeIsThis())
                .allMatch(vi -> !variableProperty.test(vi) || vi.variable().isStatic());
    }

    // simply copy from last statement, but only when set/delays over
    private AnalysisStatus obtainMostCompletePrecondition() {
        assert !methodAnalysis.precondition.isSet();
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();
        if (methodLevelData.combinedPrecondition.isVariable()) return DELAYS;
        methodAnalysis.precondition.set(methodLevelData.combinedPrecondition.get());
        return DONE;
    }

    private AnalysisStatus annotateEventual(SharedState sharedState) {
        assert !methodAnalysis.eventualIsSet();

        DetectEventual detectEventual = new DetectEventual(methodInfo, methodAnalysis,
                (TypeAnalysisImpl.Builder) typeAnalysis, analyserContext);
        MethodAnalysis.Eventual eventual = detectEventual.detect(sharedState.evaluationContext);
        if (eventual == MethodAnalysis.DELAYED_EVENTUAL) {
            return DELAYS;
        }
        methodAnalysis.setEventual(eventual);
        if (eventual == MethodAnalysis.NOT_EVENTUAL) {
            return DONE;
        }

        log(EVENTUALLY, "Marking {} with only data {}", methodInfo.distinguishingName(), eventual);
        AnnotationExpression annotation = detectEventual.makeAnnotation(eventual);
        methodAnalysis.annotations.put(annotation, true);
        return DONE;
    }

    private AnalysisStatus eventualPrepWork(SharedState sharedState) {
        assert !methodAnalysis.preconditionForEventual.isSet();

        TypeInfo typeInfo = methodInfo.typeInfo;
        List<FieldAnalysis> fieldAnalysesOfTypeInfo = myFieldAnalysers.values().stream()
                .map(fa -> (FieldAnalysis) fa.fieldAnalysis).toList();

        while (true) {
            // FIRST CRITERION: is there a non-@Final field?

            Optional<FieldAnalysis> haveDelayOnFinalFields = fieldAnalysesOfTypeInfo
                    .stream().filter(fa -> fa.getProperty(VariableProperty.FINAL) == Level.DELAY).findFirst();
            if (haveDelayOnFinalFields.isPresent()) {
                log(DELAYED, "Delaying eventual in {} until we know about @Final of (findFirst) field",
                        methodInfo.fullyQualifiedName, haveDelayOnFinalFields.get());
                assert translatedDelay(EVENTUAL_PREP_WORK, haveDelayOnFinalFields.get()
                                .getFieldInfo().fullyQualifiedName() + D_FINAL,
                        methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL);
                return DELAYS;
            }

            boolean haveNonFinalFields = fieldAnalysesOfTypeInfo
                    .stream().anyMatch(fa -> fa.getProperty(VariableProperty.FINAL) == Level.FALSE);
            if (haveNonFinalFields) {
                break;
            }

            // SECOND CRITERION: is there an eventually immutable field? We can ignore delays if the concrete type of the field
            // is the same as the owner

            Optional<FieldAnalysis> delayOnImmutableFields = fieldAnalysesOfTypeInfo
                    .stream()
                    .filter(fa -> {
                        ParameterizedType concreteType = fa.concreteTypeNullWhenDelayed();
                        boolean acceptDelay = concreteType == null || concreteType.typeInfo != null &&
                                concreteType.typeInfo.topOfInterdependentClassHierarchy() !=
                                        fa.getFieldInfo().owner.topOfInterdependentClassHierarchy();
                        return acceptDelay && fa.getProperty(VariableProperty.EXTERNAL_IMMUTABLE) == Level.DELAY;
                    }).findFirst();
            if (delayOnImmutableFields.isPresent()) {
                log(DELAYED, "Delaying eventual in {} until we know about @Immutable of (findFirst) field {}",
                        methodInfo.fullyQualifiedName, delayOnImmutableFields.get().getFieldInfo().name);
                assert translatedDelay(EVENTUAL_PREP_WORK, delayOnImmutableFields.get()
                                .getFieldInfo().fullyQualifiedName() + D_EXTERNAL_IMMUTABLE,
                        methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL);
                return DELAYS;
            }

            boolean haveEventuallyImmutableFields = fieldAnalysesOfTypeInfo
                    .stream()
                    .anyMatch(fa -> {
                        int immutable = fa.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
                        return MultiLevel.isEventuallyE1Immutable(immutable) || MultiLevel.isEventuallyE2Immutable(immutable);
                    });
            if (haveEventuallyImmutableFields) {
                break;
            }

            // THIRD CRITERION: is there a field whose content can change? Non-primitive, not implicitly immutable, not E2

            Optional<FieldAnalysis> haveDelayOnImplicitlyImmutableType = fieldAnalysesOfTypeInfo
                    .stream().filter(fa -> fa.isOfImplicitlyImmutableDataType() == null).findFirst();
            if (haveDelayOnImplicitlyImmutableType.isPresent()) {
                log(DELAYED, "Delaying eventual in {} until we know about implicitly immutable type of (findFirst) field {}",
                        methodInfo.fullyQualifiedName, haveDelayOnImplicitlyImmutableType.get().getFieldInfo().name);
                assert translatedDelay(EVENTUAL_PREP_WORK, haveDelayOnImplicitlyImmutableType.get()
                                .getFieldInfo().fullyQualifiedName() + D_IMPLICITLY_IMMUTABLE_DATA,
                        methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL);
                return DELAYS;
            }

            boolean haveContentChangeableField = fieldAnalysesOfTypeInfo
                    .stream().anyMatch(fa -> {
                        int immutable = fa.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
                        return !MultiLevel.isAtLeastEventuallyE2Immutable(immutable)
                                && !Primitives.isPrimitiveExcludingVoid(fa.getFieldInfo().type)
                                && !fa.isOfImplicitlyImmutableDataType();
                    });
            if (haveContentChangeableField) {
                break;
            }

            // GO UP TO PARENT

            ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass();
            if (Primitives.isJavaLangObject(parentClass)) {
                log(EVENTUALLY, "No eventual annotation in {}: found no non-final fields", methodInfo.distinguishingName());
                methodAnalysis.preconditionForEventual.set(Optional.empty());
                return DONE;
            }
            typeInfo = parentClass.bestTypeInfo();
            TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
            fieldAnalysesOfTypeInfo = typeInspection.fields().stream().map(analyserContext::getFieldAnalysis).toList();
        }

        if (!methodAnalysis.precondition.isSet()) {
            log(DELAYED, "Delaying compute @Only and @Mark, precondition not set (weird, should be set by now)");
            assert translatedDelay(EVENTUAL_PREP_WORK, methodInfo.fullyQualifiedName + D_PRECONDITION,
                    methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL);
            return DELAYS;
        }
        Precondition precondition = methodAnalysis.precondition.get();
        if (precondition.isEmpty()) {

            // code to detect the situation as in Lazy
            Precondition combinedPrecondition = null;
            for (FieldAnalyser fieldAnalyser : myFieldAnalysers.values()) {
                if (fieldAnalyser.fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE) {
                    FieldReference fr = new FieldReference(analyserContext, fieldAnalyser.fieldInfo);
                    StatementAnalysis beforeAssignment = statementBeforeAssignment(fr);
                    if (beforeAssignment != null) {
                        ConditionManager cm = beforeAssignment.stateData.conditionManagerForNextStatement.get();
                        if (cm.stateIsDelayed() != null) {
                            log(DELAYED, "Delaying compute @Only, @Mark, delay in state {} {}", beforeAssignment.index,
                                    methodInfo.fullyQualifiedName);
                            assert translatedDelay(EVENTUAL_PREP_WORK,
                                    beforeAssignment.fullyQualifiedName() + D_CONDITION_MANAGER_FOR_NEXT_STMT,
                                    methodInfo.fullyQualifiedName + D_PRECONDITION_FOR_EVENTUAL);
                            return DELAYS;
                        }
                        Expression state = cm.state();
                        if (!state.isBoolValueTrue()) {
                            Filter filter = new Filter(sharedState.evaluationContext, Filter.FilterMode.ACCEPT);
                            Filter.FilterResult<FieldReference> filterResult = filter.filter(state,
                                    filter.individualFieldClause(analyserContext, true));
                            Expression inResult = filterResult.accepted().get(fr);
                            if (inResult != null) {
                                Precondition pc = new Precondition(inResult, List.of(new Precondition.StateCause()));
                                if (combinedPrecondition == null) {
                                    combinedPrecondition = pc;
                                } else {
                                    combinedPrecondition = combinedPrecondition.combine(sharedState.evaluationContext, pc);
                                }
                            }
                        }
                    }
                }
            }
            log(EVENTUALLY, "No @Mark @Only annotation in {} from precondition, found {} from assignment",
                    methodInfo.distinguishingName(), combinedPrecondition);
            methodAnalysis.preconditionForEventual.set(Optional.ofNullable(combinedPrecondition));
            return DONE;
        }

        Filter filter = new Filter(sharedState.evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(precondition.expression(),
                filter.individualFieldClause(analyserContext));
        if (filterResult.accepted().isEmpty()) {
            log(EVENTUALLY, "No @Mark/@Only annotation in {}: found no individual field preconditions",
                    methodInfo.distinguishingName());
            methodAnalysis.preconditionForEventual.set(Optional.empty());
            return DONE;
        }
        Expression[] preconditionExpressions = filterResult.accepted().values().toArray(Expression[]::new);
        log(EVENTUALLY, "Did prep work for @Only, @Mark, found precondition on variables {} in {}", precondition,
                filterResult.accepted().keySet(), methodInfo.distinguishingName());

        Expression and = new And(sharedState.evaluationContext().getPrimitives()).append(sharedState.evaluationContext,
                preconditionExpressions);
        methodAnalysis.preconditionForEventual.set(Optional.of(new Precondition(and, precondition.causes())));
        return DONE;
    }

    private static final Pattern INDEX_PATTERN = Pattern.compile("(\\d+)(-C|-E|:M)");

    private StatementAnalysis statementBeforeAssignment(FieldReference fieldReference) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        VariableInfo vi = lastStatement.findOrNull(fieldReference, VariableInfoContainer.Level.MERGE);
        if (vi != null && vi.isAssigned()) {
            Matcher m = INDEX_PATTERN.matcher(vi.getAssignmentIds().getLatestAssignment());
            if (m.matches()) {
                int index = Integer.parseInt(m.group(1)) - 1;
                if (index >= 0) {
                    String id = "" + index; // TODO numeric padding to same length
                    return findStatementAnalyser(id).statementAnalysis;
                }
            }
        }
        return null;
    }

    private final static Set<VariableProperty> READ_FROM_RETURN_VALUE_PROPERTIES = Set.of(CONTAINER);

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private AnalysisStatus computeReturnValue(SharedState sharedState) {
        assert !methodAnalysis.singleReturnValue.isSet();

        // some immediate short-cuts.
        // if we cannot cast 'this' to the current type or the other way round, the method cannot be fluent
        // see Fluent_0 for one way, and Store_7 for the other direction
        ParameterizedType myType = methodInfo.typeInfo.asParameterizedType(analyserContext);
        if (!myType.isAssignableFromTo(analyserContext, methodInspection.getReturnType())) {
            methodAnalysis.setProperty(VariableProperty.FLUENT, Level.FALSE);
        }

        /*
        down-or-upcast allowed
         */
        if (methodInspection.getParameters().isEmpty() ||
                !methodInspection.getReturnType().isAssignableFromTo(analyserContext,
                        methodInspection.getParameters().get(0).parameterizedType)) {
            methodAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        }

        VariableInfo variableInfo = getReturnAsVariable();
        Expression valueWithReturn = variableInfo.getValue();
        if (variableInfo.isDelayed() || valueWithReturn.isInitialReturnExpression()) {

            // it is possible that none of the return statements are reachable... in which case there should be no delay,
            // and no SRV
            if (noReturnStatementReachable()) {
                methodAnalysis.singleReturnValue.set(new UnknownExpression(methodInfo.returnType(), "does not return a value"));
                methodAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
                methodAnalysis.setProperty(VariableProperty.FLUENT, Level.FALSE);
                methodAnalysis.setProperty(VariableProperty.NOT_NULL_EXPRESSION, VariableProperty.NOT_NULL_EXPRESSION.best);
                methodAnalysis.setProperty(VariableProperty.IMMUTABLE, VariableProperty.IMMUTABLE.best);
                methodAnalysis.setProperty(VariableProperty.CONTAINER, VariableProperty.CONTAINER.best);
                return DONE;
            }
            log(DELAYED, "Method {} has return value {}, delaying", methodInfo.distinguishingName(),
                    valueWithReturn.debugOutput());
            return DELAYS;
        }

        Expression value;
        boolean computeNotNullFromValue;
        if (methodAnalysis.getFirstStatement().lastStatementIsEscape()) {
            // we may need to remove the
            value = removeInitialReturnExpression(valueWithReturn);
            computeNotNullFromValue = true;
        } else {
            value = valueWithReturn;
            computeNotNullFromValue = false;
        }
        // try to compute the dynamic immutable status of value

        Expression valueBeforeInlining = value;
        if (!value.isConstant()) {
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
            if (modified == Level.DELAY) {
                assert translatedDelay(COMPUTE_RETURN_VALUE, methodInfo.fullyQualifiedName + D_MODIFIED_METHOD,
                        methodInfo.fullyQualifiedName + D_METHOD_RETURN_VALUE);

                log(DELAYED, "Delaying return value of {}, waiting for MODIFIED (we may try to inline!)", methodInfo.distinguishingName);
                return DELAYS;
            }
            if (modified == Level.FALSE) {
                InlinedMethod.Applicability applicability = applicability(value);
                if (applicability != InlinedMethod.Applicability.NONE) {
                    value = new InlinedMethod(methodInfo, replaceFields(sharedState.evaluationContext, value), applicability);
                }
            }
        }
        /*
        in normal situations, the @NotNull value is taken from the return value.
        When the last statement escapes, this value will have been changed; very likely, at least one
        inline conditional has been removed. We compute not-null directly on the value
        TODO this may result in evaluation trouble -- local variables not known to the method analyser
         */
        int notNull = computeNotNullFromValue ?
                value.getProperty(sharedState.evaluationContext, NOT_NULL_EXPRESSION, false) :
                variableInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION);

        if (notNull != Level.DELAY) {
            methodAnalysis.setProperty(VariableProperty.NOT_NULL_EXPRESSION, notNull);
        } else {
            log(DELAYED, "Delaying return value of {}, waiting for NOT_NULL", methodInfo.fullyQualifiedName);
            return DELAYS;
        }

        boolean valueIsConstantField;
        VariableExpression ve;
        if (value instanceof InlinedMethod inlined
                && (ve = inlined.expression().asInstanceOf(VariableExpression.class)) != null
                && ve.variable() instanceof FieldReference fieldReference) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            int constantField = fieldAnalysis.getProperty(VariableProperty.CONSTANT);
            if (constantField == Level.DELAY) {
                log(DELAYED, "Delaying return value of {}, waiting for effectively final value's @Constant designation",
                        methodInfo.distinguishingName);
                return DELAYS;
            }
            valueIsConstantField = constantField == Level.TRUE;
        } else valueIsConstantField = false;

        boolean isConstant = value.isConstant() || valueIsConstantField;

        methodAnalysis.singleReturnValue.set(value);
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (isConstant) {
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, value);
            methodAnalysis.annotations.put(constantAnnotation, true);
        } else {
            methodAnalysis.annotations.put(e2.constant, false);
        }
        methodAnalysis.setProperty(VariableProperty.CONSTANT, Level.fromBool(isConstant));
        log(METHOD_ANALYSER, "Mark method {} as @Constant? {}", methodInfo.fullyQualifiedName(), isConstant);

        VariableExpression vv;
        boolean isFluent = (vv = valueBeforeInlining.asInstanceOf(VariableExpression.class)) != null &&
                vv.variable() instanceof This thisVar &&
                thisVar.typeInfo == methodInfo.typeInfo;
        methodAnalysis.setProperty(VariableProperty.FLUENT, Level.fromBool(isFluent));
        log(METHOD_ANALYSER, "Mark method {} as @Fluent? {}", methodInfo.fullyQualifiedName(), isFluent);

        int currentIdentity = methodAnalysis.getProperty(IDENTITY);
        if (currentIdentity == Level.DELAY) {
            VariableExpression ve2;
            boolean isIdentity = (ve2 = valueBeforeInlining.asInstanceOf(VariableExpression.class)) != null &&
                    ve2.variable() instanceof ParameterInfo pi && pi.getMethod() == methodInfo && pi.index == 0;
            methodAnalysis.setProperty(IDENTITY, Level.fromBool(isIdentity));
        }
        // this is pretty dangerous for IDENTITY / will work for CONTAINER
        for (VariableProperty variableProperty : READ_FROM_RETURN_VALUE_PROPERTIES) {
            int v = variableInfo.getProperty(variableProperty);
            if (v == Level.DELAY) v = variableProperty.falseValue;
            methodAnalysis.setProperty(variableProperty, v);
            log(NOT_NULL, "Set {} of {} to value {}", variableProperty, methodInfo.fullyQualifiedName, v);
        }

        return DONE;
    }

    private Expression removeInitialReturnExpression(Expression value) {
        if (value instanceof InlineConditional inline) {
            if (inline.ifTrue.isInitialReturnExpression()) return removeInitialReturnExpression(inline.ifFalse);
            if (inline.ifFalse.isInitialReturnExpression()) return removeInitialReturnExpression(inline.ifTrue);
            return new InlineConditional(analyserContext, inline.condition, removeInitialReturnExpression(inline.ifTrue),
                    removeInitialReturnExpression(inline.ifFalse));
        }
        return value;
    }

    private AnalysisStatus computeImmutable() {
        if (!methodAnalysis.singleReturnValue.isSet()) {
            assert translatedDelay(COMPUTE_IMMUTABLE, methodInfo.fullyQualifiedName + D_METHOD_RETURN_VALUE,
                    methodInfo.fullyQualifiedName + D_IMMUTABLE);
            log(DELAYED, "Delaying @Immutable on {} until return value is set", methodInfo.fullyQualifiedName);
            return DELAYS;
        }
        Expression expression = methodAnalysis.singleReturnValue.get();
        int immutable;
        if (expression.isConstant()) {
            immutable = MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        } else {
            VariableInfo variableInfo = getReturnAsVariable();
            ParameterizedType pt = variableInfo.variable().parameterizedType();
            if (pt.arrays == 0 && pt.typeInfo == methodInfo.typeInfo) {
                /* the reason we introduce this "hack" is that IMMUTABLE of my own type often comes late
                it really is a hack because other types that come late are missed as well.
                 */
                immutable = typeAnalysis.getProperty(IMMUTABLE);

                assert immutable != Level.DELAY || translatedDelay(COMPUTE_IMMUTABLE,
                        typeAnalysis.getTypeInfo().fullyQualifiedName + D_IMMUTABLE,
                        methodInfo.fullyQualifiedName + D_IMMUTABLE);
            } else {
                int dynamic = variableInfo.getProperty(IMMUTABLE);
                assert dynamic != Level.DELAY || translatedDelay(COMPUTE_IMMUTABLE,
                        variableInfo.variable().fullyQualifiedName() + "@" + methodAnalysis.getLastStatement().index + D_IMMUTABLE,
                        methodInfo.fullyQualifiedName + D_IMMUTABLE);

                int dynamicExt = variableInfo.getProperty(EXTERNAL_IMMUTABLE);
                assert dynamicExt != Level.DELAY || translatedDelay(COMPUTE_IMMUTABLE,
                        variableInfo.variable().fullyQualifiedName() + "@" + methodAnalysis.getLastStatement().index + D_EXTERNAL_IMMUTABLE,
                        methodInfo.fullyQualifiedName + D_IMMUTABLE);

                int formalImmutable = methodInfo.returnType().defaultImmutable(analyserContext);
                assert formalImmutable != Level.DELAY || translatedDelay(COMPUTE_IMMUTABLE,
                        methodInfo.returnType().bestTypeInfo().fullyQualifiedName() + D_IMMUTABLE,
                        methodInfo.fullyQualifiedName + D_IMMUTABLE);

                immutable = dynamic == Level.DELAY || dynamicExt == Level.DELAY || formalImmutable == Level.DELAY ?
                        Level.DELAY : Math.max(formalImmutable, Math.max(dynamicExt, dynamic));
            }
        }
        if (immutable == Level.DELAY) {
            log(DELAYED, "Delaying @Immutable on {}", methodInfo.fullyQualifiedName);
            return DELAYS;
        }
        methodAnalysis.setProperty(IMMUTABLE, immutable);
        log(IMMUTABLE_LOG, "Set @Immutable to {} on {}", immutable, methodInfo.fullyQualifiedName);
        return DONE;
    }

    /*
    replace local copies of fields to fields
     */
    private Expression replaceFields(EvaluationContext evaluationContext, Expression value) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        boolean change = false;
        for (Variable variable : value.variables()) {
            if (variable instanceof LocalVariableReference lvr) {
                FieldInfo fieldInfo = extractFieldFromLocal(lvr);
                if (fieldInfo != null) {
                    FieldReference fieldReference = new FieldReference(evaluationContext.getAnalyserContext(), fieldInfo);
                    builder.put(variable, fieldReference);
                    change = true;
                }
            }
        }
        if (!change) return value;
        return value.translate(builder.build());
    }

    private FieldInfo extractFieldFromLocal(LocalVariableReference lvr) {
        if (lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy &&
                copy.localCopyOf().scopeIsThis()) return copy.localCopyOf().fieldInfo;
        return null;
    }

    private boolean noReturnStatementReachable() {
        StatementAnalysis firstStatement = methodAnalysis.getFirstStatement();
        return !recursivelyFindReachableReturnStatement(firstStatement);
    }

    private static boolean recursivelyFindReachableReturnStatement(StatementAnalysis statementAnalysis) {
        StatementAnalysis sa = statementAnalysis;
        while (!sa.flowData.isUnreachable()) {
            if (sa.statement instanceof ReturnStatement) return true;
            for (Optional<StatementAnalysis> first : sa.navigationData.blocks.get()) {
                if (first.isPresent() && recursivelyFindReachableReturnStatement(first.get())) {
                    return true;
                }
            }
            if (sa.navigationData.next.get().isEmpty()) break;
            sa = sa.navigationData.next.get().get();
        }
        return false;
    }

    private InlinedMethod.Applicability applicabilityField(FieldInfo fieldInfo) {
        Set<FieldModifier> fieldModifiers = fieldInfo.fieldInspection.get().getModifiers();
        if (fieldModifiers.contains(FieldModifier.PRIVATE)) return InlinedMethod.Applicability.TYPE;
        if (fieldModifiers.contains(FieldModifier.PUBLIC)) return InlinedMethod.Applicability.EVERYWHERE;
        return InlinedMethod.Applicability.PACKAGE;
    }

    private InlinedMethod.Applicability applicability(Expression value) {
        AtomicReference<InlinedMethod.Applicability> applicability = new AtomicReference<>(InlinedMethod.Applicability.EVERYWHERE);
        value.visit(v -> {
            if (v.isUnknown()) {
                applicability.set(InlinedMethod.Applicability.NONE);
                return false;
            }
            NewObject newObject;
            if ((newObject = v.asInstanceOf(NewObject.class)) != null && newObject.constructor() == null) {
                applicability.set(InlinedMethod.Applicability.NONE);
                return false;
            }
            VariableExpression valueWithVariable;
            if ((valueWithVariable = v.asInstanceOf(VariableExpression.class)) != null) {
                Variable variable = valueWithVariable.variable();
                if (variable instanceof LocalVariableReference lvr) {
                    FieldInfo fieldInfo = extractFieldFromLocal(lvr);
                    if (fieldInfo != null) {
                        InlinedMethod.Applicability fieldApplicability = applicabilityField(fieldInfo);
                        InlinedMethod.Applicability current = applicability.get();
                        applicability.set(current.mostRestrictive(fieldApplicability));
                    } else {
                        // TODO make a distinction between a local variable, and a local var outside a lambda
                        applicability.set(InlinedMethod.Applicability.NONE);
                    }
                } else if (variable instanceof FieldReference) {
                    InlinedMethod.Applicability fieldApplicability = applicabilityField(((FieldReference) variable).fieldInfo);
                    InlinedMethod.Applicability current = applicability.get();
                    applicability.set(current.mostRestrictive(fieldApplicability));
                }
            }
            return true; // go deeper
        });
        return applicability.get();
    }

    private AnalysisStatus computeModifiedInternalCycles() {
        boolean isCycle = methodInfo.partOfCallCycle();
        if (!isCycle) return DONE;

        int modified = methodAnalysis.getProperty(VariableProperty.TEMP_MODIFIED_METHOD);
        TypeAnalysisImpl.Builder builder = (TypeAnalysisImpl.Builder) typeAnalysis;
        Set<MethodInfo> cycle = methodInfo.methodResolution.get().methodsOfOwnClassReached();
        TypeAnalysisImpl.CycleInfo cycleInfo = builder.nonModifiedCountForMethodCallCycle.getOrCreate(cycle, x -> new TypeAnalysisImpl.CycleInfo());

        // we decide for the group
        if (modified == Level.TRUE) {
            if (!cycleInfo.modified.isSet()) cycleInfo.modified.set();
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, Level.TRUE);
            return DONE;
        }

        // others have decided for us
        if (cycleInfo.modified.isSet()) {
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, Level.TRUE);
            return DONE;
        }

        if (modified == Level.FALSE) {
            if (!cycleInfo.nonModified.contains(methodInfo)) cycleInfo.nonModified.add(methodInfo);

            if (cycleInfo.nonModified.size() == cycle.size()) {
                methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, Level.FALSE);
                return DONE;
            }
        }
        // we all agree
        if (cycleInfo.nonModified.size() == cycle.size()) {
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, Level.FALSE);
            return DONE;
        }
        // wait

        return DELAYS;
    }

    private AnalysisStatus computeModified() {
        boolean isCycle = methodInfo.partOfCallCycle();
        VariableProperty variableProperty = isCycle ? VariableProperty.TEMP_MODIFIED_METHOD : VariableProperty.MODIFIED_METHOD;

        if (methodAnalysis.getProperty(variableProperty) != Level.DELAY) return DONE;
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        // first step, check (my) field assignments
        boolean fieldAssignments = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fr &&
                        fieldInMyTypeHierarchy(fr.fieldInfo, methodInfo.typeInfo))
                .anyMatch(VariableInfo::isAssigned);
        if (fieldAssignments) {
            log(MODIFICATION, "Method {} is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(variableProperty, Level.TRUE);
            return DONE;
        }

        // if there are no field assignments, there may be modifying method calls

        // second step, check that CM is present (this generally implies that links have been established)
        Optional<VariableInfo> noContextModified = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fr &&
                        fieldInMyTypeHierarchy(fr.fieldInfo, methodInfo.typeInfo))
                .filter(vi -> vi.getProperty(CONTEXT_MODIFIED) == Level.DELAY)
                .findFirst();
         if (noContextModified.isPresent()) {
            assert translatedDelay(COMPUTE_MODIFIED, methodInfo.fullyQualifiedName + ":" + methodAnalysis.getLastStatement().index + D_LINKS_HAVE_BEEN_ESTABLISHED,
                    methodInfo.fullyQualifiedName + D_MODIFIED_METHOD);
            log(DELAYED, "Method {}: Not deciding on @Modified yet: no context modified for {}",
                    methodInfo.distinguishingName(), noContextModified.get().variable().simpleName());
            return DELAYS;
        }
        boolean isModified = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .anyMatch(vi -> vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.TRUE);
        if (isModified && isLogEnabled(MODIFICATION)) {
            List<String> fieldsWithContentModifications =
                    methodAnalysis.getLastStatement().variableStream()
                            .filter(vi -> vi.variable() instanceof FieldReference)
                            .filter(vi -> vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.TRUE)
                            .map(VariableInfo::name).collect(Collectors.toList());
            log(MODIFICATION, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (!isModified) { // also in static cases, sometimes a modification is written to "this" (MethodCall)
            VariableInfo thisVariable = getThisAsVariable();
            if (thisVariable != null) {
                int thisModified = thisVariable.getProperty(VariableProperty.CONTEXT_MODIFIED);
                if (thisModified == Level.DELAY) {
                    assert translatedDelay(COMPUTE_MODIFIED,
                            thisVariable.variable().fullyQualifiedName() + "@" + methodAnalysis.getLastStatement().index + D_CONTEXT_MODIFIED,
                            methodInfo.fullyQualifiedName + D_MODIFIED_METHOD);

                    log(DELAYED, "In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                            methodInfo.distinguishingName());
                    return DELAYS;
                }
                isModified = thisModified == Level.TRUE;
            }
        } // else: already true, so no need to look at this

        if (!isModified) {
            // if there are no modifying method calls, we still may have a modifying method
            // this will be due to calling undeclared SAMs, or calling non-modifying methods in a circular type situation
            // (A.nonModifying() calls B.modifying() on a parameter (NOT a field, so nonModifying is just that) which itself calls A.modifying()
            // NOTE that in this situation we cannot have a container, as we require a modifying! (TODO check this statement is correct)
            Boolean circular = methodLevelData.getCallsPotentiallyCircularMethod();
            if (circular == null) {
                log(DELAYED, "Delaying modification on method {}, waiting for calls to undeclared functional interfaces",
                        methodInfo.distinguishingName());
                return DELAYS;
            }
            if (circular) {
                Boolean haveModifying = findOtherModifyingElements();
                if (haveModifying == null) return DELAYS;
                isModified = haveModifying;
            }
        }
        if (!isModified) {
            OptionalInt maxModified = methodLevelData.copyModificationStatusFrom.stream()
                    .mapToInt(mi -> mi.getKey().methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD)).max();
            if (maxModified.isPresent()) {
                int mm = maxModified.getAsInt();
                if (mm == Level.DELAY) {
                    log(DELAYED, "Delaying modification on method {}, waiting to copy", methodInfo.distinguishingName());
                    return DELAYS;
                }
                isModified = maxModified.getAsInt() == Level.TRUE;
            }
        }
        log(MODIFICATION, "Mark method {} as {}", methodInfo.distinguishingName(),
                isModified ? "@Modified" : "@NotModified");
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(variableProperty, isModified ? Level.TRUE : Level.FALSE);
        return DONE;
    }

    public boolean fieldInMyTypeHierarchy(FieldInfo fieldInfo, TypeInfo typeInfo) {
        if (typeInfo == fieldInfo.owner) return true;
        TypeInspection inspection = analyserContext.getTypeInspection(typeInfo);
        if (!Primitives.isJavaLangObject(inspection.parentClass())) {
            return fieldInMyTypeHierarchy(fieldInfo, inspection.parentClass().typeInfo);
        }
        return typeInfo.primaryType() == fieldInfo.owner.primaryType();
    }

    private Boolean findOtherModifyingElements() {
        boolean nonPrivateFields = myFieldAnalysers.values().stream()
                .filter(fa -> fa.fieldInfo.type.isFunctionalInterface() && fa.fieldAnalysis.isDeclaredFunctionalInterface())
                .anyMatch(fa -> !fa.fieldInfo.isPrivate());
        if (nonPrivateFields) {
            return true;
        }
        // We also check independence (maybe the user calls a method which returns one of the fields,
        // and calls a modification directly)

        Optional<MethodInfo> someOtherMethodNotYetDecided = methodInfo.typeInfo.typeInspection.get()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(mi ->
                        analyserContext.getMethodAnalysis(mi).methodLevelData().getCallsPotentiallyCircularMethod() == null ||
                                (analyserContext.getMethodAnalysis(mi).methodLevelData().getCallsPotentiallyCircularMethod() && (
                                        analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.MODIFIED_METHOD) == Level.DELAY ||
                                                mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(analyserContext, methodInfo.typeInfo) == null ||
                                                analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.INDEPENDENT) == Level.DELAY)))
                .findFirst();
        if (someOtherMethodNotYetDecided.isPresent()) {
            log(DELAYED, "Delaying modification on method {} which calls an undeclared functional interface, because of {}",
                    methodInfo.distinguishingName(), someOtherMethodNotYetDecided.get().name);
            return null;
        }
        return methodInfo.typeInfo.typeInspection.get()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .anyMatch(mi -> analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.MODIFIED_METHOD) == Level.TRUE ||
                        !mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(analyserContext, methodInfo.typeInfo) &&
                                analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.INDEPENDENT) == MultiLevel.DEPENDENT);
    }

    private AnalysisStatus methodIsIndependent(SharedState sharedState) {
        assert methodAnalysis.getProperty(VariableProperty.INDEPENDENT) == Level.DELAY;
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        if (!methodInfo.isConstructor) {
            // we only compute @Independent/@Dependent on methods when the method is @NotModified
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.TRUE) {
                methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.DEPENDENT);
                return DONE;
            }
        } // else: for constructors, we assume @Modified so that rule is not that useful

        if (!methodLevelData.linksHaveBeenEstablished.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, links not yet computed", methodInfo.fullyQualifiedName());
            return DELAYS;
        }

        // PART 1: check the return object, if it is there

        // support data types are not set for types that have not been defined; there, we rely on annotations
        int returnObjectIsIndependent = independenceStatusOfReturnType(methodInfo, methodLevelData);
        if (returnObjectIsIndependent == Level.DELAY) {
            return DELAYS;
        }

        // CONSTRUCTOR ...
        int parametersIndependentOfFields;
        if (methodInfo.isConstructor) {
            // TODO check ExplicitConstructorInvocations

            // PART 2: check parameters, but remove those that are recursively of my own type
            Set<ParameterInfo> parameters = new HashSet<>(methodInfo.methodInspection.get().getParameters());
            parameters.removeIf(pi -> pi.parameterizedType.typeInfo == methodInfo.typeInfo);

            int independentViaLinkedVariables = computeConstructorIndependenceViaLinkedVariables(parameters);
            int independentViaStaticallyAssignedVariables = computeConstructorIndependenceViaStaticallyAssignedVariables(parameters);

            if (independentViaLinkedVariables == MultiLevel.DEPENDENT || independentViaStaticallyAssignedVariables == MultiLevel.DEPENDENT) {
                parametersIndependentOfFields = MultiLevel.DEPENDENT; // do not care about delays on the other one
            } else {
                if (independentViaLinkedVariables == Level.DELAY || independentViaStaticallyAssignedVariables == Level.DELAY) {
                    if (independentViaLinkedVariables == Level.DELAY) {
                        log(DELAYED, "Delaying @Independent on {}, linked variables not yet known for all field references", methodInfo.distinguishingName());
                    }
                    if (independentViaStaticallyAssignedVariables == Level.DELAY) {
                        log(DELAYED, "Delaying @Independent on {}, statically assigned variables not yet fully known for all field references", methodInfo.distinguishingName());
                    }
                    return DELAYS;
                }
                parametersIndependentOfFields = MultiLevel.INDEPENDENT;
            }
        } else {
            parametersIndependentOfFields = MultiLevel.INDEPENDENT;
        }

        // conclusion

        int independent = Math.min(parametersIndependentOfFields, returnObjectIsIndependent);
        methodAnalysis.setProperty(VariableProperty.INDEPENDENT, independent);
        log(INDEPENDENCE, "Mark method/constructor {} @Independent {}",
                methodInfo.fullyQualifiedName(), independent);
        return DONE;
    }

    private int computeConstructorIndependenceViaStaticallyAssignedVariables(Set<ParameterInfo> parameters) {
        Optional<VariableInfo> staticallyAssignedDelayed = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .filter(vi -> vi.getStaticallyAssignedVariables().isDelayed())
                .findFirst();
        if (staticallyAssignedDelayed.isPresent()) {
            return Level.DELAY;
        }

        Optional<VariableInfo> implicitlyImmutableNotSet = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .filter(vi -> delayOnImplicitlyImmutableTypeOfVariable(vi.variable(), analyserContext))
                .findFirst();
        if (implicitlyImmutableNotSet.isPresent()) {
            return Level.DELAY;
        }

        Optional<Variable> immutableNotKnown = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .flatMap(vi -> vi.getStaticallyAssignedVariables().variables().stream())
                .filter(v -> v.parameterizedType().defaultImmutable(analyserContext) == Level.DELAY)
                .findFirst();
        if (immutableNotKnown.isPresent()) {
            return Level.DELAY;
        }

        Optional<ParameterInfo> nonImmutableStaticallyAssignedToParameter =
                methodAnalysis.getLastStatement().variableStream()
                        .filter(vi -> vi.variable() instanceof FieldReference)
                        .filter(vi -> isFieldOfImplicitlyImmutableType(vi.variable(), analyserContext))
                        .flatMap(vi -> vi.getStaticallyAssignedVariables().variables().stream())
                        .filter(v -> v instanceof ParameterInfo)
                        .filter(v -> v.parameterizedType().defaultImmutable(analyserContext) != MultiLevel.EFFECTIVELY_E2IMMUTABLE)
                        .map(v -> (ParameterInfo) v)
                        .filter(parameters::contains)
                        .findFirst();
        return nonImmutableStaticallyAssignedToParameter.isPresent() ? MultiLevel.DEPENDENT : MultiLevel.INDEPENDENT;
    }

    private int computeConstructorIndependenceViaLinkedVariables(Set<ParameterInfo> parameters) {
        boolean allLinkedVariablesSet = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .noneMatch(vi -> vi.getLinkedVariables().isDelayed());
        if (!allLinkedVariablesSet) {
            return Level.DELAY;
        }

        boolean independentViaLinkingToParameter = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .flatMap(vi -> vi.getLinkedVariables().variables().stream())
                .filter(v -> v instanceof ParameterInfo)
                .map(v -> (ParameterInfo) v)
                .noneMatch(parameters::contains);

        return independentViaLinkingToParameter ? MultiLevel.INDEPENDENT : MultiLevel.DEPENDENT;
    }

    private int independenceStatusOfReturnType(MethodInfo methodInfo, MethodLevelData methodLevelData) {
        if (methodInfo.noReturnValue()) return MultiLevel.INDEPENDENT;

        if (!methodLevelData.linksHaveBeenEstablished.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                    methodInfo.fullyQualifiedName());
            return Level.DELAY;
        }
        // method does not return an implicitly immutable data type
        VariableInfo returnVariable = getReturnAsVariable();

        // @Dependent1

        LinkedVariables staticallyAssigned = returnVariable.getStaticallyAssignedVariables();
        boolean implicitlyImmutableDelayed = staticallyAssigned.isDelayed() ||
                staticallyAssigned.variables().stream().anyMatch(v -> delayOnImplicitlyImmutableTypeOfVariable(v, analyserContext));
        if (implicitlyImmutableDelayed) {
            log(DELAYED, "Delaying @Independent on {}, implicitly immutable status not know for assigned field",
                    methodInfo.fullyQualifiedName);
            return Level.DELAY;
        }
        boolean returnValueLinkedToImplicitlyImmutableField = staticallyAssigned.variables().stream()
                .anyMatch(v -> isFieldOfImplicitlyImmutableType(v, analyserContext));
        if (returnValueLinkedToImplicitlyImmutableField) {
            return MultiLevel.DEPENDENT_1;
        }
        int independent = returnVariable.getProperty(VariableProperty.INDEPENDENT);
        if (independent == MultiLevel.DEPENDENT_1 || independent == MultiLevel.DEPENDENT_2) {
            return independent;
        }

        // @Independent

        LinkedVariables linkedVariables = returnVariable.getLinkedVariables();
        if (linkedVariables.isDelayed()) {
            log(DELAYED, "Delaying @Independent on {}, implicitly immutable status not known for all field references",
                    methodInfo.fullyQualifiedName);
            return Level.DELAY;
        }
        int e2ImmutableStatusOfFieldRefs = linkedVariables.variables().stream()
                .filter(v -> v instanceof FieldReference)
                .map(v -> analyserContext.getFieldAnalyser(((FieldReference) v).fieldInfo))
                .mapToInt(fa -> MultiLevel.value(fa.fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE), MultiLevel.E2IMMUTABLE))
                .min().orElse(MultiLevel.EFFECTIVE);
        if (e2ImmutableStatusOfFieldRefs == MultiLevel.DELAY) {
            log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                    linkedVariables.variables().stream()
                            .filter(v -> MultiLevel.value(analyserContext.getFieldAnalyser(((FieldReference) v).fieldInfo)
                                            .fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE),
                                    MultiLevel.E2IMMUTABLE) == MultiLevel.DELAY)
                            .map(Variable::fullyQualifiedName)
                            .collect(Collectors.joining(", ")));
            return Level.DELAY;
        }

        if (e2ImmutableStatusOfFieldRefs >= MultiLevel.EVENTUAL_AFTER) return MultiLevel.INDEPENDENT;
        int immutable = methodAnalysis.getProperty(IMMUTABLE);
        ParameterizedType returnType = methodInfo.returnType();
        boolean myOwnType = returnType.isAssignableFromTo(analyserContext, methodInfo.typeInfo.asParameterizedType(analyserContext));
        if (immutable == MultiLevel.DELAY && !myOwnType) {
            log(DELAYED, "Delaying @Independent of {}, waiting for @Immutable", methodInfo.fullyQualifiedName);
            return Level.DELAY;
        }
        if (immutable >= MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK) {
            log(INDEPENDENCE, "Method {} is independent, formal return type is E2Immutable", methodInfo.distinguishingName());
            return MultiLevel.INDEPENDENT;
        }
        return myOwnType ? MultiLevel.INDEPENDENT : MultiLevel.DEPENDENT;
    }

    public static boolean delayOnImplicitlyImmutableTypeOfVariable(Variable v, AnalysisProvider analysisProvider) {
        return v instanceof FieldReference &&
                analysisProvider.getFieldAnalysis(((FieldReference) v).fieldInfo).isOfImplicitlyImmutableDataType() == null;
    }

    public static boolean isFieldOfImplicitlyImmutableType(Variable variable, AnalysisProvider analysisProvider) {
        if (!(variable instanceof FieldReference)) return false;
        FieldAnalysis fieldAnalysis = analysisProvider.getFieldAnalysis(((FieldReference) variable).fieldInfo);
        return fieldAnalysis.isOfImplicitlyImmutableDataType() == Boolean.TRUE;
    }

    @Override
    public Stream<Message> getMessageStream() {
        return Stream.concat(super.getMessageStream(), getParameterAnalysers().stream().flatMap(ParameterAnalyser::getMessageStream));
    }


    public MethodLevelData methodLevelData() {
        return methodAnalysis.methodLevelData();
    }

    @Override
    public void logAnalysisStatuses() {
        AnalysisStatus statusOfStatementAnalyser = analyserComponents.getStatus(ComputingMethodAnalyser.STATEMENT_ANALYSER);
        if (statusOfStatementAnalyser == AnalysisStatus.DELAYS) {
            StatementAnalyser lastStatement = firstStatementAnalyser.lastStatement();
            AnalyserComponents<String, StatementAnalyser.SharedState> analyserComponentsOfStatement = lastStatement.getAnalyserComponents();
            LOGGER.warn("Analyser components of last statement {} of {}:\n{}", lastStatement.index(),
                    methodInfo.fullyQualifiedName(),
                    analyserComponentsOfStatement.details());
            AnalysisStatus statusOfMethodLevelData = analyserComponentsOfStatement.getStatus(StatementAnalyser.ANALYSE_METHOD_LEVEL_DATA);
            if (statusOfMethodLevelData == AnalysisStatus.DELAYS) {
                AnalyserComponents<String, MethodLevelData.SharedState> analyserComponentsOfMethodLevelData =
                        lastStatement.statementAnalysis.methodLevelData.analyserComponents;
                LOGGER.warn("Analyser components of method level data of last statement {} of {}:\n{}", lastStatement.index(),
                        methodInfo.fullyQualifiedName(),
                        analyserComponentsOfMethodLevelData.details());
            }
        }
    }

    @Override
    public List<VariableInfo> getFieldAsVariableAssigned(FieldInfo fieldInfo) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? List.of() : methodAnalysis.getLastStatement().assignmentInfo(fieldInfo);
    }

    @Override
    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean includeLocalCopies) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? List.of() :
                methodAnalysis.getLastStatement().latestInfoOfVariablesReferringTo(fieldInfo, includeLocalCopies);
    }

    // occurs as often in a flatMap as not, so a stream version is useful
    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? Stream.empty() :
                methodAnalysis.getLastStatement().streamOfLatestInfoOfVariablesReferringTo(fieldInfo, includeLocalCopies);
    }

    public VariableInfo getThisAsVariable() {
        StatementAnalysis last = methodAnalysis.getLastStatement();
        if (last == null) return null;
        return last.getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName + ".this");
    }

    public VariableInfo getReturnAsVariable() {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        assert lastStatement != null; // either constructor, and then we shouldn't ask; or compilation error
        return lastStatement.getLatestVariableInfo(methodInfo.fullyQualifiedName());
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        return firstStatementAnalyser.navigateTo(index);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            super(iteration, conditionManager, closure);
        }

        @Override
        public EvaluationContext child(Expression condition) {
            Set<Variable> conditionIsDelayed = isDelayedSet(condition);
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition, conditionIsDelayed,
                    Precondition.empty(getPrimitives()), null);
            return ComputingMethodAnalyser.this.new EvaluationContextImpl(iteration, cm, closure);
        }

        @Override
        public Stream<DelayDebugNode> streamNodes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            if (variable instanceof FieldReference fieldReference) {
                VariableProperty vp = external(variableProperty);
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                VariableProperty vp = variableProperty == VariableProperty.NOT_NULL_EXPRESSION
                        ? VariableProperty.NOT_NULL_PARAMETER : variableProperty;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            if (variable instanceof This thisVar) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(thisVar.typeInfo);
                return typeAnalysis.getProperty(variableProperty);
            }
            throw new UnsupportedOperationException("Variable: " + variable.fullyQualifiedName() + " type " + variable.getClass());
        }

        private VariableProperty external(VariableProperty variableProperty) {
            if (variableProperty == NOT_NULL_EXPRESSION) return EXTERNAL_NOT_NULL;
            if (variableProperty == IMMUTABLE) return EXTERNAL_IMMUTABLE;
            return variableProperty;
        }

        @Override
        public int getProperty(Expression value,
                               VariableProperty variableProperty,
                               boolean duringEvaluation,
                               boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression ve) {
                return getProperty(ve.variable(), variableProperty);
            }
            return value.getProperty(this, variableProperty, true);
        }

        @Override
        public String newObjectIdentifier() {
            return methodInfo.fullyQualifiedName;
        }
    }
}
