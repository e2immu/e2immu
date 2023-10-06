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

package org.e2immu.analyser.analyser.impl.computing;

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.impl.MethodAnalyserImpl;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.impl.shallow.CompanionAnalyser;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.LocalAnalyserContext;
import org.e2immu.analyser.analyser.statementanalyser.StatementAnalyserImpl;
import org.e2immu.analyser.analyser.util.*;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.*;

public class ComputingMethodAnalyser extends MethodAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputingMethodAnalyser.class);

    public static final String STATEMENT_ANALYSER = "StatementAnalyser";
    public static final String OBTAIN_MOST_COMPLETE_PRECONDITION = "obtainMostCompletePrecondition";
    public static final String COMPUTE_MODIFIED = "computeModified";
    public static final String COMPUTE_MODIFIED_CYCLES = "computeModifiedCycles";
    public static final String COMPUTE_RETURN_VALUE = "computeReturnValue";
    public static final String COMPUTE_IMMUTABLE = "computeImmutable";
    public static final String COMPUTE_CONTAINER = "computeContainer";
    public static final String DETECT_MISSING_STATIC_MODIFIER = "detectMissingStaticModifier";
    public static final String EVENTUAL_PREP_WORK = "eventualPrepWork";
    public static final String ANNOTATE_EVENTUAL = "annotateEventual";
    public static final String COMPUTE_INDEPENDENT = "methodIsIndependent";
    public static final String SET_POST_CONDITION = "setPostCondition";
    public static final String COMPUTE_SSE = "computeStaticSideEffects";

    private final TypeAnalysis typeAnalysis;
    public final StatementAnalyserImpl firstStatementAnalyser;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final Set<PrimaryTypeAnalyser> locallyCreatedPrimaryTypeAnalysers = new HashSet<>();

    private Map<FieldInfo, FieldAnalyser> myFieldAnalysers;

    /*
    Note that MethodLevelData is not part of the shared state, as the "lastStatement", where it resides,
    is only computed in the first step of the analyser components.
     */
    private record SharedState(BreakDelayLevel breakDelayLevel, EvaluationContext evaluationContext) {
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
        if (block.isEmpty()) {
            firstStatementAnalyser = null;
        } else {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            boolean inSyncBlock = methodInspection.isSynchronized()
                    || methodInfo.inConstruction()
                    || methodInspection.isStaticBlock();
            firstStatementAnalyser = StatementAnalyserImpl.recursivelyCreateAnalysisObjects(analyserContext,
                    this, null, block.structure.statements(), "", true, inSyncBlock);
            methodAnalysis.setFirstStatement(firstStatementAnalyser.statementAnalysis);
        }

        AnalyserComponents.Builder<String, SharedState> builder = new AnalyserComponents.Builder<>();
        builder.add("mark first iteration", this::markFirstIteration);
        assert firstStatementAnalyser != null;

        // order: Companion analyser, Parameter analysers, Statement analysers, Method analyser parts
        // rest of the order (as determined in PrimaryTypeAnalyser): fields, types

        for (CompanionAnalyser companionAnalyser : companionAnalysers.values()) {
            builder.add(companionAnalyser.companionMethodName.toString(),
                    (sharedState -> companionAnalyser.analyse(sharedState.evaluationContext.getIteration())));
        }

        for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
            AnalysisStatus.AnalysisResultSupplier<SharedState> parameterAnalyserAction = (sharedState) -> {
                Analyser.SharedState state = new Analyser.SharedState(sharedState.evaluationContext.getIteration(),
                        sharedState.breakDelayLevel(), null);
                AnalyserResult result = parameterAnalyser.analyse(state);
                analyserResultBuilder.add(result);
                return result.analysisStatus();
            };

            builder.add("Parameter " + parameterAnalyser.getParameterInfo().name, parameterAnalyserAction);
        }

        AnalysisStatus.AnalysisResultSupplier<SharedState> statementAnalyser = (sharedState) -> {
            ForwardAnalysisInfo fwd = ForwardAnalysisInfo.startOfMethod(analyserContext.getPrimitives(),
                    sharedState.breakDelayLevel);
            AnalyserResult result = firstStatementAnalyser.analyseAllStatementsInBlock(sharedState
                    .evaluationContext.getIteration(), 0, fwd, sharedState.evaluationContext.getClosure());
            analyserResultBuilder.add(result, false, false, true);
            this.locallyCreatedPrimaryTypeAnalysers.addAll(result.localAnalysers());
            return result.analysisStatus();
        };

        builder.add(STATEMENT_ANALYSER, statementAnalyser)
                .add(COMPUTE_MODIFIED, this::computeModified)
                .add(COMPUTE_MODIFIED_CYCLES, (sharedState -> methodInfo.isConstructor ? DONE : computeModifiedInternalCycles()))
                .add(OBTAIN_MOST_COMPLETE_PRECONDITION, (sharedState) -> obtainMostCompletePrecondition())
                .add(SET_POST_CONDITION, (sharedState -> setPostCondition()))
                .add(COMPUTE_RETURN_VALUE, (sharedState) -> methodInfo.noReturnValue()
                        ? computeSetter(false) : computeReturnValue(sharedState))
                .add(COMPUTE_IMMUTABLE, sharedState -> methodInfo.noReturnValue() ? DONE : computeImmutable(sharedState))
                .add(COMPUTE_CONTAINER, sharedState -> methodInfo.noReturnValue() ? DONE : computeContainer(sharedState))
                .add(COMPUTE_SSE, this::computeStaticSideEffects)
                .add(DETECT_MISSING_STATIC_MODIFIER, (iteration) -> methodInfo.isConstructor ? DONE : detectMissingStaticModifier())
                .add(EVENTUAL_PREP_WORK, this::eventualPrepWork)
                .add(ANNOTATE_EVENTUAL, this::annotateEventual)
                .add(COMPUTE_INDEPENDENT, this::analyseIndependent);

        analyserComponents = builder.setLimitCausesOfDelay(true).build();
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "CMA " + methodInfo.fullyQualifiedName;
    }

    private AnalysisStatus markFirstIteration(SharedState sharedState) {
        methodAnalysis.markFirstIteration();
        return DONE;
    }

    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        LocalAnalyserContext expandable = (LocalAnalyserContext) analyserContext;
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
            if (analyser.getFieldInfo().owner == methodInfo.typeInfo) {
                myFieldAnalysers.put(analyser.getFieldInfo(), analyser);
            }
        });
        this.myFieldAnalysers = Map.copyOf(myFieldAnalysers);
    }

    // called from primary type analyser
    @Override
    public AnalyserResult analyse(Analyser.SharedState sharedState) {
        assert !isUnreachable();
        int iteration = sharedState.iteration();

        LOGGER.info("Analysing method {} it {}", methodInfo, iteration);
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, sharedState.breakDelayLevel(),
                ConditionManagerImpl.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure());
        SharedState state = new SharedState(sharedState.breakDelayLevel(), evaluationContext);

        try {
            AnalysisStatus analysisStatus = analyserComponents.run(state);
            if (analysisStatus.isDone()) methodAnalysis.internalAllDoneCheck();
            analyserResultBuilder.setAnalysisStatus(analysisStatus);

            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                    .debugConfiguration().afterMethodAnalyserVisitors();
            if (!visitors.isEmpty()) {
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                            sharedState.breakDelayLevel(),
                            evaluationContext, methodInfo, methodAnalysis,
                            parameterAnalyses, analyserComponents.getStatusesAsMap(),
                            analyserResultBuilder::getMessageStream));
                }
            }
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo);
            throw rte;
        }
    }

    @Override
    public void makeImmutable() {
        super.makeImmutable();
        if (firstStatementAnalyser != null) {
            firstStatementAnalyser.makeImmutable();
        }
    }

    @Override
    public boolean makeUnreachable() {
        if (super.makeUnreachable()) {
            if (firstStatementAnalyser != null) {
                firstStatementAnalyser.makeUnreachable();
            }
            methodAnalysis.setPropertyIfAbsentOrDelayed(TEMP_MODIFIED_METHOD, DV.FALSE_DV);
            methodAnalysis.setPropertyIfAbsentOrDelayed(MODIFIED_METHOD, DV.FALSE_DV);
            boolean isCycle = methodInfo.methodResolution.get().partOfCallCycle();
            if (isCycle) {
                // see code in computeModifiedInternalCycles()
                Set<MethodInfo> cycle = methodInfo.methodResolution.get().callCycle();
                TypeAnalysisImpl.Builder ptBuilder = methodInfo.typeInfo.isPrimaryType() ? (TypeAnalysisImpl.Builder) typeAnalysis
                        : (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(methodInfo.typeInfo.primaryType());
                TypeAnalysisImpl.CycleInfo cycleInfo = ptBuilder.nonModifiedCountForMethodCallCycle.get(cycle);
                if (cycleInfo != null && !cycleInfo.nonModified.contains(methodInfo)) {
                    cycleInfo.nonModified.add(methodInfo);
                }
            }
            return true;
        }
        return false;
    }

    private AnalysisStatus setPostCondition() {
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();
        Set<PostCondition> pcs = methodLevelData.getPostConditions();
        if (methodLevelData.arePostConditionsDelayed()) {
            CausesOfDelay delays = pcs.stream().map(pc -> pc.expression().causesOfDelay())
                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
            methodAnalysis.setPostConditionDelays(delays);
            return delays;
        }
        methodAnalysis.setFinalPostConditions(pcs);
        methodAnalysis.setIndicesOfEscapeNotInPreOrPostCondition(methodLevelData
                .getIndicesOfEscapesNotInPreOrPostConditions());

        return DONE;
    }

    private static final String NOT_RAISING = "Not raising the 'method should be marked static' error ";

    private AnalysisStatus detectMissingStaticModifier() {
        if (!methodInfo.methodInspection.get().isStatic()
                && !methodInfo.typeInfo.isInterface()
                && methodInfo.isNotATestMethod()) {
            // we need to check if there's fields being read/assigned/
            if (absentUnlessStatic(VariableInfo::isRead)
                    && absentUnlessStatic(VariableInfo::isAssigned)
                    && !getThisAsVariable().isRead()
                    && methodInfo.isNotOverridingAnyOtherMethod()
                    && !methodInfo.methodInspection.get().isDefault()) {
                analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(),
                        Message.Label.METHOD_SHOULD_BE_MARKED_STATIC));
                LOGGER.info("Method should be marked 'static': {}", methodInfo);
                return DONE;
            }
            LOGGER.debug(NOT_RAISING + "(read, assigned): {}", methodInfo);
        } else {
            LOGGER.debug(NOT_RAISING + "(static, interface, test): {}", methodInfo);
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
        assert methodAnalysis.preconditionIsVariable();

        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();
        Precondition pc = methodLevelData.combinedPreconditionGet();
        if (!methodLevelData.combinedPreconditionIsFinal()) {
            methodAnalysis.preconditionSetVariable(pc);
            return pc.expression().causesOfDelay();
        }
        assert Identifier.isListOfPositionalIdentifiers(pc.expression());

        methodAnalysis.preconditionSetFinal(pc);
        return DONE;
    }

    private AnalysisStatus annotateEventual(SharedState sharedState) {
        if (methodAnalysis.eventualIsSet()) return DONE;
        if (methodInfo.isConstructor) {
            // don't write to annotations
            methodAnalysis.setEventual(MethodAnalysis.NOT_EVENTUAL);
            return DONE;
        }

        DetectEventual detectEventual = new DetectEventual(methodInfo, methodAnalysis, typeAnalysis, analyserContext);
        MethodAnalysis.Eventual eventual = detectEventual.detect(EvaluationResultImpl.from(sharedState.evaluationContext));
        if (eventual.causesOfDelay().isDelayed()) {
            methodAnalysis.setEventualDelay(eventual);
            return eventual.causesOfDelay();
        }
        methodAnalysis.setEventual(eventual);
        if (eventual == MethodAnalysis.NOT_EVENTUAL) {
            return DONE;
        }

        LOGGER.debug("Marking {} with only data {}", methodInfo, eventual);
        AnnotationExpression annotation = detectEventual.makeAnnotation(eventual);
        methodAnalysis.addAnnotation(annotation);
        return DONE;
    }

    private AnalysisStatus eventualPrepWork(SharedState sharedState) {
        if (!methodAnalysis.getPreconditionForEventual().isDelayed()) {
            return DONE;
        }
        if (methodInfo.isConstructor) {
            methodAnalysis.setPreconditionForEventual(Precondition.empty(methodAnalysis.primitives));
            return DONE;
        }
        Primitives primitives = methodAnalysis.primitives;

        TypeInfo typeInfo = methodInfo.typeInfo;
        List<FieldAnalysis> fieldAnalysesOfTypeInfo = myFieldAnalysers.values().stream()
                .map(FieldAnalyser::getFieldAnalysis).toList();

        while (true) {
            // FIRST CRITERION: is there a non-@Final field?

            DV finalOverFields = fieldAnalysesOfTypeInfo.stream()
                    .map(fa -> fa.getProperty(Property.FINAL))
                    .reduce(Property.FINAL.bestDv, DV::min);
            if (finalOverFields.isDelayed()) {
                LOGGER.debug("Delaying eventual in {} until we know about @Final of fields", methodInfo);
                methodAnalysis.setPreconditionForEventual(Precondition.forDelayed(methodInfo.identifier,
                        EmptyExpression.EMPTY_EXPRESSION, finalOverFields.causesOfDelay(), primitives));
                return finalOverFields.causesOfDelay();
            }
            if (finalOverFields.valueIsFalse()) {
                // OK!
                break;
            }

            // SECOND CRITERION: is there an eventually immutable field? We can ignore delays if the concrete type of the field
            // is the same as the owner

            DV haveEventuallyImmutableFields = fieldAnalysesOfTypeInfo
                    .stream()
                    .map(fa -> {
                        ParameterizedType concreteType = fa.concreteTypeNullWhenDelayed();
                        boolean acceptDelay = concreteType == null || concreteType.typeInfo != null &&
                                concreteType.typeInfo.topOfInterdependentClassHierarchy() !=
                                        fa.getFieldInfo().owner.topOfInterdependentClassHierarchy();
                        DV immutable = fa.getProperty(Property.EXTERNAL_IMMUTABLE);
                        return acceptDelay && immutable.isDelayed() ? immutable :
                                DV.fromBoolDv(MultiLevel.isEventuallyFinalFields(immutable)
                                        || MultiLevel.isEventuallyImmutableHC(immutable));
                    })
                    .reduce(DV.TRUE_DV, DV::min);
            if (haveEventuallyImmutableFields.isDelayed()) {
                LOGGER.debug("Delaying eventual in {} until we know about @Immutable of fields", methodInfo);
                methodAnalysis.setPreconditionForEventual(Precondition.forDelayed(methodInfo.identifier,
                        EmptyExpression.EMPTY_EXPRESSION, haveEventuallyImmutableFields.causesOfDelay(), primitives));
                return haveEventuallyImmutableFields.causesOfDelay();
            }
            if (haveEventuallyImmutableFields.valueIsTrue()) {
                break;
            }

            // THIRD CRITERION: is there a field whose content can change? Not immutable

            DV haveContentChangeableField = fieldAnalysesOfTypeInfo.stream()
                    .map(fa -> {
                        DV immutable = fa.getProperty(Property.EXTERNAL_IMMUTABLE);

                        boolean contentChangeable = !MultiLevel.isAtLeastEventuallyImmutableHC(immutable)
                                && !fa.getFieldInfo().type.isPrimitiveExcludingVoid();
                        return DV.fromBoolDv(contentChangeable);
                    })
                    .reduce(DV.FALSE_DV, DV::max);
            if (haveContentChangeableField.isDelayed()) {
                LOGGER.debug("Delaying eventual in {} until we know about transparent types of fields", methodInfo);
                methodAnalysis.setPreconditionForEventual(Precondition.forDelayed(methodInfo.identifier,
                        EmptyExpression.EMPTY_EXPRESSION, haveContentChangeableField.causesOfDelay(), primitives));
                return haveContentChangeableField.causesOfDelay();
            }
            if (haveContentChangeableField.valueIsTrue()) {
                break;
            }

            // GO UP TO PARENT

            ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass();
            if (parentClass.isJavaLangObject()) {
                LOGGER.debug("No eventual annotation in {}: found no non-final fields", methodInfo);
                methodAnalysis.setPreconditionForEventual(Precondition.empty(methodAnalysis.primitives));
                return DONE;
            }
            typeInfo = parentClass.bestTypeInfo();
            TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
            fieldAnalysesOfTypeInfo = typeInspection.fields().stream().map(analyserContext::getFieldAnalysis).toList();
        }

        if (methodAnalysis.preconditionIsVariable()) {
            assert methodAnalysis.preconditionStatus().isDelayed();
            if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                LOGGER.debug("Method override: no precondition for eventual");
                methodAnalysis.setPreconditionForEventual(Precondition.empty(primitives));
                return DONE;
            }
            LOGGER.debug("Delaying compute @Only and @Mark, precondition not set (weird, should be set by now)");
            methodAnalysis.setPreconditionForEventual(methodAnalysis.preconditionGet());
            return methodAnalysis.preconditionGet().causesOfDelay();
        }
        Precondition precondition = methodAnalysis.preconditionGet();
        EvaluationResult context = EvaluationResultImpl.from(sharedState.evaluationContext);

        // situation of Lazy
        if (precondition.isEmpty()) {
            return preconditionAsInLazy(primitives, context);
        }

        // situation of ensureNotFrozen/ensureFrozen in Freezable, as used in a.o. Trie
        Precondition.CompanionCause cc = precondition.singleCompanionCauseOrNull();
        if (cc != null) {
            Precondition.MethodCallAndNegation mc = precondition.expressionIsPossiblyNegatedMethodCall();
            if (mc != null) {
                MethodAnalysis analysis = analyserContext.getMethodAnalysis(mc.methodCall().methodInfo);
                if (analysis.getEventual() != MethodAnalysis.NOT_EVENTUAL) {
                    LOGGER.debug("Precondition for eventual copied from precondition: companion cause: {}", methodInfo);
                    methodAnalysis.setPreconditionForEventual(precondition);
                    return DONE;
                }
            }
        }

        /*
        FilterMode.ALL will find clauses in Or and in And constructs. See SetOnce.copy for an example of an Or
        construct.
         */
        Filter filter = new Filter(context, Filter.FilterMode.ALL);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(precondition.expression(),
                filter.individualFieldClause(analyserContext));
        if (filterResult.accepted().isEmpty()) {
            LOGGER.debug("No @Mark/@Only annotation in {}: found no individual field preconditions",
                    methodInfo);
            methodAnalysis.setPreconditionForEventual(Precondition.empty(methodAnalysis.primitives));
            return DONE;
        }
        Expression[] preconditionExpressions = filterResult.accepted().values().toArray(Expression[]::new);
        LOGGER.debug("Did prep work for @Only, @Mark, found precondition {} on variables {} in {}", precondition,
                filterResult.accepted().keySet(), methodInfo);

        Expression and = And.and(context, preconditionExpressions);
        methodAnalysis.setPreconditionForEventual(new Precondition(and, precondition.causes()));
        return DONE;
    }

    private AnalysisStatus preconditionAsInLazy(Primitives primitives, EvaluationResult context) {
        // code to detect the situation as in Lazy
        Precondition combinedPrecondition = Precondition.empty(methodAnalysis.primitives);
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers.values()) {
            if (fieldAnalyser.getFieldAnalysis().getProperty(Property.FINAL).valueIsFalse()) {
                FieldReference fr = new FieldReference(analyserContext, fieldAnalyser.getFieldInfo());
                StatementAnalysis beforeAssignment = statementBeforeAssignment(fr);
                if (beforeAssignment != null) {
                    ConditionManager cm = beforeAssignment.stateData().getConditionManagerForNextStatement();
                    if (cm.state().isDelayed()) {
                        LOGGER.debug("Delaying compute @Only, @Mark, delay in state {} {}", beforeAssignment.index(),
                                methodInfo);
                        methodAnalysis.setPreconditionForEventual(Precondition.forDelayed(methodInfo.identifier,
                                cm.state(), cm.state().causesOfDelay(), primitives));
                        return cm.state().causesOfDelay();
                    }
                    Expression state = cm.state();
                    if (!state.isBoolValueTrue()) {
                        Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
                        Filter.FilterResult<FieldReference> filterResult = filter.filter(state,
                                filter.individualFieldClause(analyserContext, true));
                        Expression inResult = filterResult.accepted().get(fr);
                        if (inResult != null) {
                            Precondition pc = new Precondition(inResult, List.of(new Precondition.StateCause()));
                            combinedPrecondition = combinedPrecondition.combine(context, pc);
                        }
                    }
                }
            }
        }
        LOGGER.debug("No @Mark @Only annotation in {} from precondition, found {} from assignment",
                methodInfo, combinedPrecondition);
        methodAnalysis.setPreconditionForEventual(combinedPrecondition);
        return DONE;
    }

    private static final Pattern INDEX_PATTERN = Pattern.compile("(\\d+)(-C|-E|:M)");

    private StatementAnalysis statementBeforeAssignment(FieldReference fieldReference) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        VariableInfo vi = lastStatement.findOrNull(fieldReference, Stage.MERGE);
        if (vi != null && vi.isAssigned()) {
            Matcher m = INDEX_PATTERN.matcher(vi.getAssignmentIds().getLatestAssignment());
            if (m.matches()) {
                int index = Integer.parseInt(m.group(1)) - 1;
                if (index >= 0) {
                    String id = "" + index; // TODO numeric padding to same length
                    return findStatementAnalyser(id).getStatementAnalysis();
                }
            }
        }
        return null;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private AnalysisStatus computeReturnValue(SharedState sharedState) {
        assert methodAnalysis.singleReturnValueIsVariable();

        // some immediate short-cuts.
        // if we cannot cast 'this' to the current type or the other way round, the method cannot be fluent
        // see Fluent_0 for one way, and Store_7 for the other direction
        ParameterizedType myType = methodInfo.typeInfo.asParameterizedType(analyserContext);
        if (myType.isNotAssignableFromTo(analyserContext, methodInspection.getReturnType()) || methodInspection.isStatic()) {
            methodAnalysis.setProperty(Property.FLUENT, DV.FALSE_DV);
        }

        /*
        down-or-upcast allowed
         */
        if (methodInspection.getParameters().isEmpty() ||
                methodInspection.getReturnType().isNotAssignableFromTo(analyserContext,
                        methodInspection.getParameters().get(0).parameterizedType)) {
            methodAnalysis.setProperty(Property.IDENTITY, DV.FALSE_DV);
        }

        VariableInfo variableInfo = getReturnAsVariable();
        Expression value = variableInfo.getValue().unwrapIfConstant();

        if (methodAnalysis.getSetFieldIsNotYetSet()) {
            FieldReference getter = isGetter();
            if (getter != null) {
                assert value.isDelayed() || value instanceof VariableExpression ve && ve.variable().equals(getter);
                methodAnalysis.setGetSetField(getter.fieldInfo);
            }
            // TODO in the same PrimaryType, switch to field. Outside, switch to getter
        }

        ParameterizedType concreteReturnType = value.isInstanceOf(NullConstant.class) ? methodInfo.returnType() : value.returnType();
        DV notNullExpression = variableInfo.getProperty(NOT_NULL_EXPRESSION);
        if (value.isDelayed() || value.isInitialReturnExpression()) {
            return delayedOrUnreachableMethodResult(sharedState, variableInfo, value, concreteReturnType);
        }

        /* we already have a value for the value property NNE. We wait, however, until we have a value for ENN as well
        if we take the non-constructing methods along for NNE computation, and the value is a variable (only variables
        can have an ENN value)
         */
        DV externalNotNull;
        if ((analyserContext.getConfiguration().analyserConfiguration().computeContextPropertiesOverAllMethods() ||
                methodInfo.inConstruction()) && value.isInstanceOf(VariableExpression.class)) {
            externalNotNull = variableInfo.getProperty(EXTERNAL_NOT_NULL);
            if (externalNotNull.isDelayed()) {
                LOGGER.debug("Delaying return value of {}, waiting for NOT_NULL", methodInfo);
                return delayedSrv(concreteReturnType, value, externalNotNull.causesOfDelay(), false);
            }
        } else {
            externalNotNull = MultiLevel.NOT_INVOLVED_DV;
        }
        DV notNull = notNullExpression.max(externalNotNull);
        if (!methodAnalysis.properties.isDone(NOT_NULL_EXPRESSION)) {
            methodAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, notNull);
        }

        boolean valueIsConstantField;
        VariableExpression ve;
        if (value instanceof InlinedMethod inlined
                && (ve = inlined.expression().asInstanceOf(VariableExpression.class)) != null
                && ve.variable() instanceof FieldReference fieldReference) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            DV constantField = fieldAnalysis.getProperty(Property.CONSTANT);
            if (constantField.isDelayed()) {
                LOGGER.debug("Delaying return value of {}, waiting for effectively final value's @Constant designation",
                        methodInfo);
                return delayedSrv(concreteReturnType, value, constantField.causesOfDelay(), false);
            }
            valueIsConstantField = constantField.valueIsTrue();
        } else valueIsConstantField = false;

        boolean isConstant = value.isConstant() || valueIsConstantField;

        methodAnalysis.setSingleReturnValue(value);
        methodAnalysis.setProperty(Property.CONSTANT, DV.fromBoolDv(isConstant));

        LOGGER.debug("Mark method {} as @Constant? {}", methodInfo, isConstant);


        if (setFluent(value)) {
            computeSetter(true);
        }

        DV currentIdentity = methodAnalysis.getProperty(IDENTITY);
        if (currentIdentity.isDelayed()) {
            DV identity = variableInfo.getProperty(IDENTITY);
            methodAnalysis.setProperty(IDENTITY, identity);
        }

        DV currentIgnoreMods = methodAnalysis.getProperty(IGNORE_MODIFICATIONS);
        if (currentIgnoreMods.isDelayed()) {
            DV ignoreMods = variableInfo.getProperty(IGNORE_MODIFICATIONS).maxIgnoreDelay(IGNORE_MODIFICATIONS.falseDv);
            methodAnalysis.setProperty(IGNORE_MODIFICATIONS, ignoreMods);
        }

        return DONE;
    }

    private AnalysisStatus delayedOrUnreachableMethodResult(SharedState sharedState,
                                                            VariableInfo variableInfo,
                                                            Expression value,
                                                            ParameterizedType concreteReturnType) {
        // it is possible that none of the return statements are reachable... in which case there should be no delay,
        // and no SRV
        if (noReturnStatementReachable()) {
            UnknownExpression ue = UnknownExpression.forNoReturnValue(methodInfo.identifier, methodInfo.returnType());
            methodAnalysis.setSingleReturnValue(ue);
            methodAnalysis.setProperty(Property.IDENTITY, IDENTITY.falseDv);
            methodAnalysis.setProperty(IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv);
            methodAnalysis.setProperty(Property.FLUENT, DV.FALSE_DV);
            methodAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            methodAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV);
            methodAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            methodAnalysis.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
            methodAnalysis.setProperty(CONSTANT, DV.FALSE_DV);
            return DONE;
        }
        LOGGER.debug("It {} method {} has return value {}, delaying", sharedState.evaluationContext.getIteration(),
                methodInfo, value.minimalOutput());
        if (value.isDelayed()) {
            if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                LOGGER.debug("Method override: set rescue value for {}", methodInfo);
                Properties valueProps = Properties.of(Map.of(
                        IDENTITY, methodAnalysis.getPropertyFromMapNeverDelay(IDENTITY),
                        IGNORE_MODIFICATIONS, methodAnalysis.getPropertyFromMapNeverDelay(IGNORE_MODIFICATIONS),
                        NOT_NULL_EXPRESSION, methodAnalysis.getPropertyFromMapNeverDelay(NOT_NULL_EXPRESSION),
                        IMMUTABLE, methodAnalysis.getPropertyFromMapNeverDelay(IMMUTABLE),
                        INDEPENDENT, methodAnalysis.getPropertyFromMapNeverDelay(INDEPENDENT),
                        CONTAINER, methodAnalysis.getPropertyFromMapNeverDelay(CONTAINER)
                ));
                Expression instance = Instance.forMethodResult(methodInfo.getIdentifier(), methodInfo.returnType(), valueProps);
                methodAnalysis.setSingleReturnValue(instance);
                methodAnalysis.setProperty(Property.IDENTITY, valueProps.get(IDENTITY));
                methodAnalysis.setProperty(IGNORE_MODIFICATIONS, valueProps.get(IGNORE_MODIFICATIONS));
                methodAnalysis.setProperty(Property.FLUENT, DV.FALSE_DV);
                methodAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, valueProps.get(NOT_NULL_EXPRESSION));
                methodAnalysis.setProperty(Property.IMMUTABLE, valueProps.get(IMMUTABLE));
                /*
                 INDEPENDENT of a method is not necessarily determined by the return type's INDEPENDENT
                 */
                methodAnalysis.setProperty(Property.CONTAINER, valueProps.get(CONTAINER));
                methodAnalysis.setProperty(CONSTANT, DV.FALSE_DV);
                return DONE;
            }
            return delayedSrv(concreteReturnType, value, variableInfo.getValue().causesOfDelay(), true);
        }
        throw new UnsupportedOperationException("? no delays, and initial return expression even though return statements are reachable");
    }

    private FieldReference isGetter() {
        Block block = methodInspection.getMethodBody();
        if (block != null
                && block.structure.statements().size() == 1
                && block.structure.statements().get(0) instanceof ReturnStatement rs
                && rs.expression instanceof VariableExpression ve
                && ve.variable() instanceof FieldReference fr && fr.scopeIsThis()) {
            return fr;
        }
        return null;
    }

    private AnalysisStatus computeSetter(boolean isFluent) {
        if (methodInspection.getParameters().size() == 1) {
            List<Statement> statements = methodInspection.getMethodBody().structure.statements();
            int n = statements.size();
            if (n == 1 || isFluent && n == 2) {
                if (statements.get(0) instanceof ExpressionAsStatement eas
                        && eas.expression instanceof Assignment assignment) {
                    ParameterInfo pi = methodInspection.getParameters().get(0);
                    if (assignment.variableTarget instanceof FieldReference fr && fr.scopeIsThis()
                            && assignment.value instanceof VariableExpression ve
                            && pi.equals(ve.variable())) {
                        methodAnalysis.setGetSetField(fr.fieldInfo);
                    }
                }
            }
        }
        return DONE;
    }

    private boolean setFluent(Expression valueBeforeInlining) {
        VariableExpression vv;
        boolean isFluent = (vv = valueBeforeInlining.asInstanceOf(VariableExpression.class)) != null &&
                vv.variable() instanceof This thisVar &&
                thisVar.typeInfo == methodInfo.typeInfo;
        methodAnalysis.setProperty(Property.FLUENT, DV.fromBoolDv(isFluent));
        LOGGER.debug("Mark method {} as @Fluent? {}", methodInfo, isFluent);
        return isFluent;
    }

    private AnalysisStatus computeImmutable(SharedState sharedState) {
        if (methodAnalysis.getPropertyFromMapDelayWhenAbsent(IMMUTABLE).isDone()) return DONE;
        DV immutable = computeImmutableValue(sharedState.breakDelayLevel());
        methodAnalysis.setProperty(IMMUTABLE, immutable);
        if (immutable.isDelayed()) {
            if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                methodAnalysis.setProperty(IMMUTABLE, IMMUTABLE.falseDv);
                return DONE;
            }
            return immutable.causesOfDelay();
        }
        LOGGER.debug("Set @Immutable to {} on {}", immutable, methodInfo);
        return DONE;
    }

    private AnalysisStatus computeContainer(SharedState sharedState) {
        if (methodAnalysis.getPropertyFromMapDelayWhenAbsent(CONTAINER).isDone()) return DONE;
        DV container = computeContainerValue(sharedState.breakDelayLevel());
        if (container.isDelayed()) {
            if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                methodAnalysis.setProperty(CONTAINER, CONTAINER.falseDv);
                return DONE;
            }
            return container.causesOfDelay();
        }
        methodAnalysis.setProperty(CONTAINER, container);
        LOGGER.debug("Set @Container to {} on {}", container, methodInfo);
        return DONE;
    }

    private DV computeContainerValue(BreakDelayLevel breakDelayLevel) {
        DV formal = analyserContext.safeContainer(methodInfo.returnType());
        if (MultiLevel.CONTAINER_DV.equals(formal)) {
            return formal;
        }
        Expression expression = methodAnalysis.getSingleReturnValue();
        if (expression.isDelayed()) {
            LOGGER.debug("Delaying @Container on {} until return value is set", methodInfo);
            return methodInfo.delay(CauseOfDelay.Cause.VALUE).merge(expression.causesOfDelay());
        }
        if (expression.isConstant()) {
            return MultiLevel.CONTAINER_DV;
        }
        VariableInfo variableInfo = getReturnAsVariable();
        DV dynamic;
        if (variableInfo.valueIsSet() && variableInfo.getValue().returnType().typeInfo == methodInfo.typeInfo) {
            // read directly from type analyser
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(methodInfo.typeInfo);
            dynamic = typeAnalysis.getProperty(CONTAINER);
        } else {
            dynamic = variableInfo.getProperty(CONTAINER);
        }
        DV dynamicExt = variableInfo.getProperty(CONTAINER_RESTRICTION);
        return dynamic.max(dynamicExt);
    }

    private DV computeImmutableValue(BreakDelayLevel breakDelayLevel) {
        DV formalImmutable = analyserContext.typeImmutable(methodInfo.returnType());
        if (formalImmutable.equals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV)) {
            return formalImmutable;
        }

        Expression expression = methodAnalysis.getSingleReturnValue();
        if (expression.isDelayed()) {
            if (breakDelayLevel.acceptMethod()) {
                /*
                this break avoids a type delay in Fluent_1
                 */
                LOGGER.debug("Breaking @Immutable delay on {}", methodInfo);
            } else {
                LOGGER.debug("Delaying @Immutable on {} until return value is set", methodInfo);
                return methodInfo.delay(CauseOfDelay.Cause.VALUE).merge(expression.causesOfDelay());
            }
        }
        if (expression.isConstant()) {
            return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        }
        VariableInfo variableInfo = getReturnAsVariable();

        DV dynamic = variableInfo.getProperty(IMMUTABLE);
        // NOTE: we ignore variableInfo.getProperty(EXTERNAL_IMMUTABLE); this causes unnecessary delays and is
        // conceptually unnecessary

        // important! there is no such thing as a context immutable property on the return variable
        // we must take it from a variable
        DV dynamicCtx;
        IsVariableExpression ive;
        if ((ive = variableInfo.getValue().asInstanceOf(IsVariableExpression.class)) != null) {
            dynamicCtx = getVariableInfo(ive.variable()).getProperty(CONTEXT_IMMUTABLE);
        } else {
            dynamicCtx = MultiLevel.MUTABLE_DV;
        }
        return formalImmutable.max(dynamic).max(dynamicCtx);
    }


    private VariableInfo getVariableInfo(Variable variable) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement(true);
        assert lastStatement != null;
        return lastStatement.getLatestVariableInfo(variable.fullyQualifiedName());
    }

    /*
    Create an inlined method based on the returned value
     */
    private Expression createInlinedMethod(Expression value) {
        assert value.isDone();
        if (value.getComplexity() > Expression.COMPLEXITY_LIMIT_OF_INLINED_METHOD) {
            VariableInfo variableInfo = getReturnAsVariable();
            Properties properties = variableInfo.valueProperties();
            return Instance.forTooComplex(value.getIdentifier(), value.returnType(), properties);
        }
        return InlinedMethod.of(methodInfo, value, analyserContext);
    }


    private boolean noReturnStatementReachable() {
        StatementAnalysis firstStatement = methodAnalysis.getFirstStatement();
        return !recursivelyFindReachableReturnStatement(firstStatement);
    }

    private static boolean recursivelyFindReachableReturnStatement(StatementAnalysis statementAnalysis) {
        StatementAnalysis sa = statementAnalysis;
        while (!sa.flowData().isUnreachable()) {
            if (sa.statement() instanceof ReturnStatement) return true;
            for (Optional<StatementAnalysis> first : sa.navigationData().blocks.get()) {
                if (first.isPresent() && recursivelyFindReachableReturnStatement(first.get())) {
                    return true;
                }
            }
            if (sa.navigationData().next.get().isEmpty()) break;
            sa = sa.navigationData().next.get().get();
        }
        return false;
    }

    /*
    Compute modified "external" cycles not really needed, since the analyser handles each primary type separately.
    So they will be executed in a particular order anyway.
    This method deals with cyclic calls within the primary type, which the analyser can handle together.

    The MethodCall expression has separate logic to not change the CNN of the object for cyclic and recursive calls.
    Similar code exists in EvaluateParameters.

    See also code in makeUnreachable, for what to do in case one of the methods in the cycle becomes unreachable.
     */
    private AnalysisStatus computeModifiedInternalCycles() {
        boolean isCycle = methodInfo.methodResolution.get().partOfCallCycle();
        if (!isCycle) return DONE;

        DV modified = methodAnalysis.getProperty(Property.TEMP_MODIFIED_METHOD);
        TypeAnalysisImpl.Builder ptBuilder = methodInfo.typeInfo.isPrimaryType() ? (TypeAnalysisImpl.Builder) typeAnalysis
                : (TypeAnalysisImpl.Builder) analyserContext.getTypeAnalysis(methodInfo.typeInfo.primaryType());
        Set<MethodInfo> cycle = methodInfo.methodResolution.get().callCycle();
        TypeAnalysisImpl.CycleInfo cycleInfo = ptBuilder.nonModifiedCountForMethodCallCycle.getOrCreate(cycle, x -> new TypeAnalysisImpl.CycleInfo());

        // we decide for the group
        if (modified.valueIsTrue()) {
            if (!cycleInfo.modified.isSet()) cycleInfo.modified.set();
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.TRUE_DV);
            return DONE;
        }

        // others have decided for us
        if (cycleInfo.modified.isSet()) {
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.TRUE_DV);
            return DONE;
        }

        if (modified.valueIsFalse()) {
            if (!cycleInfo.nonModified.contains(methodInfo)) cycleInfo.nonModified.add(methodInfo);

            if (cycleInfo.nonModified.size() == cycle.size()) {
                methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
                return DONE;
            }
        }
        // we all agree
        if (cycleInfo.nonModified.size() == cycle.size()) {
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
            return DONE;
        }
        // wait
        if (modified.valueIsFalse()) {
            LOGGER.debug("Delaying @Modified of method {}, cycle: {} out of {}", methodInfo, cycleInfo, cycle);
            return methodInfo.delay(CauseOfDelay.Cause.MODIFIED_CYCLE);
        }
        LOGGER.debug("Delaying @Modified of method {}, modified", methodInfo);
        return modified.causesOfDelay();
    }

    private AnalysisStatus computeModified(SharedState sharedState) {
        boolean isCycle = methodInfo.methodResolution.get().partOfCallCycle();
        Property property = isCycle ? Property.TEMP_MODIFIED_METHOD : Property.MODIFIED_METHOD;

        if (methodAnalysis.getProperty(property).isDone()) return DONE;
        if (methodInfo.isConstructor) {
            methodAnalysis.setProperty(MODIFIED_METHOD, DV.TRUE_DV);
            return DONE;
        }
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        DV scopeDelays = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fr
                        && fieldInMyTypeHierarchy(fr.fieldInfo, methodInfo.typeInfo))
                .map(vi -> connectedToMyTypeHierarchy((FieldReference) vi.variable()))
                .reduce(CausesOfDelay.EMPTY, DV::max);
        if (scopeDelays.isDelayed()) {
            if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                methodAnalysis.setProperty(MODIFIED_METHOD, MODIFIED_METHOD.falseDv);
                return DONE;
            }
            methodAnalysis.setProperty(property, scopeDelays);
            LOGGER.debug("Delaying @Modified of method {}, scope is delayed", methodInfo);
            return scopeDelays.causesOfDelay();
        }

        List<VariableInfo> relevantVariableInfos = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fr
                        && fieldInMyTypeHierarchy(fr.fieldInfo, methodInfo.typeInfo)
                        && connectedToMyTypeHierarchy(fr).valueIsTrue())
                .toList();
        // first step, check (my) field assignments
        boolean fieldAssignments = relevantVariableInfos.stream().anyMatch(VariableInfo::isAssigned);
        if (fieldAssignments) {
            LOGGER.debug("Method {} is @Modified: fields are being assigned", methodInfo);
            methodAnalysis.setProperty(property, DV.TRUE_DV);
            return DONE;
        }

        // if there are no field assignments, there may be modifying method calls

        // second step, check that CM is present (this generally implies that links have been established)
        DV contextModified = relevantVariableInfos.stream().map(vi -> vi.getProperty(CONTEXT_MODIFIED))
                .reduce(DV.FALSE_DV, DV::max);
        if (contextModified.isDelayed()) {
            if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                methodAnalysis.setProperty(MODIFIED_METHOD, MODIFIED_METHOD.falseDv);
                return DONE;
            }
            LOGGER.debug("Method {}: Not deciding on @Modified yet: no context modified", methodInfo);
            methodAnalysis.setProperty(property, contextModified);
            return contextModified.causesOfDelay();
        }

        if (contextModified.valueIsFalse()) { // also in static cases, sometimes a modification is written to "this" (MethodCall)
            VariableInfo thisVariable = getThisAsVariable();
            if (thisVariable != null) {
                DV thisModified = thisVariable.getProperty(Property.CONTEXT_MODIFIED);
                if (thisModified.isDelayed()) {
                    if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                        methodAnalysis.setProperty(MODIFIED_METHOD, MODIFIED_METHOD.falseDv);
                        return DONE;
                    }
                    LOGGER.debug("In {}: CONTEXT_MODIFIED of this is delayed", methodInfo);
                    methodAnalysis.setProperty(property, thisModified);
                    return thisModified.causesOfDelay();
                }
                contextModified = thisModified;
            }
        } // else: already true, so no need to look at this

        if (contextModified.valueIsFalse()) {
            DV maxModified = methodLevelData.copyModificationStatusFromKeyStream()
                    .map(mi -> mi.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD))
                    .reduce(DelayFactory.initialDelay(), DV::max);
            if (!maxModified.isInitialDelay()) {
                if (maxModified.isDelayed()) {
                    if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                        methodAnalysis.setProperty(MODIFIED_METHOD, MODIFIED_METHOD.falseDv);
                        return DONE;
                    }
                    LOGGER.debug("Delaying modification on method {}, waiting to copy", methodInfo);
                    methodAnalysis.setProperty(property, maxModified);
                    return maxModified.causesOfDelay();
                }
                contextModified = maxModified;
            }
        }
        LOGGER.debug("Mark method {} as {}", methodInfo, contextModified.valueIsTrue() ? "@Modified" : "@NotModified");
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(property, contextModified);
        return DONE;
    }

    /*
    should return true when:
    - scope is "this"
    - scope is recursively "this"
    - static scope, pointing to a type in my primary type
     */
    static DV connectedToMyTypeHierarchy(FieldReference fr) {
        if (fr.scopeIsThis()) return DV.TRUE_DV;
        IsVariableExpression ive;
        if ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null) {
            if (ive.variable() instanceof FieldReference fr2) {
                return connectedToMyTypeHierarchy(fr2);
            }
        }
        if (fr.scope instanceof TypeExpression te) {
            TypeInfo typeInfo = te.parameterizedType.bestTypeInfo();
            return DV.fromBoolDv(typeInfo != null && typeInfo.primaryType() == fr.fieldInfo.owner.primaryType());
        }
        //fr.scope.isDelayed() ? fr.scope.causesOfDelay() :
        return DV.FALSE_DV;
    }

    public boolean fieldInMyTypeHierarchy(FieldInfo fieldInfo, TypeInfo typeInfo) {
        if (typeInfo == fieldInfo.owner) return true;
        TypeInspection inspection = analyserContext.getTypeInspection(typeInfo);
        ParameterizedType parentClass = inspection.parentClass();
        if (!parentClass.isJavaLangObject()) {
            return fieldInMyTypeHierarchy(fieldInfo, parentClass.typeInfo);
        }
        return typeInfo.primaryType() == fieldInfo.owner.primaryType();
    }

    /*
     Independence of a method is always with respect to the return value, NOT the parameters.
     As a consequence, void methods, constructors, and methods that always throw an exception,
     are always INDEPENDENT without hidden content.
     */
    private AnalysisStatus analyseIndependent(SharedState sharedState) {
        if (methodAnalysis.getProperty(INDEPENDENT).isDone()) {
            return DONE;
        }

        // e.g. constructors
        if (methodInfo.noReturnValue()) {
            methodAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            return DONE;
        }

        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        if (lastStatement == null) {
            // the method always throws an exception
            methodAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            return DONE;
        }

        DV immutable = methodAnalysis.getPropertyFromMapDelayWhenAbsent(IMMUTABLE);
        DV independent;
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
            // computational shortcut: a recursively immutable type cannot be dependent, or have changes to hidden content
            independent = MultiLevel.INDEPENDENT_DV;
        } else {
            // normal computation
            VariableInfo variableInfo = getReturnAsVariable();
            LinkedVariables linkedVariables = variableInfo.getLinkedVariables();
            if (linkedVariables.isDelayed()) {
                if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                    methodAnalysis.setProperty(INDEPENDENT, INDEPENDENT.falseDv);
                    return DONE;
                }
                LOGGER.debug("Delaying independent of {}, linked variables not known", methodInfo);
                methodAnalysis.setProperty(INDEPENDENT, linkedVariables.causesOfDelay());
                return linkedVariables.causesOfDelay();
            }
            if (typeAnalysis.hiddenContentDelays().isDelayed()) {
                if (sharedState.breakDelayLevel.acceptMethodOverride()) {
                    methodAnalysis.setProperty(INDEPENDENT, INDEPENDENT.falseDv);
                    return DONE;
                }
                LOGGER.debug("Delaying independent of {}, hidden content not yet known", methodInfo);
                CausesOfDelay delay = typeAnalysis.hiddenContentDelays().causesOfDelay();
                methodAnalysis.setProperty(INDEPENDENT, delay);
                return AnalysisStatus.of(delay);
            }
            SetOfTypes hiddenContentCurrentType = typeAnalysis.getHiddenContentTypes();

            ComputeIndependent computeIndependent = new ComputeIndependent(analyserContext, hiddenContentCurrentType,
                    methodInfo.typeInfo, false);
            ParameterizedType concreteReturnType = variableInfo.getValue().returnType();
            if (concreteReturnType == ParameterizedType.NULL_CONSTANT) {
                methodAnalysis.setProperty(INDEPENDENT, INDEPENDENT.bestDv);
                return DONE;
            }
            boolean factoryMethod = methodInspection.isFactoryMethod();
            DV computed;
            if (factoryMethod) {
                computed = linkedVariables.stream()
                        .filter(e -> e.getKey() instanceof ParameterInfo pi && pi.owner == methodInfo)
                        .map(e -> computeIndependent.typesAtLinkLevel(e.getValue(),
                                concreteReturnType, immutable, e.getKey().parameterizedType()))
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            } else {
                computed = linkedVariables.stream()
                        .filter(e -> e.getKey() instanceof FieldReference fr && fr.scopeIsRecursivelyThis()
                                || e.getKey() instanceof This)
                        .map(e -> {
                            if (e.getKey() instanceof This && e.getValue().le(LinkedVariables.LINK_ASSIGNED)) {
                                // return this
                                return MultiLevel.INDEPENDENT_DV;
                            }
                            return computeIndependent.typesAtLinkLevel(e.getValue(), concreteReturnType, immutable, e.getKey().parameterizedType());
                        })
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
            }
            /*
            there is extensive code to potentially upgrade the immutable value of a method call (see MethodCall.dynamicImmutable())
            so when this happens, we have to go along.
             */
            DV fromImmutable = MultiLevel.independentCorrespondingToImmutable(immutable);
            independent = computed.max(fromImmutable);
        }
        if (sharedState.breakDelayLevel.acceptMethodOverride() && independent.isDelayed()) {
            methodAnalysis.setProperty(INDEPENDENT, INDEPENDENT.falseDv);
            return DONE;
        }
        methodAnalysis.setProperty(INDEPENDENT, independent);
        return AnalysisStatus.of(independent);
    }

    private AnalysisStatus computeStaticSideEffects(SharedState sharedState) {
        DV currentValue = methodAnalysis.getProperty(STATIC_SIDE_EFFECTS);
        assert currentValue.isDelayed();

        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();
        StaticSideEffects sse = methodLevelData.staticSideEffects();
        CausesOfDelay causesOfDelay = sse.causesOfDelay();

        if (causesOfDelay.isDelayed()) {
            return causesOfDelay;
        }

        DV dv = DV.fromBoolDv(!sse.expressions().isEmpty());
        LOGGER.debug("Set STATIC_SIDE_EFFECTS of {} to {}", methodInfo.name, dv);
        methodAnalysis.setProperty(STATIC_SIDE_EFFECTS, dv);
        return DONE;
    }

    @Override
    public Stream<Message> getMessageStream() {
        Stream<Message> localAnalyserStream = locallyCreatedPrimaryTypeAnalysers.stream()
                .flatMap(Analyser::getMessageStream);
        return Stream.concat(super.getMessageStream(), Stream.concat(localAnalyserStream,
                getParameterAnalysers().stream().flatMap(ParameterAnalyser::getMessageStream)));
    }

    public MethodLevelData methodLevelData() {
        return methodAnalysis.methodLevelData();
    }

    @Override
    public void logAnalysisStatuses() {
        AnalysisStatus statusOfStatementAnalyser = analyserComponents.getStatus(ComputingMethodAnalyser.STATEMENT_ANALYSER);
        if (statusOfStatementAnalyser.isDelayed()) {
            StatementAnalyser statement = findFirstStatementWithDelays(firstStatementAnalyser);
            assert statement != null;
            AnalyserComponents<String, StatementAnalyserSharedState> analyserComponentsOfStatement = statement.getAnalyserComponents();
            LOGGER.warn("Analyser components of first statement with delays {} of {}:\n{}", statement.index(),
                    methodInfo, analyserComponentsOfStatement.details());
            AnalysisStatus statusOfMethodLevelData = analyserComponentsOfStatement.getStatus(StatementAnalyserImpl.ANALYSE_METHOD_LEVEL_DATA);
            if (statusOfMethodLevelData.isDelayed()) {
                statement.getStatementAnalysis().methodLevelData().warnAboutAnalyserComponents(statement.index(),
                        methodInfo.fullyQualifiedName);
            }
        }
    }

    private StatementAnalyser findFirstStatementWithDelays(StatementAnalyser firstStatementAnalyser) {
        StatementAnalyser sa = firstStatementAnalyser;
        while (sa != null) {
            AnalyserComponents<String, StatementAnalyserSharedState> components = sa.getAnalyserComponents();
            if (components.getStatusesAsMap().values().stream().anyMatch(AnalysisStatus::isDelayed)) {
                return sa;
            }
            if (!sa.navigationData().next.isSet()) return sa;
            Optional<StatementAnalyser> opt = sa.navigationData().next.get();
            if (opt.orElse(null) == null) return sa;
            sa = opt.get();
        }
        return null;
    }

    // occurs as often in a flatMap as not, so a stream version is useful
    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? Stream.empty() : lastStatement.streamOfLatestInfoOfVariablesReferringTo(fieldInfo);
    }

    public VariableInfo getThisAsVariable() {
        StatementAnalysis last = methodAnalysis.getLastStatement();
        if (last == null) return null;
        return last.getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName + ".this");
    }

    public VariableInfo getReturnAsVariable() {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement(true);
        assert lastStatement != null; // either a constructor, and then we shouldn't ask; or compilation error
        return lastStatement.getLatestVariableInfo(methodInfo.fullyQualifiedName());
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        return firstStatementAnalyser.navigateTo(index);
    }

    public static Property external(Property property) {
        if (property == NOT_NULL_EXPRESSION) return EXTERNAL_NOT_NULL;
        if (property == IMMUTABLE) return EXTERNAL_IMMUTABLE;
        if (property == CONTAINER) return CONTAINER_RESTRICTION;
        if (property == IGNORE_MODIFICATIONS) return EXTERNAL_IGNORE_MODIFICATIONS;
        return property;
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration,
                                        BreakDelayLevel breakDelayLevel,
                                        ConditionManager conditionManager,
                                        EvaluationContext closure) {
            super(closure == null ? 1 : closure.getDepth() + 1, iteration, breakDelayLevel, conditionManager, closure);
        }

        @Override
        public EvaluationContext child(Expression condition, Set<Variable> conditionVariables) {
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition, conditionVariables,
                    Precondition.empty(getPrimitives()));
            return ComputingMethodAnalyser.this.new EvaluationContextImpl(iteration, breakDelayLevel, cm, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public DV getProperty(Variable variable, Property property) {
            if (variable instanceof FieldReference fieldReference) {
                Property vp = external(property);
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                Property vp = property == Property.NOT_NULL_EXPRESSION
                        ? Property.NOT_NULL_PARAMETER : property;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            if (variable instanceof This thisVar) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(thisVar.typeInfo);
                return typeAnalysis.getProperty(property);
            }
            throw new UnsupportedOperationException("Variable: " + variable.fullyQualifiedName() + " type " + variable.getClass());
        }

        @Override
        public DV getProperty(Expression value,
                              Property property,
                              boolean duringEvaluation,
                              boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression ve) {
                return getProperty(ve.variable(), property);
            }
            return value.getProperty(EvaluationResultImpl.from(this), property, true);
        }

        // needed in re-evaluation of inlined method in DetectEventual, before calling analyseExpression
        @Override
        public Expression currentValue(Variable variable,
                                       Expression scopeValue,
                                       Expression indexValue,
                                       Identifier identifier,
                                       ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(identifier, variable, VariableExpression.NO_SUFFIX, scopeValue, indexValue);
        }

        @Override
        public MethodAnalyserImpl getCurrentMethod() {
            return ComputingMethodAnalyser.this;
        }

        @Override
        public TypeInfo getCurrentType() {
            return methodInfo.typeInfo;
        }
    }
}
