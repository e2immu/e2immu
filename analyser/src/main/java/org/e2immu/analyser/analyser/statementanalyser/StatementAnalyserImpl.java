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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.ProgressWrapper;
import org.e2immu.analyser.analyser.impl.PrimaryTypeAnalyserImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.ExpandableAnalyserContextImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.VariableAccessReport;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.impl.ListOfSortedTypes;
import org.e2immu.analyser.resolver.impl.SortedType;
import org.e2immu.annotation.Container;
import org.e2immu.support.Either;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container(builds = StatementAnalysisImpl.class)
public class StatementAnalyserImpl implements StatementAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyserImpl.class);

    public static final String ANALYSE_METHOD_LEVEL_DATA = "analyseMethodLevelData";
    public static final String EVALUATION_OF_MAIN_EXPRESSION = "evaluationOfMainExpression";
    public static final String SUB_BLOCKS = "subBlocks";
    public static final String ANALYSE_TYPES_IN_STATEMENT = "analyseTypesInStatement";
    public static final String SET_BLOCK_EXECUTION = "setBlockExecution";
    public static final String ANALYSE_INTERRUPTS_FLOW = "analyseInterruptsFlow";
    public static final String FREEZE_ASSIGNMENT_IN_BLOCK = "freezeAssignmentInBlock";
    public static final String CHECK_UNUSED_RETURN_VALUE = "checkUnusedReturnValue";
    public static final String CHECK_USELESS_ASSIGNMENTS = "checkUselessAssignments";
    public static final String CHECK_UNUSED_LOCAL_VARIABLES = "checkUnusedLocalVariables";
    public static final String CHECK_UNUSED_LOOP_VARIABLES = "checkUnusedLoopVariables";
    public static final String INITIALISE_OR_UPDATE_VARIABLES = "initialiseOrUpdateVariables";
    public static final String CHECK_UNREACHABLE_STATEMENT = "checkUnreachableStatement";
    public static final String TRANSFER_FROM_CLOSURE_TO_RESULT = "transferFromClosureToResult";

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final ExpandableAnalyserContextImpl analyserContext;
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    //private ConditionManager localConditionManager;
    private final AnalyserResult.Builder analyserResultBuilder = new AnalyserResult.Builder()
            .setAnalysisStatus(NOT_YET_EXECUTED);
    private AnalyserComponents<String, StatementAnalyserSharedState> analyserComponents;
    private final SetOnce<List<PrimaryTypeAnalyser>> localAnalysers = new SetOnce<>();

    private final SAHelper helper;
    private final SAEvaluationOfMainExpression saEvaluation;
    private final SASubBlocks saSubBlocks;
    private final SACheck saCheck;
    private final FlipSwitch unreachable = new FlipSwitch();

    private StatementAnalyserImpl(AnalyserContext analyserContext,
                                  MethodAnalyser methodAnalyser,
                                  Statement statement,
                                  StatementAnalysis parent,
                                  String index,
                                  boolean inSyncBlock) {
        this.analyserContext = new ExpandableAnalyserContextImpl(Objects.requireNonNull(analyserContext));
        this.myMethodAnalyser = Objects.requireNonNull(methodAnalyser);
        this.statementAnalysis = new StatementAnalysisImpl(analyserContext.getPrimitives(),
                methodAnalyser.getMethodAnalysis(), statement, parent, index, inSyncBlock);
        this.helper = new SAHelper(statementAnalysis);
        SAApply saApply = new SAApply(statementAnalysis, myMethodAnalyser);
        saEvaluation = new SAEvaluationOfMainExpression(statementAnalysis, saApply,
                this, localAnalysers);
        saSubBlocks = new SASubBlocks(statementAnalysis, this);
        saCheck = new SACheck(statementAnalysis);
    }

    public static StatementAnalyserImpl recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock) {
        Objects.requireNonNull(myMethodAnalyser);
        Objects.requireNonNull(myMethodAnalyser.getMethodAnalysis());
        String adjustedIndices;
        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
            adjustedIndices = indices;
        } else {
            // we're in replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
            adjustedIndices = pos < 0 ? "" : indices.substring(0, pos);
        }
        StatementAnalyserImpl first = null;
        StatementAnalyserImpl previous = null;
        for (Statement statement : statements) {
            String padded = pad(statementIndex, statements.size());
            String iPlusSt = adjustedIndices.isEmpty() ? "" + padded : adjustedIndices + "." + padded;
            StatementAnalyserImpl statementAnalyser = new StatementAnalyserImpl(analyserContext, myMethodAnalyser, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.statementAnalysis.navigationData().next.set(Optional.of(statementAnalyser.statementAnalysis));
                previous.navigationData.next.set(Optional.of(statementAnalyser));
            }
            previous = statementAnalyser;
            if (first == null) first = statementAnalyser;

            int blockIndex = 0;
            List<Optional<StatementAnalyser>> blocks = new ArrayList<>();
            List<Optional<StatementAnalysis>> analysisBlocks = new ArrayList<>();

            Structure structure = statement.getStructure();
            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;
            if (!(statement instanceof SwitchStatementNewStyle)) {
                if (structure.haveStatements()) {
                    String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);

                    StatementAnalyserImpl subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                            statementAnalyser.statementAnalysis, structure.getStatements(),
                            indexWithBlock, true, newInSyncBlock);
                    blocks.add(Optional.of(subStatementAnalyser));
                    analysisBlocks.add(Optional.of(subStatementAnalyser.statementAnalysis));
                } else {
                    if (structure.isEmptyBlock()) {
                        blocks.add(Optional.empty());
                    }
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            } // else: this one only has sub-blocks
            for (Structure subStatements : structure.subStatements()) {
                if (subStatements.haveStatements()) {
                    String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);

                    StatementAnalyserImpl subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                            statementAnalyser.statementAnalysis, subStatements.getStatements(),
                            indexWithBlock, true, newInSyncBlock);
                    blocks.add(Optional.of(subStatementAnalyser));
                    analysisBlocks.add(Optional.of(subStatementAnalyser.statementAnalysis));
                } else {
                    blocks.add(Optional.empty());
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            }
            statementAnalyser.statementAnalysis.navigationData().blocks.set(List.copyOf(analysisBlocks));
            statementAnalyser.navigationData.blocks.set(List.copyOf(blocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.statementAnalysis.navigationData().next.set(Optional.empty());
            previous.navigationData.next.set(Optional.empty());
        }
        return first;

    }

    @Override
    public String toString() {
        return "Statement " + index() + " of " + myMethodAnalyser.getMethodInfo().fullyQualifiedName;
    }

    record PreviousAndFirst(StatementAnalyser previous, StatementAnalyser first) {
    }

    /**
     * Main recursive method, which follows the navigation chain for all statements in a block. When called from the method analyser,
     * it loops over the statements of the method.
     *
     * @param iteration             the current iteration
     * @param forwardAnalysisInfoIn information from the level above
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    public AnalyserResult analyseAllStatementsInBlock(int iteration,
                                                      ForwardAnalysisInfo forwardAnalysisInfoIn,
                                                      EvaluationContext closure) {
        try {
            // skip all the statements that are already in the DONE state...
            PreviousAndFirst previousAndFirst = goToFirstStatementToAnalyse();
            AnalyserResult.Builder builder = new AnalyserResult.Builder();

            if (previousAndFirst.first == null) {
                // nothing left to analyse
                return builder.build();
            }

            boolean delaySubsequentStatementBecauseOfECI = false;
            StatementAnalyser previousStatement = previousAndFirst.previous;
            StatementAnalyserImpl statementAnalyser = (StatementAnalyserImpl) previousAndFirst.first;
            Expression switchCondition = new BooleanConstant(statementAnalysis.primitives(), true);
            ForwardAnalysisInfo forwardAnalysisInfo = forwardAnalysisInfoIn;
            do {
                boolean wasReplacement;
                EvaluationContext evaluationContext = new SAEvaluationContext(statementAnalysis,
                        myMethodAnalyser, this, analyserContext,
                        localAnalysers, iteration, forwardAnalysisInfo.conditionManager(), closure,
                        delaySubsequentStatementBecauseOfECI, forwardAnalysisInfo.allowBreakDelay());
                if (analyserContext.getConfiguration().analyserConfiguration().skipTransformations()) {
                    wasReplacement = false;
                } else {
                    // first attempt at detecting a transformation
                    wasReplacement = statementAnalyser.checkForPatterns(evaluationContext);
                    statementAnalyser = (StatementAnalyserImpl) statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.getStatementAnalysis();
                EvaluationResult context = EvaluationResult.from(evaluationContext);
                switchCondition = forwardAnalysisInfo.conditionInSwitchStatement(context, previousStatement, switchCondition,
                        statementAnalyser.statementAnalysis);
                ForwardAnalysisInfo statementInfo = forwardAnalysisInfo.otherConditionManager(forwardAnalysisInfo.conditionManager()
                        .withCondition(context, switchCondition));

                AnalyserResult result = statementAnalyser.analyseSingleStatement(iteration, closure,
                        wasReplacement, previousStatementAnalysis, statementInfo, delaySubsequentStatementBecauseOfECI);
                delaySubsequentStatementBecauseOfECI |= result.analysisStatus().causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.ECI);

                builder.add(result);
                previousStatement = statementAnalyser;

                statementAnalyser = (StatementAnalyserImpl) statementAnalyser.navigationDataNextGet().orElse(null);

                if (result.analysisStatus().isProgress() && forwardAnalysisInfo.allowBreakDelay()) {
                    LOGGER.debug("**** Removing allow break delay for subsequent statements ****");
                    // uncomment the following statement if you want to break only delays at one statement,
                    // instead of in the whole method
                    // Expressions_0 will suffer a lot of you do that (currently at 50+ iterations)
                    //forwardAnalysisInfo = forwardAnalysisInfo.removeAllowBreakDelay();
                }
            } while (statementAnalyser != null);
            return builder.build();
        } catch (Throwable rte) {
            LOGGER.warn("Caught exception while analysing block {} of: {}, position {}", index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName(),
                    statementAnalysis.location(Stage.INITIAL));
            throw rte;
        }
    }

    @Override
    public StatementAnalyser navigateTo(String target) {
        return saSubBlocks.navigateTo(target);
    }

    @Override
    public NavigationData<StatementAnalyser> getNavigationData() {
        return navigationData;
    }

    @Override
    public StatementAnalyser followReplacements() {
        if (navigationData.replacement.isSet()) {
            return navigationData.replacement.get().followReplacements();
        }
        return this;
    }

    @Override
    public boolean navigationDataNextIsSet() {
        return navigationData.next.isSet();
    }

    @Override
    public Optional<StatementAnalyser> navigationDataNextGet() {
        return navigationData.next.get();
    }

    @Override
    public StatementAnalyser lastStatement() {
        return lastStatement(false);
    }

    @Override
    public StatementAnalyser lastStatement(boolean excludeThrows) {
        if (statementAnalysis.flowData().isUnreachable() && statementAnalysis.parent() == null) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        StatementAnalyser afterReplacements = followReplacements();
        if (!afterReplacements.navigationDataNextIsSet()) return afterReplacements;
        if (afterReplacements.navigationDataNextGet().isPresent()) {
            StatementAnalyser statementAnalyser = afterReplacements.navigationDataNextGet().get();
            if (statementAnalyser.getStatementAnalysis().flowData().isUnreachable() ||
                    excludeThrows && statementAnalyser.statement() instanceof ThrowStatement) {
                return afterReplacements;
            }
            // recursion
            return statementAnalyser.lastStatement(excludeThrows);
        } else {
            return afterReplacements;
        }
    }

    @Override
    public NavigationData<StatementAnalyser> navigationData() {
        return navigationData;
    }

    @Override
    public StatementAnalyser lastStatementOfSwitchOldStyle(String key) {
        return saSubBlocks.lastStatementOfSwitchOldStyle(key);
    }

    @Override
    public String index() {
        return statementAnalysis.index();
    }

    @Override
    public Statement statement() {
        return statementAnalysis.statement();
    }

    @Override
    public StatementAnalysis parent() {
        return statementAnalysis.parent();
    }

    @Override
    public void wireDirectly(StatementAnalyser newStatementAnalyser) {
        navigationData.replacement.set(newStatementAnalyser);
        statementAnalysis.navigationData().replacement.set(newStatementAnalyser.getStatementAnalysis());
    }

    @Override
    public void wireNext(StatementAnalyser newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
        statementAnalysis.navigationData().next.set(newStatement == null ? Optional.empty() :
                Optional.of(newStatement.getStatementAnalysis()));
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalyser> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(evaluationContext.getAnalyserContext(),
                evaluationContext.getCurrentMethod(),
                parent(), statements, startIndex, false, statementAnalysis.inSyncBlock());
    }

    @Override
    public boolean isDone() {
        return analyserResultBuilder.getAnalysisStatus() == DONE;
    }

    private PreviousAndFirst goToFirstStatementToAnalyse() {
        StatementAnalyser statementAnalyser = followReplacements();
        StatementAnalyser previous = null;
        while (statementAnalyser != null && statementAnalyser.isDone()) {
            LOGGER.debug("Skip statement {}, done", statementAnalyser.index());
            previous = statementAnalyser;
            statementAnalyser = statementAnalyser.navigationDataNextGet().orElse(null);
            if (statementAnalyser != null) {
                statementAnalyser = statementAnalyser.followReplacements();
            }
        }
        return new PreviousAndFirst(previous, statementAnalyser);
    }

    private boolean checkForPatterns(EvaluationContext evaluationContext) {
        PatternMatcher<StatementAnalyser> patternMatcher = analyserContext.getPatternMatcher();
        MethodInfo methodInfo = myMethodAnalyser.getMethodInfo();
        return patternMatcher.matchAndReplace(methodInfo, this, evaluationContext);
    }

    @Override
    public AnalyserComponents<String, StatementAnalyserSharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    /**
     * @param iteration                            the iteration
     * @param wasReplacement                       boolean, to ensure that the effect of a replacement warrants continued analysis
     * @param previous                             null if there was no previous statement in this block
     * @param delaySubsequentStatementBecauseOfECI explicit constructor invocation cannot be carried out, we'll have to wait
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    private AnalyserResult analyseSingleStatement(int iteration,
                                                  EvaluationContext closure,
                                                  boolean wasReplacement,
                                                  StatementAnalysis previous,
                                                  ForwardAnalysisInfo forwardAnalysisInfo,
                                                  boolean delaySubsequentStatementBecauseOfECI) {
        try {
            if (analyserComponents == null) {
                AnalysisStatus.AnalysisResultSupplier<StatementAnalyserSharedState> typesInStatement = sharedState -> {
                    AnalyserResult analyserResult = analyseTypesInStatement(sharedState);
                    // we don't copy directly; we'll filter later on in transferFromClosureToResult
                    analyserResultBuilder.addWithoutVariableAccess(analyserResult);
                    statementAnalysis.setVariableAccessReportOfSubAnalysers(analyserResult.variableAccessReport());
                    return analyserResult.analysisStatus();
                };

                // no program restrictions at the moment
                // be careful with limiting causes of delay, at least Cause.ECI has to pass!
                AnalyserProgram analyserProgram = AnalyserProgram.PROGRAM_ALL;
                analyserComponents = new AnalyserComponents.Builder<String, StatementAnalyserSharedState>(analyserProgram)
                        .add(CHECK_UNREACHABLE_STATEMENT, this::checkUnreachableStatement)
                        .add(INITIALISE_OR_UPDATE_VARIABLES, this::initialiseOrUpdateVariables)
                        .add(ANALYSE_TYPES_IN_STATEMENT, typesInStatement)
                        .add(EVALUATION_OF_MAIN_EXPRESSION, saEvaluation::evaluationOfMainExpression)
                        .add(SUB_BLOCKS, saSubBlocks::subBlocks)
                        .add(SET_BLOCK_EXECUTION, sharedState -> statementAnalysis.flowData()
                                .setBlockExecution(sharedState.forwardAnalysisInfo().execution()))
                        .add(ANALYSE_INTERRUPTS_FLOW, sharedState -> statementAnalysis.flowData()
                                .analyseInterruptsFlow(this, previous))
                        .add(FREEZE_ASSIGNMENT_IN_BLOCK, this::freezeAssignmentInBlock)
                        .add(ANALYSE_METHOD_LEVEL_DATA, sharedState ->
                                statementAnalysis.methodLevelData().analyse(sharedState, statementAnalysis,
                                        previous == null ? null : previous.methodLevelData(),
                                        previous == null ? null : previous.index(),
                                        statementAnalysis.stateData()))
                        .add(CHECK_UNUSED_RETURN_VALUE, sharedState -> saCheck.checkUnusedReturnValueOfMethodCall(analyserContext))
                        .add(CHECK_UNUSED_LOCAL_VARIABLES, sharedState -> saCheck.checkUnusedLocalVariables(navigationData))
                        .add(CHECK_UNUSED_LOOP_VARIABLES, sharedState -> saCheck.checkUnusedLoopVariables(navigationData))
                        .add(CHECK_USELESS_ASSIGNMENTS, sharedState -> saCheck.checkUselessAssignments(navigationData))
                        .add(TRANSFER_FROM_CLOSURE_TO_RESULT, this::transferFromClosureToResult)
                        .setLimitCausesOfDelay(true)
                        .build();
            }


            boolean startOfNewBlock = previous == null;
            ConditionManager localConditionManager;
            if (startOfNewBlock) {
                localConditionManager = forwardAnalysisInfo.conditionManager();
            } else {
                Expression condition;
                if (forwardAnalysisInfo.switchSelectorIsDelayed().isDelayed()) {
                    CausesOfDelay causes = forwardAnalysisInfo.conditionManager().condition().causesOfDelay()
                            .merge(forwardAnalysisInfo.switchSelectorIsDelayed().causesOfDelay());
                    condition = DelayedExpression.forSwitchSelector(Identifier.generate("switchSelector2"),
                            statementAnalysis.primitives(), forwardAnalysisInfo.switchSelector(), causes);
                } else {
                    condition = forwardAnalysisInfo.conditionManager().condition();
                }
                localConditionManager = ConditionManagerHelper.makeLocalConditionManager(condition.getIdentifier(), previous, condition);
            }

            EvaluationContext evaluationContext = new SAEvaluationContext(
                    statementAnalysis, myMethodAnalyser, this, analyserContext, localAnalysers,
                    iteration, localConditionManager, closure, delaySubsequentStatementBecauseOfECI,
                    forwardAnalysisInfo.allowBreakDelay());
            StatementAnalyserSharedState sharedState = new StatementAnalyserSharedState(evaluationContext,
                    EvaluationResult.from(evaluationContext),
                    analyserResultBuilder, previous, forwardAnalysisInfo, localConditionManager);
            AnalysisStatus overallStatus = analyserComponents.run(sharedState);

            AnalyserResult result = analyserResultBuilder
                    .addTypeAnalysers(localAnalysers.getOrDefault(List.of())) // unreachable statement...
                    .addMessages(statementAnalysis.messageStream())
                    .setAnalysisStatus(overallStatus)
                    .combineAnalysisStatus(wasReplacement
                            ? new ProgressWrapper(DelayFactory.createDelay(statementAnalysis.location(Stage.MERGE),
                            CauseOfDelay.Cause.REPLACEMENT))
                            : DONE, false)
                    .build();

            helper.visitStatementVisitors(statementAnalysis.index(), result, sharedState,
                    analyserContext.getConfiguration().debugConfiguration(), analyserComponents);

            if (overallStatus.isDone()) {
                statementAnalysis.internalAllDoneCheck();
                LOGGER.debug("*** ALL DONE {} {} ***", index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName);
            }

            LOGGER.debug("Returning from statement {} of {} with analysis status {}", statementAnalysis.index(),
                    myMethodAnalyser.getMethodInfo().name, result.analysisStatus());
            return result;
        } catch (Throwable rte) {
            LOGGER.warn("Caught exception while analysing statement {} of {}", index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName());
            throw rte;
        }
    }

    private AnalysisStatus freezeAssignmentInBlock(StatementAnalyserSharedState sharedState) {
        if (statementAnalysis.statement() instanceof LoopStatement) {
            ((StatementAnalysisImpl) statementAnalysis).freezeLocalVariablesAssignedInThisLoop();
        }
        return DONE; // only in first iteration
    }

    // in a separate task, so that it can be skipped when the statement is unreachable
    private AnalysisStatus initialiseOrUpdateVariables(StatementAnalyserSharedState sharedState) {
        if (sharedState.evaluationContext().getIteration() == 0) {
            statementAnalysis.initIteration0(sharedState.evaluationContext(), myMethodAnalyser.getMethodInfo(), sharedState.previous());
        } else {
            statementAnalysis.initIteration1Plus(sharedState.evaluationContext(), sharedState.previous());
        }

        if (statementAnalysis.flowData().initialTimeNotYetSet()) {
            int time;
            if (sharedState.previous() != null) {
                time = sharedState.previous().flowData().getTimeAfterSubBlocks();
            } else if (statementAnalysis.parent() != null) {
                time = statementAnalysis.parent().flowData().getTimeAfterEvaluation();
            } else {
                time = 0; // start
            }
            statementAnalysis.flowData().setInitialTime(time, index());
        }
        return RUN_AGAIN;
    }


    private AnalyserResult analyseTypesInStatement(StatementAnalyserSharedState sharedState) {
        if (!localAnalysers.isSet()) {
            List<TypeInfo> typeDefinedInStatement = statementAnalysis.statement().getStructure().findTypeDefinedInStatement();
            Stream<TypeInfo> locallyDefinedTypes = Stream.concat(typeDefinedInStatement.stream(),
                    statement() instanceof LocalClassDeclaration lcd ? Stream.of(lcd.typeInfo) : Stream.empty());
            List<PrimaryTypeAnalyser> analysers = locallyDefinedTypes
                    // those without a sorted type are already in the current primary type's sorted type!!
                    .filter(typeInfo -> typeInfo.typeResolution.get().sortedType() != null)
                    .map(typeInfo -> {
                        SortedType sortedType = typeInfo.typeResolution.get().sortedType();
                        // we'll use the default analyser generator here
                        ListOfSortedTypes listOfSortedTypes = new ListOfSortedTypes(List.of(sortedType));
                        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyserImpl(analyserContext,
                                listOfSortedTypes,
                                analyserContext.getConfiguration(),
                                analyserContext.getPrimitives(),
                                analyserContext.importantClasses(),
                                Either.left(analyserContext.getPatternMatcher()),
                                analyserContext.getE2ImmuAnnotationExpressions());
                        primaryTypeAnalyser.initialize();
                        return primaryTypeAnalyser;
                    }).toList();
            localAnalysers.set(analysers);
            recursivelyAddPrimaryTypeAnalysersToAnalyserContext(analysers);

            boolean haveNext = navigationData.next.get().isPresent();
            // first, simple propagation of those analysers that we've already accumulated
            if (haveNext) {
                ((StatementAnalyserImpl) navigationData.next.get().get()).analyserContext.addAll(analyserContext);
                navigationData.blocks.get().forEach(opt -> opt.ifPresent(sa ->
                        ((StatementAnalyserImpl) sa).analyserContext.addAll(analyserContext)));
            }
        }
        if (localAnalysers.get().isEmpty()) {
            return AnalyserResult.EMPTY;
        }
        AnalyserResult.Builder builder = new AnalyserResult.Builder();
        builder.setAnalysisStatus(NOT_YET_EXECUTED);
        for (PrimaryTypeAnalyser analyser : localAnalysers.get()) {
            LOGGER.debug("------- Starting local analyser {} ------", analyser.getName());
            Analyser.SharedState shared = new Analyser.SharedState(sharedState.evaluationContext().getIteration(),
                    sharedState.evaluationContext().allowBreakDelay(), sharedState.evaluationContext());
            AnalyserResult analyserResult = analyser.analyse(shared);
            builder.add(analyserResult);
            LOGGER.debug("------- Ending local analyser   {} ------", analyser.getName());
        }
        return builder.build();
    }

    private void recursivelyAddPrimaryTypeAnalysersToAnalyserContext(List<PrimaryTypeAnalyser> analysers) {
        AnalyserContext context = analyserContext;
        while (context != null) {
            if (context instanceof ExpandableAnalyserContextImpl expandable) {
                analysers.forEach(expandable::addPrimaryTypeAnalyser);
            }
            context = context.getParent();
        }
    }

    @Override
    public void makeImmutable() {
        if (localAnalysers.isSet()) {
            localAnalysers.get().forEach(PrimaryTypeAnalyser::makeImmutable);
        }
        for (Optional<StatementAnalyser> block : navigationData.blocks.get()) {
            block.ifPresent(StatementAnalyser::makeImmutable);
        }
        navigationData.next.get().ifPresent(StatementAnalyser::makeImmutable);
    }

    // executed only once per statement, at the very beginning of the loop
    // we're assuming that the flowData are computed correctly
    private AnalysisStatus checkUnreachableStatement(StatementAnalyserSharedState sharedState) {
        // if the previous statement was not reachable, we won't reach this one either
        if (sharedState.previous() != null
                && sharedState.previous().flowData().getGuaranteedToBeReachedInMethod().equals(FlowData.NEVER)) {
            makeUnreachable();
            return DONE_ALL;
        }
        DV execution = sharedState.forwardAnalysisInfo().execution();
        Expression state = sharedState.localConditionManager().state();
        CausesOfDelay localConditionManagerIsDelayed = sharedState.localConditionManager().causesOfDelay();
        AnalysisStatus analysisStatus = statementAnalysis.flowData().computeGuaranteedToBeReachedReturnUnreachable
                (sharedState.previous(), execution, state, state.causesOfDelay(), localConditionManagerIsDelayed);
        if (analysisStatus == DONE_ALL) {
            statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(Stage.INITIAL), Message.Label.UNREACHABLE_STATEMENT));
            makeUnreachable();
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return analysisStatus;
    }

    /*
    variables that were in the closure (coming from a statement outside this local primary type analyser)
    and were read or assigned, get marked in the result
    */
    private AnalysisStatus transferFromClosureToResult(StatementAnalyserSharedState statementAnalyserSharedState) {
        StatementAnalysis last = myMethodAnalyser.getMethodAnalysis().getLastStatement();
        if (last != statementAnalysis) {
            // no point in running this unless we are the last statement in the method
            return DONE;
        }
        EvaluationContext closure = statementAnalyserSharedState.evaluationContext().getClosure();
        if (closure != null) {
            VariableAccessReport.Builder builder = new VariableAccessReport.Builder();
            AtomicReference<CausesOfDelay> causes = new AtomicReference<>(CausesOfDelay.EMPTY);
            TypeInfo currentType = statementAnalyserSharedState.evaluationContext().getCurrentType();
            CausesOfDelay linksEstablished = statementAnalysis.methodLevelData().getLinksHaveBeenEstablished();
            statementAnalysis.variableStream().forEach(vi -> {
                // naive approach
                Variable variable = vi.variable();
                if (closure.acceptForVariableAccessReport(variable, currentType)) {
                    // mark, irrespective of whether it is present there or not (given that we are not the owner)
                    // readId will be 0-E, index 0
                    if (vi.isRead()) {
                        builder.addVariableRead(variable);
                    }
                    if (!(variable instanceof This)) {
                        DV modified = vi.getProperty(Property.CONTEXT_MODIFIED);

                    /*
                     the variable can be P-- in iteration 0, with modified == FALSE, and PEM in iteration 1, with a delay.
                     Only when the links have been established, can we be sure that modified will progress in a stable fashion.
                     */

                        DV combined = modified.isDelayed() || linksEstablished.isDone() ? modified :
                                modified.causesOfDelay().merge(linksEstablished);
                        builder.addContextProperty(variable, Property.CONTEXT_MODIFIED, combined); // also when delayed!!!
                        if (combined.isDelayed()) causes.set(causes.get().merge(combined.causesOfDelay()));

                        DV notNull = vi.getProperty(Property.CONTEXT_NOT_NULL);
                        builder.addContextProperty(variable, Property.CONTEXT_NOT_NULL, notNull);
                        if (notNull.isDelayed()) causes.set(causes.get().merge(notNull.causesOfDelay()));
                    } // else: context modified on this == modified_method
                }
            });
            VariableAccessReport variableAccessReport = builder.build();
            analyserResultBuilder.setVariableAccessReport(variableAccessReport);

            if (causes.get().isDelayed()) {
                LOGGER.debug("Delay transfer from closure to result: {}", causes.get());
                return causes.get();
            }
        }
        return DONE;
    }

    @Override
    public List<StatementAnalyser> lastStatementsOfNonEmptySubBlocks() {
        return navigationData.blocks.get().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sa -> !sa.getStatementAnalysis().flowData().isUnreachable())
                .map(StatementAnalyser::lastStatement)
                .collect(Collectors.toList());
    }

    @Override
    public EvaluationContext newEvaluationContextForOutside() {
        return new SAEvaluationContext(statementAnalysis, myMethodAnalyser, this,
                analyserContext, localAnalysers, 0,
                ConditionManager.initialConditionManager(statementAnalysis.primitives()), null,
                false, false);
    }

    @Override
    public StatementAnalysis getStatementAnalysis() {
        return statementAnalysis;
    }

    @Override
    public Stream<Message> getMessageStream() {
        return analyserResultBuilder.getMessageStream();
    }

    @Override
    public void makeUnreachable() {
        if (!unreachable.isSet()) {
            unreachable.set();
            navigationData.blocks.get().forEach(optSa -> optSa.ifPresent(StatementAnalyser::makeUnreachable));
            navigationData.next.get().ifPresent(StatementAnalyser::makeUnreachable);
            if (localAnalysers.isSet()) localAnalysers.get().forEach(PrimaryTypeAnalyser::makeUnreachable);
            statementAnalysis.makeUnreachable();
        }
    }
}
