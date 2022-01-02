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
import org.e2immu.analyser.analyser.delay.ProgressWrapper;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.impl.PrimaryTypeAnalyserImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.ExpandableAnalyserContextImpl;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.statement.LocalClassDeclaration;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.SynchronizedStatement;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.annotation.Container;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;
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
    public static final String CHECK_NOT_NULL_ESCAPES_AND_PRECONDITIONS = "checkNotNullEscapesAndPreconditions";
    public static final String CHECK_UNUSED_RETURN_VALUE = "checkUnusedReturnValue";
    public static final String CHECK_USELESS_ASSIGNMENTS = "checkUselessAssignments";
    public static final String CHECK_UNUSED_LOCAL_VARIABLES = "checkUnusedLocalVariables";
    public static final String CHECK_UNUSED_LOOP_VARIABLES = "checkUnusedLoopVariables";
    public static final String INITIALISE_OR_UPDATE_VARIABLES = "initialiseOrUpdateVariables";
    public static final String CHECK_UNREACHABLE_STATEMENT = "checkUnreachableStatement";

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final ExpandableAnalyserContextImpl analyserContext;
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    //private ConditionManager localConditionManager;
    private AnalysisStatus analysisStatus;
    private AnalyserComponents<String, StatementAnalyserSharedState> analyserComponents;
    private final SetOnce<List<PrimaryTypeAnalyser>> localAnalysers = new SetOnce<>();

    private final SAHelper helper;
    private final SAEvaluationOfMainExpression saEvaluation;
    private final SASubBlocks saSubBlocks;
    private final SACheck saCheck;

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
     * @param iteration           the current iteration
     * @param forwardAnalysisInfo information from the level above
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    public StatementAnalyserResult analyseAllStatementsInBlock(int iteration, ForwardAnalysisInfo forwardAnalysisInfo, EvaluationContext closure) {
        try {
            // skip all the statements that are already in the DONE state...
            PreviousAndFirst previousAndFirst = goToFirstStatementToAnalyse();
            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();

            if (previousAndFirst.first == null) {
                // nothing left to analyse
                return builder.setAnalysisStatus(DONE).build();
            }

            StatementAnalyser previousStatement = previousAndFirst.previous;
            StatementAnalyserImpl statementAnalyser = (StatementAnalyserImpl) previousAndFirst.first;
            Expression switchCondition = new BooleanConstant(statementAnalysis.primitives(), true);
            do {
                boolean wasReplacement;
                EvaluationContext evaluationContext = new SAEvaluationContext(statementAnalysis,
                        myMethodAnalyser, this, analyserContext,
                        localAnalysers, iteration, forwardAnalysisInfo.conditionManager(), closure);
                if (analyserContext.getConfiguration().analyserConfiguration().skipTransformations()) {
                    wasReplacement = false;
                } else {
                    // first attempt at detecting a transformation
                    wasReplacement = statementAnalyser.checkForPatterns(evaluationContext);
                    statementAnalyser = (StatementAnalyserImpl) statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.getStatementAnalysis();
                switchCondition = forwardAnalysisInfo.conditionInSwitchStatement(evaluationContext, previousStatement, switchCondition, statementAnalysis);
                ForwardAnalysisInfo statementInfo = forwardAnalysisInfo.otherConditionManager(forwardAnalysisInfo.conditionManager()
                        .withCondition(evaluationContext, switchCondition, forwardAnalysisInfo.switchSelectorIsDelayed()));

                StatementAnalyserResult result = statementAnalyser.analyseSingleStatement(iteration, closure,
                        wasReplacement, previousStatementAnalysis, statementInfo);
                builder.add(result);
                previousStatement = statementAnalyser;

                statementAnalyser = (StatementAnalyserImpl) statementAnalyser.navigationDataNextGet().orElse(null);
            } while (statementAnalyser != null);
            return builder.build();
        } catch (Throwable rte) {
            LOGGER.warn("Caught exception while analysing block {} of: {}", index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName());
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

    // note that there is a clone of this in statementAnalysis
    @Override
    public StatementAnalyser lastStatement() {
        if (statementAnalysis.flowData().isUnreachable() && statementAnalysis.parent() == null) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        StatementAnalyser afterReplacements = followReplacements();
        if (!afterReplacements.navigationDataNextIsSet()) return afterReplacements;
        return afterReplacements.navigationDataNextGet().map(statementAnalyser -> {
            if (statementAnalyser.getStatementAnalysis().flowData().isUnreachable()) {
                return afterReplacements;
            }
            return statementAnalyser.lastStatement();
        }).orElse(afterReplacements);
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
        return analysisStatus == DONE;
    }

    private PreviousAndFirst goToFirstStatementToAnalyse() {
        StatementAnalyser statementAnalyser = followReplacements();
        StatementAnalyser previous = null;
        while (statementAnalyser != null && statementAnalyser.isDone()) {
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

    private Location getLocation() {
        return statementAnalysis.location();
    }

    @Override
    public AnalyserComponents<String, StatementAnalyserSharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    /**
     * @param iteration      the iteration
     * @param wasReplacement boolean, to ensure that the effect of a replacement warrants continued analysis
     * @param previous       null if there was no previous statement in this block
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    private StatementAnalyserResult analyseSingleStatement(int iteration,
                                                           EvaluationContext closure,
                                                           boolean wasReplacement,
                                                           StatementAnalysis previous,
                                                           ForwardAnalysisInfo forwardAnalysisInfo) {
        try {
            if (analysisStatus == null) {
                //assert localConditionManager == null : "expected null localConditionManager";
                assert analyserComponents == null : "expected null analyser components";

                // no program restrictions at the moment
                AnalyserProgram analyserProgram = AnalyserProgram.PROGRAM_ALL;
                analyserComponents = new AnalyserComponents.Builder<String, StatementAnalyserSharedState>(analyserProgram)
                        .add(CHECK_UNREACHABLE_STATEMENT, this::checkUnreachableStatement)
                        .add(INITIALISE_OR_UPDATE_VARIABLES, this::initialiseOrUpdateVariables)
                        .add(ANALYSE_TYPES_IN_STATEMENT, this::analyseTypesInStatement)
                        .add(EVALUATION_OF_MAIN_EXPRESSION, saEvaluation::evaluationOfMainExpression)
                        .add(SUB_BLOCKS, saSubBlocks::subBlocks)
                        .add(SET_BLOCK_EXECUTION, sharedState -> statementAnalysis.flowData()
                                .setBlockExecution(sharedState.forwardAnalysisInfo().execution()))
                        .add(ANALYSE_INTERRUPTS_FLOW, sharedState -> statementAnalysis.flowData()
                                .analyseInterruptsFlow(this, previous))
                        .add(FREEZE_ASSIGNMENT_IN_BLOCK, this::freezeAssignmentInBlock)
                        .add(CHECK_NOT_NULL_ESCAPES_AND_PRECONDITIONS, saCheck::checkNotNullEscapesAndPreconditions)
                        .add(ANALYSE_METHOD_LEVEL_DATA, sharedState ->
                                statementAnalysis.methodLevelData().analyse(sharedState, statementAnalysis,
                                        previous == null ? null : previous.methodLevelData(),
                                        previous == null ? null : previous.index(),
                                        statementAnalysis.stateData()))
                        .add(CHECK_UNUSED_RETURN_VALUE, sharedState -> saCheck.checkUnusedReturnValueOfMethodCall(analyserContext))
                        .add(CHECK_UNUSED_LOCAL_VARIABLES, sharedState -> saCheck.checkUnusedLocalVariables(navigationData))
                        .add(CHECK_UNUSED_LOOP_VARIABLES, sharedState -> saCheck.checkUnusedLoopVariables(navigationData))
                        .add(CHECK_USELESS_ASSIGNMENTS, sharedState -> saCheck.checkUselessAssignments(navigationData))
                        .build();
            }


            boolean startOfNewBlock = previous == null;
            ConditionManager localConditionManager;
            if (startOfNewBlock) {
                localConditionManager = forwardAnalysisInfo.conditionManager();
            } else {
                localConditionManager = ConditionManagerHelper.makeLocalConditionManager(previous, forwardAnalysisInfo.conditionManager().condition(),
                        forwardAnalysisInfo.switchSelectorIsDelayed());
            }

            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
            EvaluationContext evaluationContext = new SAEvaluationContext(
                    statementAnalysis, myMethodAnalyser, this, analyserContext, localAnalysers,
                    iteration, localConditionManager, closure);
            StatementAnalyserSharedState sharedState = new StatementAnalyserSharedState(evaluationContext, builder, previous, forwardAnalysisInfo, localConditionManager);
            AnalysisStatus overallStatus = analyserComponents.run(sharedState);

            StatementAnalyserResult result = sharedState.builder()
                    .addTypeAnalysers(localAnalysers.getOrDefault(List.of())) // unreachable statement...
                    .addMessages(statementAnalysis.messageStream())
                    .setAnalysisStatus(overallStatus)
                    .combineAnalysisStatus(wasReplacement
                            ? new ProgressWrapper(new SimpleSet(getLocation(), CauseOfDelay.Cause.REPLACEMENT))
                            : DONE)
                    .build();
            analysisStatus = result.analysisStatus();

            helper.visitStatementVisitors(statementAnalysis.index(), result, sharedState,
                    analyserContext.getConfiguration().debugConfiguration(), analyserComponents);

            log(ANALYSER, "Returning from statement {} of {} with analysis status {}", statementAnalysis.index(),
                    myMethodAnalyser.getMethodInfo().name, analysisStatus);
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


    private AnalysisStatus analyseTypesInStatement(StatementAnalyserSharedState sharedState) {
        if (!localAnalysers.isSet()) {
            List<TypeInfo> typeDefinedInStatement = statementAnalysis.statement().getStructure().findTypeDefinedInStatement();
            Stream<TypeInfo> locallyDefinedTypes = Stream.concat(typeDefinedInStatement.stream(),
                    statement() instanceof LocalClassDeclaration lcd ? Stream.of(lcd.typeInfo) : Stream.empty());
            List<PrimaryTypeAnalyser> analysers = locallyDefinedTypes
                    // those without a sorted type are already in the current primary type's sorted type!!
                    .filter(typeInfo -> typeInfo.typeResolution.get().sortedType() != null)
                    .map(typeInfo -> {
                        SortedType sortedType = typeInfo.typeResolution.get().sortedType();
                        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyserImpl(analyserContext,
                                List.of(sortedType),
                                analyserContext.getConfiguration(),
                                analyserContext.getPrimitives(),
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

        AnalysisStatus analysisStatus = DONE;
        for (PrimaryTypeAnalyser analyser : localAnalysers.get()) {
            log(ANALYSER, "------- Starting local analyser {} ------", analyser.getName());
            AnalysisStatus lambdaStatus = analyser.analyse(sharedState.evaluationContext().getIteration(), sharedState.evaluationContext());
            log(ANALYSER, "------- Ending local analyser   {} ------", analyser.getName());
            analysisStatus = analysisStatus.combine(lambdaStatus);
            statementAnalysis.ensureMessages(analyser.getMessageStream());
        }


        return analysisStatus;
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
            statementAnalysis.flowData().setGuaranteedToBeReached(FlowData.NEVER);
            return DONE_ALL;
        }
        DV execution = sharedState.forwardAnalysisInfo().execution();
        Expression state = sharedState.localConditionManager().state();
        CausesOfDelay localConditionManagerIsDelayed = sharedState.localConditionManager().causesOfDelay();
        AnalysisStatus analysisStatus = statementAnalysis.flowData().computeGuaranteedToBeReachedReturnUnreachable
                (sharedState.previous(), execution, state, state.causesOfDelay(), localConditionManagerIsDelayed);
        if (analysisStatus == DONE_ALL) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.UNREACHABLE_STATEMENT));
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return analysisStatus;
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
                ConditionManager.initialConditionManager(statementAnalysis.primitives()), null);
    }

    @Override
    public StatementAnalysis getStatementAnalysis() {
        return statementAnalysis;
    }

}
