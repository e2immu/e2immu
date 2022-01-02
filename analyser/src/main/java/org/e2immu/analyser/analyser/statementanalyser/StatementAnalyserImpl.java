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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
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
    private final SAApply saApply;
    private final SAEvaluationOfMainExpression saEvaluation;
    private final SASubBlocks saSubBlocks;

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
        saApply = new SAApply(statementAnalysis, myMethodAnalyser);
        saEvaluation = new SAEvaluationOfMainExpression(statementAnalysis, saApply,
                this, localAnalysers);
        saSubBlocks = new SASubBlocks(statementAnalysis, this);
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
                        .add(CHECK_NOT_NULL_ESCAPES_AND_PRECONDITIONS, this::checkNotNullEscapesAndPreconditions)
                        .add(ANALYSE_METHOD_LEVEL_DATA, sharedState ->
                                statementAnalysis.methodLevelData().analyse(sharedState, statementAnalysis,
                                        previous == null ? null : previous.methodLevelData(),
                                        previous == null ? null : previous.index(),
                                        statementAnalysis.stateData()))
                        .add(CHECK_UNUSED_RETURN_VALUE, sharedState -> checkUnusedReturnValueOfMethodCall())
                        .add(CHECK_UNUSED_LOCAL_VARIABLES, sharedState -> checkUnusedLocalVariables())
                        .add(CHECK_UNUSED_LOOP_VARIABLES, sharedState -> checkUnusedLoopVariables())
                        .add(CHECK_USELESS_ASSIGNMENTS, sharedState -> checkUselessAssignments())
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
            Stream<TypeInfo> locallyDefinedTypes = Stream.concat(statementAnalysis.statement().getStructure().findTypeDefinedInStatement().stream(),
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

    /*
    delays on ENN are dealt with later than normal delays on values
     */
    record ApplyStatusAndEnnStatus(CausesOfDelay status, CausesOfDelay ennStatus) {
        public AnalysisStatus combinedStatus() {
            CausesOfDelay delay = status.merge(ennStatus);
            return AnalysisStatus.of(delay);
        }
    }


    /*
    Not-null escapes should not contribute to preconditions.
    All the rest should.
     */
    private AnalysisStatus checkNotNullEscapesAndPreconditions(StatementAnalyserSharedState sharedState) {
        if (statementAnalysis.statement() instanceof AssertStatement) return DONE; // is dealt with in subBlocks
        DV escapeAlwaysExecuted = statementAnalysis.isEscapeAlwaysExecutedInCurrentBlock();
        CausesOfDelay delays = escapeAlwaysExecuted.causesOfDelay()
                .merge(statementAnalysis.stateData().conditionManagerForNextStatement.get().causesOfDelay());
        if (!escapeAlwaysExecuted.valueIsFalse()) {
            Set<Variable> nullVariables = statementAnalysis.stateData().conditionManagerForNextStatement.get()
                    .findIndividualNullInCondition(sharedState.evaluationContext(), true);
            for (Variable nullVariable : nullVariables) {
                log(PRECONDITION, "Escape with check not null on {}", nullVariable.fullyQualifiedName());

                ensureContextNotNullForParent(nullVariable, delays, escapeAlwaysExecuted.valueIsTrue());
                if (nullVariable instanceof LocalVariableReference lvr && lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy) {
                    ensureContextNotNullForParent(copy.localCopyOf(), delays, escapeAlwaysExecuted.valueIsTrue());
                }
            }
            if (escapeAlwaysExecuted.valueIsTrue()) {
                // escapeCondition should filter out all != null, == null clauses
                Expression precondition = statementAnalysis.stateData().conditionManagerForNextStatement.get()
                        .precondition(sharedState.evaluationContext());
                CausesOfDelay preconditionIsDelayed = precondition.causesOfDelay().merge(delays);
                Expression translated = sharedState.evaluationContext().acceptAndTranslatePrecondition(precondition);
                if (translated != null) {
                    log(PRECONDITION, "Escape with precondition {}", translated);
                    Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
                    statementAnalysis.stateData().setPrecondition(pc, preconditionIsDelayed.isDelayed());
                    return AnalysisStatus.of(preconditionIsDelayed);
                }
            }

            if (delays.isDelayed()) return delays;
        }
        if (statementAnalysis.stateData().preconditionIsEmpty()) {
            // it could have been set from the assert statement (subBlocks) or apply via a method call
            statementAnalysis.stateData().setPreconditionAllowEquals(Precondition.empty(statementAnalysis.primitives()));
        } else if (!statementAnalysis.stateData().preconditionIsFinal()) {
            return statementAnalysis.stateData().getPrecondition().expression().causesOfDelay();
        }
        return DONE;
    }


    private void ensureContextNotNullForParent(Variable nullVariable, CausesOfDelay delays, boolean notifyParent) {
        // move from condition (x!=null) to property
        VariableInfoContainer vic = statementAnalysis.findForWriting(nullVariable);
        if (!vic.hasEvaluation()) {
            VariableInfo initial = vic.getPreviousOrInitial();
            vic.ensureEvaluation(getLocation(), initial.getAssignmentIds(), initial.getReadId(),
                    initial.getStatementTime(), initial.getReadAtStatementTimes());
        }
        DV valueToSet = delays.isDone() ? (notifyParent ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV) : delays;
        vic.setProperty(CONTEXT_NOT_NULL_FOR_PARENT, valueToSet, EVALUATION);
    }


    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li NYR + local variable created higher up + return: <code>int i=0; if(xxx) { i=3; return; }</code></li>
     *     <li>NYR + escape: <code>int i=0; if(xxx) { i=3; throw new UnsupportedOperationException(); }</code></li>
     * </ul>
     * Comes after unused local variable, we do not want 2 errors
     */
    private AnalysisStatus checkUselessAssignments() {
        if (!statementAnalysis.flowData().interruptsFlowIsSet()) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return statementAnalysis.flowData().interruptStatus().causesOfDelay();
        }
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData().bestAlwaysInterrupt();
        DV reached = statementAnalysis.flowData().getGuaranteedToBeReachedInMethod();
        if (reached.isDelayed()) return reached.causesOfDelay();

        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if ((atEndOfBlock || alwaysInterrupts) && myMethodAnalyser.getMethodInfo().isNotATestMethod()) {
            // important to be after this statement, because assignments need to be "earlier" in notReadAfterAssignment
            String indexEndOfBlock = StringUtil.beyond(index());
            statementAnalysis.rawVariableStream()
                    .filter(e -> e.getValue().variableNature() != VariableNature.FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof ReturnVariable)) // that's for the compiler!
                    .filter(this::uselessForDependentVariable)
                    .filter(vi -> vi.notReadAfterAssignment(indexEndOfBlock))
                    .forEach(variableInfo -> {
                        boolean isLocalAndLocalToThisBlock = statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name());
                        if (bestAlwaysInterrupt == InterruptsFlow.ESCAPE ||
                                isLocalAndLocalToThisBlock ||
                                variableInfo.variable().isLocal() && bestAlwaysInterrupt == InterruptsFlow.RETURN &&
                                        localVariableAssignmentInThisBlock(variableInfo)) {
                            Location location = getLocation();
                            Message unusedLv = Message.newMessage(location,
                                    Message.Label.UNUSED_LOCAL_VARIABLE, variableInfo.name());
                            if (!statementAnalysis.containsMessage(unusedLv)) {
                                statementAnalysis.ensure(Message.newMessage(location,
                                        Message.Label.USELESS_ASSIGNMENT, variableInfo.name()));
                            }
                        }
                    });
        }
        return DONE;
    }

    private boolean uselessForDependentVariable(VariableInfo variableInfo) {
        if (variableInfo.variable() instanceof DependentVariable dv) {
            return dv.arrayVariable != null && !variableHasBeenReadAfter(dv.arrayVariable,
                    variableInfo.getAssignmentIds().getLatestAssignment());
        }
        return true;
    }

    private boolean variableHasBeenReadAfter(Variable variable, String assignment) {
        VariableInfo variableInfo = statementAnalysis.findOrThrow(variable);
        int c = variableInfo.getReadId().compareTo(assignment);
        return c > 0;
    }

    private boolean localVariableAssignmentInThisBlock(VariableInfo variableInfo) {
        assert variableInfo.variable().isLocal();
        if (!variableInfo.isAssigned()) return false;
        return StringUtil.inSameBlock(variableInfo.getAssignmentIds().getLatestAssignmentIndex(), index());
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty() && myMethodAnalyser.getMethodInfo().isNotATestMethod()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.rawVariableStream()
                    .filter(e -> !(e.getValue().variableNature() instanceof VariableNature.LoopVariable) &&
                            e.getValue().variableNature() != VariableNature.FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof DependentVariable))
                    .filter(vi -> statementAnalysis.isLocalVariableAndLocalToThisBlock(vi.name()) && !vi.isRead())
                    .forEach(vi -> statementAnalysis.ensure(Message.newMessage(getLocation(),
                            Message.Label.UNUSED_LOCAL_VARIABLE, vi.name())));
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedLoopVariables() {
        if (statement() instanceof LoopStatement
                && !statementAnalysis.containsMessage(Message.Label.EMPTY_LOOP)
                && myMethodAnalyser.getMethodInfo().isNotATestMethod()) {
            statementAnalysis.rawVariableStream()
                    .filter(e -> e.getValue().variableNature() instanceof VariableNature.LoopVariable loopVariable &&
                            loopVariable.statementIndex().equals(index()))
                    .forEach(e -> {
                        String loopVarFqn = e.getKey();
                        StatementAnalyser first = navigationData.blocks.get().get(0).orElse(null);
                        StatementAnalysis statementAnalysis = first == null ? null : first.lastStatement().getStatementAnalysis();
                        if (statementAnalysis == null || !statementAnalysis.variableIsSet(loopVarFqn) ||
                                !statementAnalysis.getVariable(loopVarFqn).current().isRead()) {
                            this.statementAnalysis.ensure(Message.newMessage(getLocation(),
                                    Message.Label.UNUSED_LOOP_VARIABLE, loopVarFqn));
                        }
                    });
        }
        return DONE;
    }

    /*
     * Can be delayed
     */
    private AnalysisStatus checkUnusedReturnValueOfMethodCall() {
        if (statementAnalysis.statement() instanceof ExpressionAsStatement eas
                && eas.expression instanceof MethodCall methodCall
                && myMethodAnalyser.getMethodInfo().isNotATestMethod()) {
            if (methodCall.methodInfo.returnType().isVoidOrJavaLangVoid()) return DONE;
            MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
            DV identity = methodAnalysis.getProperty(Property.IDENTITY);
            if (identity.isDelayed()) {
                log(DELAYED, "Delaying unused return value in {} {}, waiting for @Identity of {}",
                        index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
                return identity.causesOfDelay();
            }
            if (identity.valueIsTrue()) return DONE;
            DV modified = methodAnalysis.getProperty(MODIFIED_METHOD);
            if (modified.isDelayed() && !methodCall.methodInfo.isAbstract()) {
                log(DELAYED, "Delaying unused return value in {} {}, waiting for @Modified of {}",
                        index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
                return modified.causesOfDelay();
            }
            if (modified.valueIsFalse()) {
                MethodInspection methodCallInspection = analyserContext.getMethodInspection(methodCall.methodInfo);
                if (methodCallInspection.isStatic()) {
                    // for static methods, we verify if one of the parameters is modifying
                    CausesOfDelay delays = CausesOfDelay.EMPTY;
                    for (ParameterInfo parameterInfo : methodCallInspection.getParameters()) {
                        ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                        DV mv = parameterAnalysis.getProperty(MODIFIED_VARIABLE);
                        if (mv.valueIsTrue()) {
                            return DONE;
                        }
                        if (mv.isDelayed()) {
                            delays = delays.merge(mv.causesOfDelay());
                        }
                    }
                    if (delays.isDelayed()) {
                        log(DELAYED, "Delaying unused return value {} {}, waiting for @Modified of parameters in {}",
                                index(), myMethodAnalyser.getMethodInfo().fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName());
                        return delays;
                    }
                }

                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
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
                ConditionManager.initialConditionManager(statementAnalysis.primitives()), null);
    }

    @Override
    public StatementAnalysis getStatementAnalysis() {
        return statementAnalysis;
    }

}
