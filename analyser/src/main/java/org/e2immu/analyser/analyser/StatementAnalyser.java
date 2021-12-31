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

import org.e2immu.analyser.analyser.util.FindInstanceOfPatterns;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.annotation.Container;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.INITIAL;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser>, HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);

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
    private AnalyserComponents<String, SharedState> analyserComponents;
    private final SetOnce<List<PrimaryTypeAnalyser>> localAnalysers = new SetOnce<>();

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index,
                              boolean inSyncBlock) {
        this.analyserContext = new ExpandableAnalyserContextImpl(Objects.requireNonNull(analyserContext));
        this.myMethodAnalyser = Objects.requireNonNull(methodAnalyser);
        this.statementAnalysis = new StatementAnalysis(analyserContext.getPrimitives(),
                methodAnalyser.methodAnalysis, statement, parent, index, inSyncBlock);
    }

    public static StatementAnalyser recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock) {
        Objects.requireNonNull(myMethodAnalyser);
        Objects.requireNonNull(myMethodAnalyser.methodAnalysis);
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
        StatementAnalyser first = null;
        StatementAnalyser previous = null;
        for (Statement statement : statements) {
            String padded = pad(statementIndex, statements.size());
            String iPlusSt = adjustedIndices.isEmpty() ? "" + padded : adjustedIndices + "." + padded;
            StatementAnalyser statementAnalyser = new StatementAnalyser(analyserContext, myMethodAnalyser, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.statementAnalysis.navigationData.next.set(Optional.of(statementAnalyser.statementAnalysis));
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

                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
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

                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
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
            statementAnalyser.statementAnalysis.navigationData.blocks.set(List.copyOf(analysisBlocks));
            statementAnalyser.navigationData.blocks.set(List.copyOf(blocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.statementAnalysis.navigationData.next.set(Optional.empty());
            previous.navigationData.next.set(Optional.empty());
        }
        return first;

    }

    @Override
    public String toString() {
        return "Statement " + index() + " of " + myMethodAnalyser.methodInfo.fullyQualifiedName;
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
            StatementAnalyser statementAnalyser = previousAndFirst.first;
            Expression switchCondition = new BooleanConstant(statementAnalysis.primitives, true);
            do {
                boolean wasReplacement;
                EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager(), closure);
                if (analyserContext.getConfiguration().analyserConfiguration().skipTransformations()) {
                    wasReplacement = false;
                } else {
                    // first attempt at detecting a transformation
                    wasReplacement = statementAnalyser.checkForPatterns(evaluationContext);
                    statementAnalyser = statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.statementAnalysis;
                switchCondition = statementAnalyser.conditionInSwitchStatement(forwardAnalysisInfo, evaluationContext, previousStatement, switchCondition);
                ForwardAnalysisInfo statementInfo = forwardAnalysisInfo.otherConditionManager(forwardAnalysisInfo.conditionManager()
                        .withCondition(evaluationContext, switchCondition, forwardAnalysisInfo.switchSelectorIsDelayed()));

                StatementAnalyserResult result = statementAnalyser.analyseSingleStatement(iteration, closure,
                        wasReplacement, previousStatementAnalysis, statementInfo);
                builder.add(result);
                previousStatement = statementAnalyser;

                statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            } while (statementAnalyser != null);
            return builder.build();
        } catch (Throwable rte) {
            LOGGER.warn("Caught exception while analysing block {} of: {}", index(), myMethodAnalyser.methodInfo.fullyQualifiedName());
            throw rte;
        }
    }

    /*
    this is the statement to be executed (with an ID that can match one of the elements in the map
     */
    private Expression conditionInSwitchStatement(ForwardAnalysisInfo base,
                                                  EvaluationContext evaluationContext,
                                                  StatementAnalyser previousStatement,
                                                  Expression previous) {
        if (base.switchIdToLabels() != null) {
            Statement statement = previousStatement == null ? null : previousStatement.statement();
            Expression startFrom;
            if (statement instanceof BreakStatement || statement instanceof ReturnStatement) {
                // clear all
                startFrom = new BooleanConstant(statementAnalysis.primitives, true);
            } else {
                startFrom = previous;
            }
            Expression label = base.switchIdToLabels().get(index());
            if (label != null) {
                Expression toAdd;
                if (label == EmptyExpression.DEFAULT_EXPRESSION) {
                    toAdd = Negation.negate(evaluationContext,
                            Or.or(evaluationContext, base.switchIdToLabels().values().stream()
                                    .filter(e -> e != EmptyExpression.DEFAULT_EXPRESSION).toList()));
                } else {
                    toAdd = label;
                }
                if (startFrom.isBoolValueTrue()) return toAdd;
                return And.and(evaluationContext, startFrom, toAdd);
            }
            return startFrom;
        }
        return previous;
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

    // note that there is a clone of this in statementAnalysis
    @Override
    public StatementAnalyser lastStatement() {
        if (statementAnalysis.flowData.isUnreachable() && statementAnalysis.parent == null) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        StatementAnalyser afterReplacements = followReplacements();
        if (!afterReplacements.navigationData.next.isSet()) return afterReplacements;
        return afterReplacements.navigationData.next.get().map(statementAnalyser -> {
            if (statementAnalyser.statementAnalysis.flowData.isUnreachable()) {
                return afterReplacements;
            }
            return statementAnalyser.lastStatement();
        }).orElse(afterReplacements);
    }

    @Override
    public String index() {
        return statementAnalysis.index;
    }

    @Override
    public Statement statement() {
        return statementAnalysis.statement;
    }

    @Override
    public StatementAnalysis parent() {
        return statementAnalysis.parent;
    }

    @Override
    public void wireDirectly(StatementAnalyser newStatementAnalyser) {
        navigationData.replacement.set(newStatementAnalyser);
        statementAnalysis.navigationData.replacement.set(newStatementAnalyser.statementAnalysis);
    }

    @Override
    public void wireNext(StatementAnalyser newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
        statementAnalysis.navigationData.next.set(newStatement == null ? Optional.empty() : Optional.of(newStatement.statementAnalysis));
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalyser> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(evaluationContext.getAnalyserContext(),
                evaluationContext.getCurrentMethod(),
                parent(), statements, startIndex, false, statementAnalysis.inSyncBlock);
    }

    private PreviousAndFirst goToFirstStatementToAnalyse() {
        StatementAnalyser statementAnalyser = followReplacements();
        StatementAnalyser previous = null;
        while (statementAnalyser != null && statementAnalyser.analysisStatus == DONE) {
            previous = statementAnalyser;
            statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            if (statementAnalyser != null) {
                statementAnalyser = statementAnalyser.followReplacements();
            }
        }
        return new PreviousAndFirst(previous, statementAnalyser);
    }

    private boolean checkForPatterns(EvaluationContext evaluationContext) {
        PatternMatcher<StatementAnalyser> patternMatcher = analyserContext.getPatternMatcher();
        MethodInfo methodInfo = myMethodAnalyser.methodInfo;
        return patternMatcher.matchAndReplace(methodInfo, this, evaluationContext);
    }

    private Location getLocation() {
        return statementAnalysis.location();
    }

    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    record SharedState(EvaluationContext evaluationContext,
                       StatementAnalyserResult.Builder builder,
                       StatementAnalysis previous,
                       ForwardAnalysisInfo forwardAnalysisInfo,
                       ConditionManager localConditionManager) {
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

                analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                        .add(CHECK_UNREACHABLE_STATEMENT, this::checkUnreachableStatement)
                        .add(INITIALISE_OR_UPDATE_VARIABLES, this::initialiseOrUpdateVariables)
                        .add(ANALYSE_TYPES_IN_STATEMENT, this::analyseTypesInStatement)
                        .add(EVALUATION_OF_MAIN_EXPRESSION, this::evaluationOfMainExpression)
                        .add(SUB_BLOCKS, this::subBlocks)
                        .add(SET_BLOCK_EXECUTION, sharedState -> statementAnalysis.flowData
                                .setBlockExecution(sharedState.forwardAnalysisInfo.execution()))
                        .add(ANALYSE_INTERRUPTS_FLOW, sharedState -> statementAnalysis.flowData
                                .analyseInterruptsFlow(this, previous))
                        .add(FREEZE_ASSIGNMENT_IN_BLOCK, this::freezeAssignmentInBlock)
                        .add(CHECK_NOT_NULL_ESCAPES_AND_PRECONDITIONS, this::checkNotNullEscapesAndPreconditions)
                        .add(ANALYSE_METHOD_LEVEL_DATA, sharedState -> statementAnalysis.methodLevelData.analyse(sharedState, statementAnalysis,
                                previous == null ? null : previous.methodLevelData,
                                previous == null ? null : previous.index,
                                statementAnalysis.stateData))
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
                localConditionManager = makeLocalConditionManager(previous, forwardAnalysisInfo.conditionManager().condition(),
                        forwardAnalysisInfo.switchSelectorIsDelayed());
            }

            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, localConditionManager, closure);
            SharedState sharedState = new SharedState(evaluationContext, builder, previous, forwardAnalysisInfo, localConditionManager);
            AnalysisStatus overallStatus = analyserComponents.run(sharedState);

            StatementAnalyserResult result = sharedState.builder()
                    .addTypeAnalysers(localAnalysers.getOrDefault(List.of())) // unreachable statement...
                    .addMessages(statementAnalysis.messageStream())
                    .setAnalysisStatus(overallStatus)
                    .combineAnalysisStatus(wasReplacement
                            ? new ProgressWrapper(new CausesOfDelay.SimpleSet(getLocation(), CauseOfDelay.Cause.REPLACEMENT))
                            : DONE)
                    .build();
            analysisStatus = result.analysisStatus();

            visitStatementVisitors(statementAnalysis.index, result, sharedState);

            log(ANALYSER, "Returning from statement {} of {} with analysis status {}", statementAnalysis.index,
                    myMethodAnalyser.methodInfo.name, analysisStatus);
            return result;
        } catch (Throwable rte) {
            LOGGER.warn("Caught exception while analysing statement {} of {}", index(), myMethodAnalyser.methodInfo.fullyQualifiedName());
            throw rte;
        }
    }

    /*
    three aspects:
    1- precondition, comes via MethodLevelData; is cumulative
    2- state, comes via conditionManagerForNextStatement
    3- condition, can be updated in case of SwitchOldStyle
     */
    private ConditionManager makeLocalConditionManager(StatementAnalysis previous,
                                                       Expression condition,
                                                       CausesOfDelay conditionIsDelayed) {
        Precondition combinedPrecondition;
        CausesOfDelay combinedPreconditionIsDelayed;
        if (previous.methodLevelData.combinedPrecondition.isFinal()) {
            combinedPrecondition = previous.methodLevelData.combinedPrecondition.get();
            combinedPreconditionIsDelayed = CausesOfDelay.EMPTY;
        } else {
            combinedPreconditionIsDelayed = previous.methodLevelData.combinedPreconditionIsDelayedSet();
            combinedPrecondition = Precondition.empty(statementAnalysis.primitives);
        }

        ConditionManager previousCm = previous.stateData.conditionManagerForNextStatement.get();
        // can be null in case the statement is unreachable
        if (previousCm == null) {
            return ConditionManager.impossibleConditionManager(statementAnalysis.primitives);
        }
        if (previousCm.condition().equals(condition)) {
            return previousCm.withPrecondition(combinedPrecondition, combinedPreconditionIsDelayed);
        }
        // swap condition for the one from forwardAnalysisInfo
        return new ConditionManager(condition, conditionIsDelayed, previousCm.state(),
                previousCm.stateIsDelayed(), combinedPrecondition,
                combinedPreconditionIsDelayed, previousCm.parent());
    }

    private AnalysisStatus freezeAssignmentInBlock(SharedState sharedState) {
        if (statementAnalysis.statement instanceof LoopStatement) {
            statementAnalysis.localVariablesAssignedInThisLoop.freeze();
        }
        return DONE; // only in first iteration
    }

    // in a separate task, so that it can be skipped when the statement is unreachable
    private AnalysisStatus initialiseOrUpdateVariables(SharedState sharedState) {
        if (sharedState.evaluationContext.getIteration() == 0) {
            statementAnalysis.initIteration0(sharedState.evaluationContext, myMethodAnalyser.methodInfo, sharedState.previous);
        } else {
            statementAnalysis.initIteration1Plus(sharedState.evaluationContext, sharedState.previous);
        }

        if (statementAnalysis.flowData.initialTimeNotYetSet()) {
            int time;
            if (sharedState.previous != null) {
                time = sharedState.previous.flowData.getTimeAfterSubBlocks();
            } else if (statementAnalysis.parent != null) {
                time = statementAnalysis.parent.flowData.getTimeAfterEvaluation();
            } else {
                time = 0; // start
            }
            statementAnalysis.flowData.setInitialTime(time, index());
        }
        return RUN_AGAIN;
    }

    private void visitStatementVisitors(String statementId, StatementAnalyserResult result, SharedState sharedState) {
        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                analyserContext.getConfiguration().debugConfiguration().statementAnalyserVariableVisitors()) {
            statementAnalysis.variables.stream()
                    .map(Map.Entry::getValue)
                    .forEach(variableInfoContainer -> statementAnalyserVariableVisitor.visit(
                            new StatementAnalyserVariableVisitor.Data(
                                    sharedState.evaluationContext.getIteration(),
                                    sharedState.evaluationContext,
                                    myMethodAnalyser.methodInfo,
                                    statementId,
                                    variableInfoContainer.current().name(),
                                    variableInfoContainer.current().variable(),
                                    variableInfoContainer.current().getValue(),
                                    variableInfoContainer.current().getProperties(),
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration().statementAnalyserVisitors()) {
            ConditionManager cm = statementAnalysis.stateData.conditionManagerForNextStatement.get();
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            result,
                            sharedState.evaluationContext.getIteration(),
                            sharedState.evaluationContext,
                            myMethodAnalyser.methodInfo,
                            statementAnalysis,
                            statementAnalysis.index,
                            cm == null ? null : cm.condition(),
                            cm == null ? null : cm.state(),
                            cm == null ? null : cm.absoluteState(sharedState.evaluationContext),
                            cm,
                            sharedState.localConditionManager(),
                            analyserComponents.getStatusesAsMap()));
        }
    }

    private AnalysisStatus analyseTypesInStatement(SharedState sharedState) {
        if (!localAnalysers.isSet()) {
            Stream<TypeInfo> locallyDefinedTypes = Stream.concat(statementAnalysis.statement.getStructure().findTypeDefinedInStatement().stream(),
                    statement() instanceof LocalClassDeclaration lcd ? Stream.of(lcd.typeInfo) : Stream.empty());
            List<PrimaryTypeAnalyser> analysers = locallyDefinedTypes
                    // those without a sorted type are already in the current primary type's sorted type!!
                    .filter(typeInfo -> typeInfo.typeResolution.get().sortedType() != null)
                    .map(typeInfo -> {
                        SortedType sortedType = typeInfo.typeResolution.get().sortedType();
                        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyser(analyserContext,
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
                navigationData.next.get().get().analyserContext.addAll(analyserContext);
                navigationData.blocks.get().forEach(opt -> opt.ifPresent(sa -> sa.analyserContext.addAll(analyserContext)));
            }
        }

        AnalysisStatus analysisStatus = DONE;
        for (PrimaryTypeAnalyser analyser : localAnalysers.get()) {
            log(ANALYSER, "------- Starting local analyser {} ------", analyser.getName());
            AnalysisStatus lambdaStatus = analyser.analyse(sharedState.evaluationContext.getIteration(), sharedState.evaluationContext);
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
    private AnalysisStatus checkUnreachableStatement(SharedState sharedState) {
        // if the previous statement was not reachable, we won't reach this one either
        if (sharedState.previous != null
                && sharedState.previous.flowData.getGuaranteedToBeReachedInMethod().equals(FlowData.NEVER)) {
            statementAnalysis.flowData.setGuaranteedToBeReached(FlowData.NEVER);
            return DONE_ALL;
        }
        DV execution = sharedState.forwardAnalysisInfo().execution();
        Expression state = sharedState.localConditionManager.state();
        CausesOfDelay localConditionManagerIsDelayed = sharedState.localConditionManager.causesOfDelay();
        AnalysisStatus analysisStatus = statementAnalysis.flowData.computeGuaranteedToBeReachedReturnUnreachable
                (sharedState.previous, execution, state, state.causesOfDelay(), localConditionManagerIsDelayed);
        if (analysisStatus == DONE_ALL) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.UNREACHABLE_STATEMENT));
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return analysisStatus;
    }

    private DV isEscapeAlwaysExecutedInCurrentBlock() {
        if (!statementAnalysis.flowData.interruptsFlowIsSet()) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return statementAnalysis.flowData.interruptStatus().causesOfDelay();
        }
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return Level.fromBoolDv(statementAnalysis.flowData.getGuaranteedToBeReachedInCurrentBlock().equals(FlowData.ALWAYS));
        }
        return Level.FALSE_DV;
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
    The main loop calls the apply method with the results of an evaluation.
    For every variable, a number of steps are executed:
    - it is created, some local copies are created, and it is prepared for EVAL level
    - values, linked variables are set

    Finally, general modifications are carried out
     */
    private ApplyStatusAndEnnStatus apply(SharedState sharedState, EvaluationResult evaluationResult) {
        CausesOfDelay delay = evaluationResult.causes();

        if (evaluationResult.addCircularCall()) {
            statementAnalysis.methodLevelData.addCircularCall();
        }

        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        // the first part is per variable
        // order is important because we need to re-map statically assigned variables
        // but first, we need to ensure that all variables exist, independent of the later ordering

        // make a copy because we might add a variable when linking the local loop copy
        evaluationResult.changeData().forEach((v, cd) ->
                ensureVariables(sharedState.evaluationContext, v, cd, evaluationResult.statementTime()));
        Map<Variable, VariableInfoContainer> existingVariablesNotVisited = statementAnalysis.variableEntryStream(EVALUATION)
                .collect(Collectors.toMap(e -> e.getValue().current().variable(), Map.Entry::getValue,
                        (v1, v2) -> v2, HashMap::new));

        List<Map.Entry<Variable, EvaluationResult.ChangeData>> sortedEntries =
                new ArrayList<>(evaluationResult.changeData().entrySet());
        sortedEntries.sort((e1, e2) -> {
            // return variables at the end
            if (e1.getKey() instanceof ReturnVariable) return 1;
            if (e2.getKey() instanceof ReturnVariable) return -1;
            // then assignments
            if (e1.getValue().markAssignment() && !e2.getValue().markAssignment()) return -1;
            if (e2.getValue().markAssignment() && !e1.getValue().markAssignment()) return 1;
            // then the "mentions" (markRead, change linked variables, etc.)
            return e1.getKey().fullyQualifiedName().compareTo(e2.getKey().fullyQualifiedName());
        });

        for (Map.Entry<Variable, EvaluationResult.ChangeData> entry : sortedEntries) {
            Variable variable = entry.getKey();
            existingVariablesNotVisited.remove(variable);
            EvaluationResult.ChangeData changeData = entry.getValue();

            // we're now guaranteed to find the variable
            VariableInfoContainer vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
            VariableInfo vi = vic.best(EVALUATION);
            VariableInfo vi1 = vic.getPreviousOrInitial();
            assert vi != vi1 : "There should already be a different EVALUATION object";

            if (changeData.markAssignment()) {
                if (conditionsForOverwritingPreviousAssignment(vi1, vic, changeData,
                        sharedState.localConditionManager, sharedState.evaluationContext)) {
                    statementAnalysis.ensure(Message.newMessage(getLocation(),
                            Message.Label.OVERWRITING_PREVIOUS_ASSIGNMENT, "variable " + variable.simpleName()));
                }

                Expression bestValue = bestValue(changeData, vi1);
                Expression valueToWrite = maybeValueNeedsState(sharedState, vic, variable, bestValue);

                log(ANALYSER, "Write value {} to variable {}", valueToWrite, variable.fullyQualifiedName());
                // first do the properties that come with the value; later, we'll write the ones in changeData
                Map<Property, DV> valueProperties = sharedState.evaluationContext
                        .getValueProperties(valueToWrite, variable instanceof ReturnVariable);
                CausesOfDelay valuePropertiesIsDelayed = valueProperties.values().stream()
                        .map(DV::causesOfDelay)
                        .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

                boolean valueToWriteIsDelayed = valueToWrite.isDelayed();
                Expression valueToWritePossiblyDelayed;
                if (valueToWriteIsDelayed) {
                    valueToWritePossiblyDelayed = valueToWrite;
                } else if (valuePropertiesIsDelayed.isDelayed()) {
                    valueToWritePossiblyDelayed = valueToWrite.createDelayedValue(sharedState.evaluationContext,
                            valuePropertiesIsDelayed);
                } else {
                    // no delays!
                    valueToWritePossiblyDelayed = valueToWrite;
                }

                Map<Property, DV> merged = mergeAssignment(variable, valueProperties, changeData.properties(), groupPropertyValues);
                // LVs start empty, the changeData.linkedVariables will be added later
                vic.setValue(valueToWritePossiblyDelayed, LinkedVariables.EMPTY, merged, false);

                if (vic.variableNature().isLocalVariableInLoopDefinedOutside()) {
                    VariableInfoContainer local = addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
                    // assign the value of the assignment to the local copy created
                    if (local != null) {
                        Variable localVar = local.current().variable();
                        // calculation needs to be done again, this time with the local copy rather than the original
                        // (so that we can replace the local one with instance)
                        Expression valueToWrite2 = maybeValueNeedsState(sharedState, vic, localVar, bestValue);
                        Expression valueToWriteCorrected;
                        IsVariableExpression ive;
                        if ((ive = valueToWrite2.asInstanceOf(IsVariableExpression.class)) != null &&
                                ive.variable().equals(localVar)) {
                            // not allowing j$2 to be assigned to j$2; assign to initial instead
                            valueToWriteCorrected = local.getPreviousOrInitial().getValue();
                        } else {
                            valueToWriteCorrected = valueToWrite2;
                        }
                        log(ANALYSER, "Write value {} to local copy variable {}", valueToWriteCorrected, localVar.fullyQualifiedName());
                        Map<Property, DV> merged2 = mergeAssignment(localVar, valueProperties,
                                changeData.properties(), groupPropertyValues);

                        LinkedVariables linkedToMain = LinkedVariables.of(variable, LinkedVariables.STATICALLY_ASSIGNED_DV);
                        local.ensureEvaluation(getLocation(),
                                new AssignmentIds(index() + EVALUATION), VariableInfoContainer.NOT_YET_READ,
                                statementAnalysis.statementTime(EVALUATION), Set.of());
                        local.setValue(valueToWriteCorrected, linkedToMain, merged2, false);
                        existingVariablesNotVisited.remove(localVar);
                    }
                }
                if (variable instanceof FieldReference fr) {
                    FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo);
                    DV effFinal = fieldAnalysis.getProperty(Property.FINAL);
                    if (effFinal.isDelayed()) {
                        log(DELAYED, "Delaying statement {}, assignment to field, no idea about @Final", index());
                        delay = delay.merge(effFinal.causesOfDelay());
                    }
                }
            } else if (!assignmentToNonCopy(vic, evaluationResult)) {
                if (changeData.value() != null) {
                    // a modifying method caused an updated instance value

                    Map<Property, DV> merged = mergePreviousAndChange(sharedState.evaluationContext,
                            variable, vi1.getProperties(),
                            changeData.properties(), groupPropertyValues, true);
                    vic.setValue(changeData.value(), vi1.getLinkedVariables(), merged, false);
                } else {
                    if (variable instanceof This || !evaluationResult.causes().isDelayed()) {
                        // TODO we used to check for "haveDelaysCausedByMethodCalls"; now assuming ALL delays
                        // we're not assigning (and there is no change in instance because of a modifying method)
                        // only then we copy from INIT to EVAL
                        // so we must integrate set properties
                        Map<Property, DV> merged = mergePreviousAndChange(
                                sharedState.evaluationContext,
                                variable, vi1.getProperties(),
                                changeData.properties(), groupPropertyValues, true);
                        vic.setValue(vi1.getValue(), vi1.getLinkedVariables(), merged, false);
                    } else {
                        // delayed situation; do not copy the value properties
                        Map<Property, DV> merged = mergePreviousAndChange(
                                sharedState.evaluationContext,
                                variable, vi1.getProperties(),
                                changeData.properties(), groupPropertyValues, false);
                        merged.forEach((k, v) -> vic.setProperty(k, v, false, EVALUATION));
                    }
                }
            }
            if (vi.isDelayed()) {
                log(DELAYED, "Apply of {}, {} is delayed because of unknown value for {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                delay = delay.merge(vi.getValue().causesOfDelay());
            } else if (changeData.delays().isDelayed()) {
                log(DELAYED, "Apply of {}, {} is delayed because of delay in method call on {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                delay = delay.merge(changeData.delays());
            }
        }

        /*
        The loop variable has been created in the initialisation phase. Evaluation has to wait until
        the expression of the forEach statement has been evaluated. For this reason, we need to handle
        this separately.
         */
        if (statement() instanceof ForEachStatement) {
            Variable loopVar = obtainLoopVar();
            evaluationOfForEachVariable(loopVar, evaluationResult.getExpression(),
                    evaluationResult.causes(), sharedState.evaluationContext);
        }

        // OutputBuilderSimplified 2, statement 0 in "go", shows why we may want to copy from prev -> eval
        // This should not happen when due to an assignment, the loop copy also gets a new value. See loop above,
        // where we remove the loop copy from existingVarsNotVisited. Example in Loops_2, 9, 10
        for (Map.Entry<Variable, VariableInfoContainer> e : existingVariablesNotVisited.entrySet()) {
            VariableInfoContainer vic = e.getValue();
            if (vic.hasEvaluation()) {
                /* so we have an evaluation, but we did not get the chance to copy from previous into evaluation.
                 (this happened because an evaluation was ensured for some other reason than the pure
                  evaluation of the expression).
                At least for IMMUTABLE we need to copy the value from previous into evaluation, because
                the next statement will copy it from there
                 */
                VariableInfo prev = vic.getPreviousOrInitial();
                DV immPrev = prev.getProperty(IMMUTABLE);
                if (immPrev.isDone()) {
                    vic.setProperty(IMMUTABLE, immPrev, EVALUATION);
                }
            }
        }

        // the second one is across clusters of variables

        addToMap(groupPropertyValues, CONTEXT_NOT_NULL, x -> x.parameterizedType().defaultNotNull(), true);
        addToMap(groupPropertyValues, EXTERNAL_NOT_NULL, x ->
                new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(x, getLocation(),
                        CauseOfDelay.Cause.EXTERNAL_NOT_NULL)), false);
        addToMap(groupPropertyValues, EXTERNAL_IMMUTABLE, x -> x.parameterizedType().defaultImmutable(analyserContext, false), false);
        addToMap(groupPropertyValues, CONTEXT_IMMUTABLE, x -> MultiLevel.NOT_INVOLVED_DV, true);
        addToMap(groupPropertyValues, CONTEXT_MODIFIED, x -> Level.FALSE_DV, true);

        if (statement() instanceof ForEachStatement) {
            Variable loopVar = obtainLoopVar();
            potentiallyUpgradeCnnOfLocalLoopVariableAndCopy(sharedState.evaluationContext,
                    groupPropertyValues.getMap(EXTERNAL_NOT_NULL),
                    groupPropertyValues.getMap(CONTEXT_NOT_NULL), evaluationResult.value(),
                    evaluationResult.causes(), loopVar);
        }

        Function<Variable, LinkedVariables> linkedVariablesFromChangeData = v -> {
            EvaluationResult.ChangeData changeData = evaluationResult.changeData().get(v);
            return changeData == null ? LinkedVariables.EMPTY : changeData.linkedVariables();
        };
        Set<Variable> reassigned = evaluationResult.changeData().entrySet().stream()
                .filter(e -> e.getValue().markAssignment()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(statementAnalysis, EVALUATION,
                v -> false,
                reassigned,
                linkedVariablesFromChangeData,
                sharedState.evaluationContext);
        computeLinkedVariables.writeLinkedVariables();

        // 1
        CausesOfDelay cnnStatus = computeLinkedVariables.write(CONTEXT_NOT_NULL,
                groupPropertyValues.getMap(CONTEXT_NOT_NULL));
        delay = delay.merge(cnnStatus);

        // 2
        CausesOfDelay ennStatus = computeLinkedVariables.write(EXTERNAL_NOT_NULL, groupPropertyValues.getMap(EXTERNAL_NOT_NULL));
        potentiallyRaiseErrorsOnNotNullInContext(evaluationResult.changeData());

        // 3
        CausesOfDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        // 4
        CausesOfDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE, groupPropertyValues.getMap(CONTEXT_IMMUTABLE));

        // 5
        importContextModifiedValuesForThisFromSubTypes(groupPropertyValues.getMap(CONTEXT_MODIFIED));
        CausesOfDelay cmStatus = computeLinkedVariables.write(CONTEXT_MODIFIED,
                groupPropertyValues.getMap(CONTEXT_MODIFIED));
        delay = delay.merge(cmStatus);

        // odds and ends

        if (evaluationResult.causes().isDone()) {
            evaluationResult.messages().getMessageStream().forEach(statementAnalysis::ensure);
        }

        // not checking on DONE anymore because any delay will also have crept into the precondition itself??
        if (evaluationResult.precondition() != null) {
            Expression preconditionExpression = evaluationResult.precondition().expression();
            if (preconditionExpression.isBoolValueFalse()) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.INCOMPATIBLE_PRECONDITION));
                statementAnalysis.stateData.setPreconditionAllowEquals(Precondition.empty(statementAnalysis.primitives));
            } else {
                Expression translated = sharedState.evaluationContext
                        .acceptAndTranslatePrecondition(evaluationResult.precondition().expression());
                if (translated != null) {
                    Precondition pc = new Precondition(translated, evaluationResult.precondition().causes());
                    statementAnalysis.stateData.setPrecondition(pc, preconditionExpression.isDelayed());
                }
                if (preconditionExpression.isDelayed()) {
                    log(DELAYED, "Apply of {}, {} is delayed because of precondition",
                            index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                    delay = delay.merge(preconditionExpression.causesOfDelay());
                } else {
                    checkPreconditionCompatibilityWithConditionManager(sharedState.evaluationContext,
                            preconditionExpression,
                            sharedState.localConditionManager);
                }
            }
        } else if (!statementAnalysis.stateData.preconditionIsFinal()) {
            // undo a potential previous delay, so that no precondition is seen to be present
            statementAnalysis.stateData.setPrecondition(null, true);
        }

        // debugging...

        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration().evaluationResultVisitors()) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(),
                    myMethodAnalyser.methodInfo, statementAnalysis.index, statementAnalysis, evaluationResult));
        }

        return new ApplyStatusAndEnnStatus(delay, ennStatus.merge(extImmStatus).merge(cImmStatus));
    }

    private Variable obtainLoopVar() {
        Structure structure = statementAnalysis.statement.getStructure();
        LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
        return lvc.localVariableReference;
    }

    private void evaluationOfForEachVariable(Variable loopVar,
                                             Expression evaluatedIterable,
                                             CausesOfDelay someValueWasDelayed,
                                             EvaluationContext evaluationContext) {
        LinkedVariables linked = evaluatedIterable.linkedVariables(evaluationContext);
        VariableInfoContainer vic = statementAnalysis.findForWriting(loopVar);
        vic.ensureEvaluation(getLocation(), new AssignmentIds(index() + EVALUATION), VariableInfoContainer.NOT_YET_READ,
                statementAnalysis.statementTime(EVALUATION), Set.of());
        Map<Property, DV> valueProperties = Map.of(); // FIXME
        Expression instance = Instance.forLoopVariable(index(), loopVar, valueProperties);
        vic.setValue(instance, LinkedVariables.EMPTY, Map.of(), false);
        vic.setLinkedVariables(linked, EVALUATION);
    }

    private boolean assignmentToNonCopy(VariableInfoContainer vic, EvaluationResult evaluationResult) {
        if (vic.variableNature() instanceof VariableNature.CopyOfVariableInLoop) {
            Variable original = vic.variableNature().localCopyOf();
            EvaluationResult.ChangeData changeData = evaluationResult.changeData().get(original);
            return changeData != null && changeData.markAssignment();
        }
        return false;
    }

    private boolean conditionsForOverwritingPreviousAssignment(VariableInfo vi1,
                                                               VariableInfoContainer vic,
                                                               EvaluationResult.ChangeData changeData,
                                                               ConditionManager conditionManager,
                                                               EvaluationContext evaluationContext) {
        if (vi1.isAssigned() && !vi1.isRead() && changeData.markAssignment() &&
                changeData.readAtStatementTime().isEmpty() && !(vi1.variable() instanceof ReturnVariable)) {
            String index = vi1.getAssignmentIds().getLatestAssignmentIndex();
            StatementAnalysis sa = myMethodAnalyser.findStatementAnalyser(index).statementAnalysis;
            if (sa.stateData.conditionManagerForNextStatement.isVariable()) {
                return false; // we'll be back
            }
            ConditionManager atAssignment = sa.stateData.conditionManagerForNextStatement.get();
            Expression myAbsoluteState = conditionManager.absoluteState(evaluationContext);
            Expression initialAbsoluteState = atAssignment.absoluteState(evaluationContext);
            if (!initialAbsoluteState.equals(myAbsoluteState)) return false;
            // now check if we're in loop block, and there was an assignment outside
            // this loop block will not have an effect on the absolute state (See Loops_2, Loops_13)
            VariableInfoContainer initialVic = sa.variables.get(vi1.variable().fullyQualifiedName());
            // do raise an error when the assignment is in the loop condition
            return initialVic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop ||
                    !(vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop loop) ||
                    index().equals(loop.statementIndex());
        }
        return false;
    }

    private void importContextModifiedValuesForThisFromSubTypes(Map<Variable, DV> map) {
        DV bestInSub = localAnalysers.get().stream()
                .flatMap(PrimaryTypeAnalyser::methodAnalyserStream)
                .filter(ma -> ma instanceof ComputingMethodAnalyser)
                .map(ma -> ((ComputingMethodAnalyser) ma).getThisAsVariable())
                .filter(Objects::nonNull)
                .map(variableInfo -> variableInfo.getProperty(CONTEXT_MODIFIED))
                .reduce(DV.MIN_INT_DV, DV::max);
        if (bestInSub != DV.MIN_INT_DV) {
            Variable thisVar = new This(analyserContext, myMethodAnalyser.methodInfo.typeInfo);
            DV myValue = map.getOrDefault(thisVar, null);
            DV merged = myValue == null ? bestInSub : myValue.max(bestInSub);
            map.put(thisVar, merged);
        }
    }

    private void checkPreconditionCompatibilityWithConditionManager(EvaluationContext evaluationContext,
                                                                    Expression preconditionExpression,
                                                                    ConditionManager localConditionManager) {
        Expression result = localConditionManager.evaluate(evaluationContext, preconditionExpression);
        if (result.isBoolValueFalse()) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.INCOMPATIBLE_PRECONDITION));
        }
    }

    /*
    not directly in EvaluationResult, because we could have ENN = 0 on a local field copy, and ENN = 1 on the field itself.
    that is only "leveled out" using the dependency graph of static assignments

    the presence of the IN_NOT_NULL_CONTEXT flag implies that CNN was 0
     */
    private void potentiallyRaiseErrorsOnNotNullInContext(Map<Variable, EvaluationResult.ChangeData> changeDataMap) {
        for (Map.Entry<Variable, EvaluationResult.ChangeData> e : changeDataMap.entrySet()) {
            Variable variable = e.getKey();
            EvaluationResult.ChangeData changeData = e.getValue();
            if (changeData.getProperty(IN_NOT_NULL_CONTEXT).valueIsTrue()) {
                VariableInfoContainer vic = statementAnalysis.findOrNull(variable);
                VariableInfo vi = vic.best(EVALUATION);
                if (vi != null && !(vi.variable() instanceof ParameterInfo)) {
                    DV externalNotNull = vi.getProperty(Property.EXTERNAL_NOT_NULL);
                    DV notNullExpression = vi.getProperty(NOT_NULL_EXPRESSION);
                    if (vi.valueIsSet() && externalNotNull.equals(MultiLevel.NULLABLE_DV)
                            && notNullExpression.equals(MultiLevel.NULLABLE_DV)) {
                        Variable primary = Objects.requireNonNullElse(vic.variableNature().localCopyOf(), variable);
                        statementAnalysis.ensure(Message.newMessage(getLocation(),
                                Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION,
                                "Variable: " + primary.simpleName()));
                    }
                }
            }
            if (changeData.getProperty(CANDIDATE_FOR_NULL_PTR_WARNING).valueIsTrue()) {
                if (!statementAnalysis.candidateVariablesForNullPtrWarning.contains(variable)) {
                    statementAnalysis.candidateVariablesForNullPtrWarning.add(variable);
                }
            }
        }
    }

    private void potentiallyRaiseNullPointerWarningENN() {
        statementAnalysis.candidateVariablesForNullPtrWarning.stream().forEach(variable -> {
            VariableInfo vi = statementAnalysis.findOrNull(variable, VariableInfoContainer.Level.MERGE);
            DV cnn = vi.getProperty(CONTEXT_NOT_NULL); // after merge, CNN should still be too low
            if (cnn.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                statementAnalysis.ensure(Message.newMessage(getLocation(),
                        Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN,
                        "Variable: " + variable.fullyQualifiedName()));
            }
        });
    }

    private void potentiallyUpgradeCnnOfLocalLoopVariableAndCopy(EvaluationContext evaluationContext,
                                                                 Map<Variable, DV> externalNotNull,
                                                                 Map<Variable, DV> contextNotNull,
                                                                 Expression value,
                                                                 CausesOfDelay delays,
                                                                 Variable loopVar) {
        assert contextNotNull.containsKey(loopVar); // must be present!
        if (delays.isDelayed()) {
            // we want to avoid a particular value on EVAL for the loop variable
            contextNotNull.put(loopVar, delays);
            externalNotNull.put(loopVar, MultiLevel.NOT_INVOLVED_DV);
        } else {
            DV nne = evaluationContext.getProperty(value, NOT_NULL_EXPRESSION, false, false);
            boolean variableNotNull = nne.ge(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);
            if (variableNotNull) {
                DV oneLevelLess = MultiLevel.composeOneLevelLessNotNull(nne);

                contextNotNull.put(loopVar, oneLevelLess);
                externalNotNull.put(loopVar, MultiLevel.NOT_INVOLVED_DV);

                LocalVariableReference copyVar = statementAnalysis.createLocalLoopCopy(loopVar, index());
                if (contextNotNull.containsKey(copyVar)) {
                    // can be delayed to the next iteration
                    contextNotNull.put(copyVar, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                }
            }
        }
    }

    private void addToMap(GroupPropertyValues groupPropertyValues,
                          Property property,
                          Function<Variable, DV> falseValue,
                          boolean complainDelay0) {
        Map<Variable, DV> map = groupPropertyValues.getMap(property);
        statementAnalysis.variables.stream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            if (!map.containsKey(vi1.variable())) { // variables that don't occur in contextNotNull
                DV prev = vi1.getProperty(property);
                if (prev.isDone()) {
                    if (vic.hasEvaluation()) {
                        VariableInfo vi = vic.best(EVALUATION);
                        DV eval = vi.getProperty(property);
                        if (eval.isDelayed()) {
                            map.put(vi.variable(), prev.maxIgnoreDelay(falseValue.apply(vi.variable())));
                        } else {
                            map.put(vi.variable(), eval);
                        }
                    } else {
                        map.put(vi1.variable(), prev);
                    }
                } else {
                    map.put(vi1.variable(), prev);
                    if (complainDelay0 && "0".equals(index())) {
                        throw new UnsupportedOperationException(
                                "Impossible, all variables start with non-delay: " + vi1.variable().fullyQualifiedName()
                                        + ", prop " + property);
                    }
                }
            }
        });
    }

    /*
    Variable is target of assignment. In terms of CNN/CM it should be neutral (rather than delayed), as its current value
    is not of relevance.

    There is no overlap between valueProps and variableProps
     */
    private Map<Property, DV> mergeAssignment(Variable variable,
                                              Map<Property, DV> valueProps,
                                              Map<Property, DV> changeData,
                                              GroupPropertyValues groupPropertyValues) {
        Map<Property, DV> res = new HashMap<>(valueProps);
        res.putAll(changeData);

        // reasoning: only relevant when assigning to a field, this assignment is in StaticallyAssignedVars, so
        // the field's value is taken anyway
        groupPropertyValues.set(EXTERNAL_NOT_NULL, variable, MultiLevel.NOT_INVOLVED_DV);
        groupPropertyValues.set(EXTERNAL_IMMUTABLE, variable, MultiLevel.NOT_INVOLVED_DV);

        DV cnn = res.remove(CONTEXT_NOT_NULL);
        groupPropertyValues.set(CONTEXT_NOT_NULL, variable, cnn == null ? variable.parameterizedType().defaultNotNull() : cnn);
        DV cm = res.remove(CONTEXT_MODIFIED);
        groupPropertyValues.set(CONTEXT_MODIFIED, variable, cm == null ? Level.FALSE_DV : cm);
        DV cImm = res.remove(CONTEXT_IMMUTABLE);
        groupPropertyValues.set(CONTEXT_IMMUTABLE, variable, cImm == null ? MultiLevel.MUTABLE_DV : cImm);

        return res;
    }

    private Map<Property, DV> mergePreviousAndChange(
            EvaluationContext evaluationContext,
            Variable variable,
            Map<Property, DV> previous,
            Map<Property, DV> changeData,
            GroupPropertyValues groupPropertyValues,
            boolean allowValueProperties) {
        Set<Property> both = new HashSet<>(previous.keySet());
        both.addAll(changeData.keySet());
        both.addAll(GroupPropertyValues.PROPERTIES);
        Map<Property, DV> res = new HashMap<>(changeData);

        both.forEach(k -> {
            DV prev = previous.getOrDefault(k, k.valueWhenAbsent());
            DV change = changeData.getOrDefault(k, k.valueWhenAbsent());
            if (GroupPropertyValues.PROPERTIES.contains(k)) {
                DV value = switch (k) {
                    case EXTERNAL_IMMUTABLE, CONTEXT_MODIFIED, EXTERNAL_NOT_NULL -> prev.max(change);
                    case CONTEXT_IMMUTABLE -> evaluationContext.isMyself(variable) ? MultiLevel.MUTABLE_DV : prev.max(change);
                    case CONTEXT_NOT_NULL -> variable.parameterizedType().defaultNotNull().max(prev).max(change);
                    default -> throw new UnsupportedOperationException();
                };
                groupPropertyValues.set(k, variable, value);
            } else {
                switch (k) {
                    // value properties are copied from previous, only when the value from previous is copied as well
                    case NOT_NULL_EXPRESSION, CONTAINER, IMMUTABLE, IDENTITY, INDEPENDENT -> {
                        if (allowValueProperties) res.put(k, prev);
                    }
                    // all other properties are copied from change data
                    default -> res.put(k, change);
                }
            }
        });
        res.keySet().removeAll(GroupPropertyValues.PROPERTIES);
        return res;
    }

    /*
    As the first action in 'apply', we need to ensure that all variables exist, and have a proper assignmentId and readId.

    We need to do:
    - generally ensure a EVALUATION level for each variable occurring, with correct assignmentId, readId
    - create fields + local copies of variable fields, because they don't exist in the first iteration
    - link the fields to their local copies (or at least, compute these links)

    Local variables, This, Parameters will already exist, minimally in INITIAL level
    Fields (and forms of This (super...)) will not exist in the first iteration; they need creating
     */
    private void ensureVariables(EvaluationContext evaluationContext,
                                 Variable variable,
                                 EvaluationResult.ChangeData changeData,
                                 int newStatementTime) {
        VariableInfoContainer vic;
        if (!statementAnalysis.variables.isSet(variable.fullyQualifiedName())) {
            assert variable.variableNature() instanceof VariableNature.NormalLocalVariable :
                    "Encountering variable " + variable.fullyQualifiedName() + " of nature " + variable.variableNature();
            vic = statementAnalysis.createVariable(evaluationContext, variable,
                    statementAnalysis.flowData.getInitialTime(), VariableNature.normal(variable, index()));
        } else {
            vic = statementAnalysis.variables.get(variable.fullyQualifiedName());

        }
        String id = index() + EVALUATION;
        VariableInfo initial = vic.getPreviousOrInitial();
        AssignmentIds assignmentIds = changeData.markAssignment() ? new AssignmentIds(id) : initial.getAssignmentIds();
        // we do not set readId to the empty set when markAssignment... we'd rather keep the old value
        // we will compare the recency anyway

        String readId = changeData.readAtStatementTime().isEmpty() ? initial.getReadId() : id;
        int statementTime = statementAnalysis.statementTimeForVariable(analyserContext, variable, newStatementTime);

        vic.ensureEvaluation(getLocation(), assignmentIds, readId, statementTime, changeData.readAtStatementTime());
        if (evaluationContext.isMyself(variable)) vic.setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV, EVALUATION);
    }

    /*
    See among others Loops_1: when a variable is assigned in a loop, it is possible that interrupts (break, etc.) have
    caused a state. If this variable is defined outside the loop, it'll have to have a value when coming out of the loop.
    The added state helps to avoid unconditional assignments.
    In i=3; while(c) { if(x) break; i=5; }, we return x?3:5; x will most likely be dependent on the loop and, be
    turned into some generic integer

    In i=3; while(c) { i=5; if(x) break; }, we return c?5:3, as soon as c has a value

    Q: what is the best place for this piece of code? EvalResult?? This here seems too late
     */
    private Expression maybeValueNeedsState(SharedState sharedState,
                                            VariableInfoContainer vic,
                                            Variable variable,
                                            Expression value) {
        boolean valueIsDelayed = value.isDelayed();
        if (valueIsDelayed || !(vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop)) {
            // not applicable
            return value;
        }
        // variable defined outside loop, now in loop, not delayed

        ConditionManager localConditionManager = sharedState.localConditionManager;

        if (!localConditionManager.state().isBoolValueTrue()) {
            Expression state = localConditionManager.state();

            ForwardEvaluationInfo fwd = new ForwardEvaluationInfo(Map.of(), true, variable);
            // do not take vi1 itself, but "the" local copy of the variable
            Expression valueOfVariablePreAssignment = sharedState.evaluationContext.currentValue(variable,
                    statementAnalysis.statementTime(VariableInfoContainer.Level.INITIAL), fwd);

            InlineConditional inlineConditional = new InlineConditional(Identifier.generate(),
                    analyserContext, state, value, valueOfVariablePreAssignment);
            return inlineConditional.optimise(sharedState.evaluationContext.dropConditionManager());
        }
        return value;
    }

    /*
     we keep track which local variables are assigned in a loop

     add this variable name to all parent loop statements until definition of the local variable
     in this way, a version can be introduced to be used before this assignment.

     at the same time, a new local copy has to be created in this statement to be used after the assignment
     */
    private VariableInfoContainer addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName) {
        StatementAnalysis sa = statementAnalysis;
        String loopIndex = null;
        boolean frozen = false;
        while (sa != null) {
            if (!sa.variables.isSet(fullyQualifiedName)) return null;
            VariableInfoContainer localVic = sa.variables.get(fullyQualifiedName);
            if (!localVic.variableNature().isLocalVariableInLoopDefinedOutside()) return null;
            if (sa.statement instanceof LoopStatement) {
                if (!sa.localVariablesAssignedInThisLoop.contains(fullyQualifiedName)) {
                    sa.localVariablesAssignedInThisLoop.add(fullyQualifiedName);
                }
                loopIndex = sa.index;
                frozen = sa.localVariablesAssignedInThisLoop.isFrozen();
                break; // we've found the loop
            }
            sa = sa.parent;
        }
        assert loopIndex != null;
        if (!frozen) return null; // too early to do an assignment
        Variable variable = vic.getPreviousOrInitial().variable();
        Variable loopCopy = statementAnalysis.createLocalLoopCopy(variable, loopIndex);
        // loop copy must exist already!
        return statementAnalysis.variables.get(loopCopy.fullyQualifiedName());
    }

    private Expression bestValue(EvaluationResult.ChangeData valueChangeData, VariableInfo vi1) {
        if (valueChangeData != null && valueChangeData.value() != null) {
            return valueChangeData.value();
        }
        if (vi1 != null) {
            return vi1.getValue();
        }
        return null;
    }

    /*
    Not-null escapes should not contribute to preconditions.
    All the rest should.
     */
    private AnalysisStatus checkNotNullEscapesAndPreconditions(SharedState sharedState) {
        if (statementAnalysis.statement instanceof AssertStatement) return DONE; // is dealt with in subBlocks
        DV escapeAlwaysExecuted = isEscapeAlwaysExecutedInCurrentBlock();
        CausesOfDelay delays = escapeAlwaysExecuted.causesOfDelay()
                .merge(statementAnalysis.stateData.conditionManagerForNextStatement.get().causesOfDelay());
        if (!escapeAlwaysExecuted.valueIsFalse()) {
            Set<Variable> nullVariables = statementAnalysis.stateData.conditionManagerForNextStatement.get()
                    .findIndividualNullInCondition(sharedState.evaluationContext, true);
            for (Variable nullVariable : nullVariables) {
                log(PRECONDITION, "Escape with check not null on {}", nullVariable.fullyQualifiedName());

                ensureContextNotNullForParent(nullVariable, delays, escapeAlwaysExecuted.valueIsTrue());
                if (nullVariable instanceof LocalVariableReference lvr && lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy) {
                    ensureContextNotNullForParent(copy.localCopyOf(), delays, escapeAlwaysExecuted.valueIsTrue());
                }
            }
            if (escapeAlwaysExecuted.valueIsTrue()) {
                // escapeCondition should filter out all != null, == null clauses
                Expression precondition = statementAnalysis.stateData.conditionManagerForNextStatement.get()
                        .precondition(sharedState.evaluationContext);
                CausesOfDelay preconditionIsDelayed = precondition.causesOfDelay().merge(delays);
                Expression translated = sharedState.evaluationContext.acceptAndTranslatePrecondition(precondition);
                if (translated != null) {
                    log(PRECONDITION, "Escape with precondition {}", translated);
                    Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
                    statementAnalysis.stateData.setPrecondition(pc, preconditionIsDelayed.isDelayed());
                    return AnalysisStatus.of(preconditionIsDelayed);
                }
            }

            if (delays.isDelayed()) return delays;
        }
        if (statementAnalysis.stateData.preconditionIsEmpty()) {
            // it could have been set from the assert statement (subBlocks) or apply via a method call
            statementAnalysis.stateData.setPreconditionAllowEquals(Precondition.empty(statementAnalysis.primitives));
        } else if (!statementAnalysis.stateData.preconditionIsFinal()) {
            return statementAnalysis.stateData.getPrecondition().expression().causesOfDelay();
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

    /*
    we create the variable(s), to make sure they exist in INIT level, but defer computation of their value to evaluation.
    In effect, we split int i=3; into int i (INIT); i=3 (EVAL);

    Loop and catch variables are special in that their scope is restricted to the statement and its block.
    We deal with them here, however they are assigned in the structure.

    Explicit constructor invocation uses "updaters" in the structure, but that is essentially level 3 evaluation.

    The for-statement has explicit initialisation and updating. These statements need evaluation, but the actual
    values are only used for independent for-loop analysis (not yet implemented) rather than for assigning real
    values to the loop variable.

    Loop (and catch) variables will be defined in level 2. A special local variable with a $<index> suffix will
    be created to represent a generic loop value.

    The special thing about creating variables at level 2 in a statement is that they are not transferred to the next statement,
    nor are they merged into level 4.
     */
    private List<Expression> initializersAndUpdaters(SharedState sharedState) {
        List<Expression> expressionsToEvaluate = new ArrayList<>();

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e), or for(int i=...), or int i=3, j=4;
        // variable will be set to a NewObject case of a catch

        if (sharedState.forwardAnalysisInfo.catchVariable() != null) {
            // inject a catch(E1 | E2 e) { } exception variable, directly with assigned value, "read"
            LocalVariableCreation catchVariable = sharedState.forwardAnalysisInfo.catchVariable();
            String name = catchVariable.localVariable.name();
            if (!statementAnalysis.variables.isSet(name)) {
                LocalVariableReference lvr = new LocalVariableReference(catchVariable.localVariable);
                VariableInfoContainer vic = VariableInfoContainerImpl.newCatchVariable(getLocation(), lvr, index(),
                        Instance.forCatchOrThis(index(), lvr, analyserContext),
                        lvr.parameterizedType().defaultImmutable(analyserContext, false),
                        statementAnalysis.navigationData.hasSubBlocks());
                statementAnalysis.variables.put(name, vic);
            }
        }

        for (Expression expression : statementAnalysis.statement.getStructure().initialisers()) {
            Expression initialiserToEvaluate;

            if (expression instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr;
                VariableInfoContainer vic;
                String name = lvc.localVariable.name();
                if (!statementAnalysis.variables.isSet(name)) {

                    // create the local (loop) variable

                    lvr = new LocalVariableReference(lvc.localVariable);
                    VariableNature variableNature;
                    if (statement() instanceof LoopStatement) {
                        variableNature = new VariableNature.LoopVariable(index());
                    } else if (statement() instanceof TryStatement) {
                        variableNature = new VariableNature.TryResource(index());
                    } else {
                        variableNature = new VariableNature.NormalLocalVariable(index());
                    }
                    vic = statementAnalysis.createVariable(sharedState.evaluationContext,
                            lvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD, variableNature);
                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.localVariablesAssignedInThisLoop.add(lvr.fullyQualifiedName());
                    }
                } else {
                    vic = statementAnalysis.variables.get(name);
                    lvr = (LocalVariableReference) vic.current().variable();
                }

                // what should we evaluate? catch: assign a value which will be read; for(int i=0;...) --> 0 instead of i=0;
                if (statement() instanceof LoopStatement) {
                    initialiserToEvaluate = lvc.expression;
                    // but, because we don't evaluate the assignment, we need to assign some value to the loop variable
                    // otherwise we'll get delays
                    // especially in the case of forEach, the lvc.expression is empty (e.g., 'String s') anyway
                    // an assignment may be difficult. The value is never used, only local copies are

                    DV defaultImmutable = lvr.parameterizedType().defaultImmutable(analyserContext, false);
                    DV initialNotNull = lvr.parameterizedType().defaultNotNull();
                    Map<Property, DV> properties =
                            Map.of(CONTEXT_MODIFIED, Level.FALSE_DV,
                                    EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV,
                                    CONTEXT_NOT_NULL, initialNotNull,
                                    EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED_DV,
                                    CONTEXT_IMMUTABLE, defaultImmutable);
                    Map<Property, DV> valueProperties = Map.of(); // FIXME
                    Instance instance = Instance.forLoopVariable(index(), lvr, valueProperties);
                    vic.setValue(instance, LinkedVariables.EMPTY, properties, true);
                    // the linking (normal, and content) can only be done after evaluating the expression over which we iterate
                } else {
                    initialiserToEvaluate = lvc; // == expression
                }
            } else initialiserToEvaluate = expression;

            if (initialiserToEvaluate != null && initialiserToEvaluate != EmptyExpression.EMPTY_EXPRESSION) {
                expressionsToEvaluate.add(initialiserToEvaluate);
            }
        }

        // part 2: updaters, + determine which local variables are modified in the updaters

        if (statementAnalysis.statement instanceof LoopStatement) {
            for (Expression expression : statementAnalysis.statement.getStructure().updaters()) {
                expression.visit(e -> {
                    VariableExpression ve;
                    if (e instanceof Assignment assignment
                            && (ve = assignment.target.asInstanceOf(VariableExpression.class)) != null) {
                        if (!(statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) &&
                                !statementAnalysis.localVariablesAssignedInThisLoop.contains(ve.name())) {
                            statementAnalysis.localVariablesAssignedInThisLoop.add(ve.name());
                        }
                        expressionsToEvaluate.add(assignment); // we do evaluate the assignment no that the var will be there
                    }
                });
            }
        } else if (statementAnalysis.statement instanceof ExplicitConstructorInvocation) {
            Structure structure = statement().getStructure();
            expressionsToEvaluate.addAll(structure.updaters());
        }

        return expressionsToEvaluate;
    }

    /*
    create local variables Y y = x for every sub-expression x instanceof Y y

    the scope of the variable is determined as follows:
    (1) if the expression is an if-statement, without else: pos = then block, neg = rest of current block
    (2) if the expression is an if-statement with else: pos = then block, neg = else block
    (3) otherwise, only the current expression is accepted (we set to then block)
    ==> positive: always then block
    ==> negative: either else or rest of block
     */


    private List<Assignment> patternVariables(SharedState sharedState, Expression expression) {
        List<FindInstanceOfPatterns.InstanceOfPositive> instanceOfList = FindInstanceOfPatterns.find(expression);
        boolean haveElse = statementAnalysis.statement instanceof IfElseStatement ifElse && !ifElse.elseBlock.isEmpty();
        StatementAnalyser firstSubBlock = !(statementAnalysis.statement instanceof IfElseStatement) ? null :
                navigationData.blocks.get().get(0).orElse(null);
        // create local variables
        instanceOfList.stream()
                .filter(instanceOf -> instanceOf.instanceOf().patternVariable() != null)
                .filter(instanceOf -> !statementAnalysis.variables.isSet(instanceOf.instanceOf().patternVariable().simpleName()))
                .forEach(instanceOf -> {
                    LocalVariableReference lvr = instanceOf.instanceOf().patternVariable();
                    String scope = instanceOf.positive() ? index() + ".0.0" : haveElse ? index() + ".1.0" :
                            indexOnlyIfEscapeInSubBlock(firstSubBlock);
                    Variable scopeVariable = instanceOf.instanceOf().expression() instanceof IsVariableExpression ve ?
                            ve.variable() : null;
                    VariableNature variableNature = new VariableNature.Pattern(scope, instanceOf.positive(), scopeVariable);
                    statementAnalysis.createVariable(sharedState.evaluationContext, lvr,
                            VariableInfoContainer.NOT_A_VARIABLE_FIELD, variableNature);
                });

        // add assignments
        return instanceOfList.stream()
                .filter(iop -> iop.instanceOf().patternVariable() != null)
                .map(iop -> new Assignment(sharedState.evaluationContext.getPrimitives(),
                        new VariableExpression(iop.instanceOf().patternVariable()), //  PropertyWrapper.propertyWrapper(
                        iop.instanceOf().expression())) //, Map.of(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL))
                .toList();
    }

    private String indexOnlyIfEscapeInSubBlock(StatementAnalyser subBlock) {
        if (subBlock == null) return "xx";
        // TODO no idea how to implement... (could be a delay, we have no idea yet because sub-blocks are handled later
        // and we cannot overwrite VIC's variableNature)
        return navigationData.next.get().map(StatementAnalyser::index).orElse("xx");
    }

    private Either<CausesOfDelay, List<Expression>> localVariablesInLoop(SharedState sharedState) {
        if (statementAnalysis.localVariablesAssignedInThisLoop == null) {
            return Either.right(List.of()); // not for us
        }
        // part 3, iteration 1+: ensure local loop variable copies and their values

        if (!statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) {
            return Either.left(new CausesOfDelay.SimpleSet(getLocation(), CauseOfDelay.Cause.LOCAL_VARS_ASSIGNED)); // DELAY
        }
        List<Expression> expressionsToEvaluate = new ArrayList<>();
        statementAnalysis.localVariablesAssignedInThisLoop.stream().forEach(fqn -> {
            assert statement() instanceof LoopStatement;

            VariableInfoContainer vic = statementAnalysis.findForWriting(fqn); // must exist already
            VariableInfo vi = vic.best(EVALUATION); // NOT merge, merge is after the loop
            // assign to local variable that has been created at Level 2 in this statement
            String assigned = index() + VariableInfoContainer.Level.INITIAL;
            LocalVariableReference loopCopy = statementAnalysis.createLocalLoopCopy(vi.variable(), index());
            String loopCopyFqn = loopCopy.fullyQualifiedName();
            String read = index() + EVALUATION;
            Expression newValue = Instance.localVariableInLoop(index(), vi.variable(),
                    sharedState.evaluationContext().getAnalyserContext());
            Map<Property, DV> valueProps = sharedState.evaluationContext.getValueProperties(newValue);
            if (!statementAnalysis.variables.isSet(loopCopyFqn)) {
                VariableInfoContainer newVic = VariableInfoContainerImpl.newLoopVariable(getLocation(),
                        loopCopy, assigned,
                        read,
                        newValue,
                        mergeValueAndLoopVar(valueProps, vi.getProperties()),
                        LinkedVariables.of(vi.variable(), LinkedVariables.STATICALLY_ASSIGNED_DV),
                        true);
                statementAnalysis.variables.put(loopCopyFqn, newVic);
            } else {
                // FIXME check mergeValueAndLoopVar -- which properties are we talking about?
                VariableInfoContainer loopVic = statementAnalysis.variables.get(loopCopyFqn);
                loopVic.setValue(newValue, LinkedVariables.EMPTY, valueProps, true);
            }
        });
        return Either.right(expressionsToEvaluate);
    }

    private static Map<Property, DV> mergeValueAndLoopVar(Map<Property, DV> value,
                                                          Map<Property, DV> loopVar) {
        Map<Property, DV> res = new HashMap<>(value);
        res.putAll(loopVar);
        return res;
    }

    /*
    We cannot yet set Linked1Variables in VIC.copy(), because the dependency graph is involved so
    "notReadInThisStatement" is not accurate. But unless an actual "delay" is set, there really is
    no involvement.
     */

    private AnalysisStatus evaluationOfMainExpression(SharedState sharedState) {
        List<Expression> expressionsFromInitAndUpdate = initializersAndUpdaters(sharedState);
        Either<CausesOfDelay, List<Expression>> expressionsFromLocalVariablesInLoop = localVariablesInLoop(sharedState);
        /*
        if we're in a loop statement and there are delays (localVariablesAssignedInThisLoop not frozen)
        we have to come back!
         */
        AnalysisStatus analysisStatus = expressionsFromLocalVariablesInLoop.isLeft()
                ? expressionsFromLocalVariablesInLoop.getLeft() : DONE;
        if (expressionsFromLocalVariablesInLoop.isRight()) {
            expressionsFromInitAndUpdate.addAll(expressionsFromLocalVariablesInLoop.getRight());
        }

        Structure structure = statementAnalysis.statement.getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
            // TODO do we need this? statementAnalysis.ensureLinkedVariables1();
            // try-statement has no main expression, and it may not have initialisers; break; continue; ...
            if (statementAnalysis.stateData.valueOfExpression.isVariable()) {
                setFinalAllowEquals(statementAnalysis.stateData.valueOfExpression, EmptyExpression.EMPTY_EXPRESSION);
            }
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.copyTimeAfterExecutionFromInitialTime();
            }
            if (statementAnalysis.statement instanceof BreakStatement breakStatement) {
                if (statementAnalysis.parent.statement instanceof SwitchStatementOldStyle) {
                    return analysisStatus;
                }
                StatementAnalysis.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(breakStatement);
                Expression state = sharedState.localConditionManager.stateUpTo(sharedState.evaluationContext, correspondingLoop.steps());
                correspondingLoop.statementAnalysis().stateData.addStateOfInterrupt(index(), state, state.isDelayed());
                if (state.isDelayed()) return state.causesOfDelay();
            } else if (statement() instanceof LocalClassDeclaration localClassDeclaration) {
                EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext);
                PrimaryTypeAnalyser primaryTypeAnalyser =
                        localAnalysers.get().stream()
                                .filter(pta -> pta.primaryTypes.contains(localClassDeclaration.typeInfo))
                                .findFirst().orElseThrow();
                builder.markVariablesFromPrimaryTypeAnalyser(primaryTypeAnalyser);
                return apply(sharedState, builder.build()).combinedStatus();
            } else if (statementAnalysis.statement instanceof ExplicitConstructorInvocation eci) {
                // empty parameters: this(); or super();
                Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, null);
                if (!assignments.isBooleanConstant()) {
                    EvaluationResult result = assignments.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());
                    AnalysisStatus applyResult = apply(sharedState, result).combinedStatus();
                    return applyResult.combine(analysisStatus);
                }
            }
            return analysisStatus;
        }

        try {
            if (structure.expression() != EmptyExpression.EMPTY_EXPRESSION) {
                expressionsFromInitAndUpdate.addAll(patternVariables(sharedState, structure.expression()));
                expressionsFromInitAndUpdate.add(structure.expression());
            }
            // Too dangerous to use CommaExpression.comma, because it filters out constants etc.!
            Expression toEvaluate = expressionsFromInitAndUpdate.size() == 1 ? expressionsFromInitAndUpdate.get(0) :
                    new CommaExpression(expressionsFromInitAndUpdate);
            EvaluationResult result;
            if (statementAnalysis.statement instanceof ReturnStatement) {
                assert structure.expression() != EmptyExpression.EMPTY_EXPRESSION;
                result = createAndEvaluateReturnStatement(sharedState);
            } else {
                result = toEvaluate.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());
            }
            if (statementAnalysis.statement instanceof ThrowStatement) {
                if (myMethodAnalyser.methodInfo.hasReturnValue()) {
                    result = modifyReturnValueRemoveConditionBasedOnState(sharedState, result);
                }
            }
            if (statementAnalysis.statement instanceof AssertStatement) {
                result = handleNotNullClausesInAssertStatement(sharedState.evaluationContext, result);
            }
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterEvaluation(result.statementTime(), index());
            }
            ApplyStatusAndEnnStatus applyResult = apply(sharedState, result);
            AnalysisStatus statusPost = AnalysisStatus.of(applyResult.status.merge(analysisStatus.causesOfDelay()));
            CausesOfDelay ennStatus = applyResult.ennStatus;

            if (statementAnalysis.statement instanceof ExplicitConstructorInvocation eci) {
                Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, result);
                if (!assignments.isBooleanConstant()) {
                    result = assignments.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());
                    ApplyStatusAndEnnStatus assignmentResult = apply(sharedState, result);
                    statusPost = assignmentResult.status.merge(analysisStatus.causesOfDelay());
                    ennStatus = applyResult.ennStatus.merge(assignmentResult.ennStatus);
                }
            }

            Expression value = result.value();
            assert value != null; // EmptyExpression in case there really is no value
            boolean valueIsDelayed = value.isDelayed() || statusPost != DONE;

            if (!valueIsDelayed && (statementAnalysis.statement instanceof IfElseStatement ||
                    statementAnalysis.statement instanceof AssertStatement)) {
                value = eval_IfElse_Assert(sharedState, value);
            } else if (!valueIsDelayed && statementAnalysis.statement instanceof HasSwitchLabels switchStatement) {
                eval_Switch(sharedState, value, switchStatement);
            }

            // the value can be delayed even if it is "true", for example (Basics_3)
            // see Precondition_3 for an example where different values arise, because preconditions kick in
            boolean valueIsDelayed2 = value.isDelayed() || statusPost != DONE;
            statementAnalysis.stateData.setValueOfExpression(value, valueIsDelayed2);

            if (ennStatus.isDelayed()) {
                log(DELAYED, "Delaying statement {} in {} because of external not null/external immutable",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            }
            return AnalysisStatus.of(ennStatus.merge(statusPost.causesOfDelay()));
        } catch (Throwable rte) {
            LOGGER.warn("Failed to evaluate main expression in statement {}", statementAnalysis.index);
            throw rte;
        }
    }

    /*
    fixme: this works in simpler situations, but does not when (much) more complex.

     */
    private EvaluationResult modifyReturnValueRemoveConditionBasedOnState(SharedState sharedState,
                                                                          EvaluationResult result) {
        if (sharedState.previous == null) return result; // first statement of block, no need to change
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);
        VariableInfo vi = sharedState.previous.findOrThrow(returnVariable);
        if (!vi.getValue().isReturnValue()) {
            // remove all return_value parts
            Expression newValue = vi.getValue().removeAllReturnValueParts();
            EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext).compose(result);
            Assignment assignment = new Assignment(statementAnalysis.primitives,
                    new VariableExpression(returnVariable), newValue);
            EvaluationResult assRes = assignment.evaluate(sharedState.evaluationContext, ForwardEvaluationInfo.DEFAULT);
            builder.compose(assRes);
            return builder.build();
        }
        return result;
    }

    private EvaluationResult handleNotNullClausesInAssertStatement(EvaluationContext evaluationContext,
                                                                   EvaluationResult evaluationResult) {
        Expression expression = evaluationResult.getExpression();
        Filter.FilterResult<ParameterInfo> result = moveConditionToParameter(evaluationContext, expression);
        if (result != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            boolean changes = false;
            for (Map.Entry<ParameterInfo, Expression> e : result.accepted().entrySet()) {
                boolean isNotNull = e.getValue().equalsNotNull();
                Variable notNullVariable = e.getKey();
                log(ANALYSER, "Found parameter (not)null ({}) assertion, {}", isNotNull, notNullVariable.simpleName());
                if (isNotNull) {
                    builder.setProperty(notNullVariable, CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                    changes = true;
                }
            }
            if (changes) {
                return builder.setExpression(evaluationResult.getExpression()).compose(evaluationResult).build();
            }
        }
        return evaluationResult;
    }

    private Expression replaceExplicitConstructorInvocation(SharedState sharedState,
                                                            ExplicitConstructorInvocation eci,
                                                            EvaluationResult result) {
         /* structure.updaters contains all the parameter values
               expressionsToEvaluate should contain assignments for each instance field, as found in the last statement of the
               explicit method
             */
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalyser(eci.methodInfo);
        int n = eci.methodInfo.methodInspection.get().getParameters().size();
        EvaluationResult.Builder builder = new EvaluationResult.Builder();
        Map<Expression, Expression> translation = new HashMap<>();
        if (result != null && n > 0) {
            int i = 0;
            List<Expression> storedValues = n == 1 ? List.of(result.value()) : result.storedValues();
            for (Expression parameterExpression : storedValues) {
                ParameterInfo parameterInfo = eci.methodInfo.methodInspection.get().getParameters().get(i);
                translation.put(new VariableExpression(parameterInfo), parameterExpression);
                i++;
            }
        }
        List<Expression> assignments = new ArrayList<>();
        for (FieldInfo fieldInfo : myMethodAnalyser.methodInfo.typeInfo.visibleFields(analyserContext)) {
            if (!fieldInfo.isStatic(analyserContext)) {
                for (VariableInfo variableInfo : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                    if (variableInfo.isAssigned()) {
                        EvaluationResult translated = variableInfo.getValue()
                                .reEvaluate(sharedState.evaluationContext, translation);
                        Assignment assignment = new Assignment(Identifier.generate(),
                                statementAnalysis.primitives,
                                new VariableExpression(new FieldReference(analyserContext, fieldInfo)),
                                translated.value(), null, null, false);
                        builder.compose(translated);
                        assignments.add(assignment);
                    }
                }
            }
        }
        return CommaExpression.comma(sharedState.evaluationContext, assignments);
    }

    /*
      modify the value of the return variable according to the evaluation result and the current state

      consider
      if(x) return a;
      return b;

      after the if, the state is !x, and the return variable has the value x?a:<return>
      we should not immediately overwrite, but take the existing return value into account, and return x?a:b

      See Eg. Warnings_5, ConditionalChecks_4
   */

    private EvaluationResult createAndEvaluateReturnStatement(SharedState sharedState) {
        assert myMethodAnalyser.methodInfo.hasReturnValue();
        Structure structure = statementAnalysis.statement.getStructure();
        ConditionManager localConditionManager = sharedState.localConditionManager;
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);
        Expression currentReturnValue = statementAnalysis.initialValueOfReturnVariable(returnVariable);

        EvaluationContext evaluationContext;
        Expression toEvaluate;
        if (localConditionManager.state().isBoolValueTrue() || currentReturnValue instanceof UnknownExpression) {
            // default situation
            toEvaluate = structure.expression();
            evaluationContext = sharedState.evaluationContext;
        } else {
            evaluationContext = new EvaluationContextImpl(sharedState.evaluationContext.getIteration(),
                    localConditionManager.withoutState(statementAnalysis.primitives), sharedState.evaluationContext.getClosure());
            if (myMethodAnalyser.methodInfo.returnType().equals(statementAnalysis.primitives.booleanParameterizedType())) {
                // state, boolean; evaluation of And will add clauses to the context one by one
                toEvaluate = And.and(evaluationContext, localConditionManager.state(), structure.expression());
            } else {
                // state, not boolean
                InlineConditional inlineConditional = new InlineConditional(Identifier.generate(),
                        analyserContext, localConditionManager.state(), structure.expression(), currentReturnValue);
                toEvaluate = inlineConditional.optimise(evaluationContext);
            }
        }
        Assignment assignment = new Assignment(statementAnalysis.primitives,
                new VariableExpression(new ReturnVariable(myMethodAnalyser.methodInfo)), toEvaluate);
        return assignment.evaluate(evaluationContext, structure.forwardEvaluationInfo());
    }

    /*
    goal: raise errors, exclude branches, etc.
     */
    private void eval_Switch(SharedState sharedState, Expression switchExpression, HasSwitchLabels switchStatement) {
        assert switchExpression != null;
        List<String> never = new ArrayList<>();
        List<String> always = new ArrayList<>();
        switchStatement.labels().forEach(label -> {
            Expression labelEqualsSwitchExpression = Equals.equals(sharedState.evaluationContext, label, switchExpression);
            Expression evaluated = sharedState.localConditionManager.evaluate(sharedState.evaluationContext,
                    labelEqualsSwitchExpression);
            if (evaluated.isBoolValueTrue()) {
                always.add(label.toString());
            } else if (evaluated.isBoolValueFalse()) {
                never.add(label.toString());
            }
        });
        // we could have any combination of the three variables

        if (!never.isEmpty() || !always.isEmpty()) {
            String msg = !always.isEmpty() ? "Is always reached: " + String.join("; ", always) :
                    "Is never reached: " + String.join("; ", never);
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.TRIVIAL_CASES_IN_SWITCH, msg));
        }
    }

    private Expression eval_IfElse_Assert(SharedState sharedState, Expression value) {
        assert value != null;

        Expression evaluated = sharedState.localConditionManager.evaluate(sharedState.evaluationContext, value);

        if (evaluated.isConstant()) {
            Message.Label message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData.blocks.get();
            if (statementAnalysis.statement instanceof IfElseStatement) {
                message = Message.Label.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = evaluated.isBoolValueTrue();
                    if (!isTrue) {
                        Message msg = Message.newMessage(new Location(myMethodAnalyser.methodInfo,
                                        firstStatement.index, firstStatement.statement.getIdentifier()),
                                Message.Label.UNREACHABLE_STATEMENT);
                        // let's add it to us, rather than to this unreachable statement
                        statementAnalysis.ensure(msg);
                    }
                    // guaranteed to be reached in block is always ALWAYS because it is the first statement
                    setExecutionOfSubBlock(firstStatement, isTrue ? FlowData.ALWAYS : FlowData.NEVER);
                });
                if (blocks.size() == 2) {
                    blocks.get(1).ifPresent(firstStatement -> {
                        boolean isTrue = evaluated.isBoolValueTrue();
                        if (isTrue) {
                            Message msg = Message.newMessage(new Location(myMethodAnalyser.methodInfo,
                                            firstStatement.index, firstStatement.statement.getIdentifier()),
                                    Message.Label.UNREACHABLE_STATEMENT);
                            statementAnalysis.ensure(msg);
                        }
                        setExecutionOfSubBlock(firstStatement, isTrue ? FlowData.NEVER : FlowData.ALWAYS);
                    });
                }
            } else if (statementAnalysis.statement instanceof AssertStatement) {
                boolean isTrue = evaluated.isBoolValueTrue();
                if (isTrue) {
                    message = Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE;
                } else {
                    message = Message.Label.ASSERT_EVALUATES_TO_CONSTANT_FALSE;
                    Optional<StatementAnalysis> next = statementAnalysis.navigationData.next.get();
                    if (next.isPresent()) {
                        StatementAnalysis nextAnalysis = next.get();
                        nextAnalysis.flowData.setGuaranteedToBeReached(FlowData.NEVER);
                        Message msg = Message.newMessage(new Location(myMethodAnalyser.methodInfo, nextAnalysis.index,
                                nextAnalysis.statement.getIdentifier()), Message.Label.UNREACHABLE_STATEMENT);
                        statementAnalysis.ensure(msg);
                    }
                }
            } else throw new UnsupportedOperationException();
            statementAnalysis.ensure(Message.newMessage(sharedState.evaluationContext.getLocation(), message));
            return evaluated;
        }
        return value;
    }

    private void setExecutionOfSubBlock(StatementAnalysis firstStatement, DV execution) {
        DV mine = statementAnalysis.flowData.getGuaranteedToBeReachedInMethod();
        DV combined;
        if (FlowData.ALWAYS.equals(mine)) combined = execution;
        else if (FlowData.NEVER.equals(mine)) combined = FlowData.NEVER;
        else if (FlowData.CONDITIONALLY.equals(mine)) combined = FlowData.CONDITIONALLY;
        else throw new UnsupportedOperationException();

        if (!firstStatement.flowData.getGuaranteedToBeReachedInMethod().equals(FlowData.NEVER) || !combined.equals(FlowData.CONDITIONALLY)) {
            firstStatement.flowData.setGuaranteedToBeReachedInMethod(combined);
        } // else: we'll keep NEVER
    }

    private AnalysisStatus subBlocks(SharedState sharedState) {
        List<Optional<StatementAnalyser>> startOfBlocks = navigationData.blocks.get();
        AnalysisStatus analysisStatus = AnalysisStatus.of(sharedState.localConditionManager.causesOfDelay());

        if (!startOfBlocks.isEmpty()) {
            return haveSubBlocks(sharedState, startOfBlocks).combine(analysisStatus);
        }

        if (statementAnalysis.statement instanceof AssertStatement) {
            Expression assertion = statementAnalysis.stateData.valueOfExpression.get();
            boolean expressionIsDelayed = statementAnalysis.stateData.valueOfExpression.isVariable();
            // NOTE that it is possible that assertion is not delayed, but the valueOfExpression is delayed
            // because of other delays in the apply method (see setValueOfExpression call in evaluationOfMainExpression)

            if (moveConditionToParameter(sharedState.evaluationContext, assertion) == null) {
                Expression translated = Objects.requireNonNullElse(
                        sharedState.evaluationContext.acceptAndTranslatePrecondition(assertion),
                        new BooleanConstant(statementAnalysis.primitives, true));
                Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
                statementAnalysis.stateData.setPrecondition(pc, expressionIsDelayed);
            } else {
                // the null/not null of parameters has been handled during the main evaluation
                statementAnalysis.stateData.setPrecondition(Precondition.empty(statementAnalysis.primitives),
                        expressionIsDelayed);
            }

            if (expressionIsDelayed) {
                analysisStatus = AnalysisStatus.of(statementAnalysis.stateData.valueOfExpressionIsDelayed());
            }
        }

        if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
            statementAnalysis.flowData.copyTimeAfterSubBlocksFromTimeAfterExecution();
        }

        statementAnalysis.stateData.setLocalConditionManagerForNextStatement(sharedState.localConditionManager);
        return analysisStatus;
    }

    private Filter.FilterResult<ParameterInfo> moveConditionToParameter(EvaluationContext evaluationContext, Expression expression) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<ParameterInfo> result = filter.filter(expression, filter.individualNullOrNotNullClauseOnParameter());
        if (result != null && !result.accepted().isEmpty() && result.rest().isBoolValueTrue()) {
            return result;
        }
        return null;
    }

    // identical code in statement analysis
    public StatementAnalyser navigateTo(String target) {
        String myIndex = index();
        if (myIndex.equals(target)) return this;
        if (target.startsWith(myIndex)) {
            // go into sub-block
            int n = myIndex.length();
            int blockIndex = Integer.parseInt(target.substring(n + 1, target.indexOf('.', n + 1)));
            return navigationData.blocks.get().get(blockIndex)
                    .orElseThrow(() -> new UnsupportedOperationException("Looking for " + target + ", block " + blockIndex));
        }
        if (myIndex.compareTo(target) < 0 && navigationData.next.get().isPresent()) {
            return navigationData.next.get().get().navigateTo(target);
        }
        throw new UnsupportedOperationException("? have index " + myIndex + ", looking for " + target);
    }

    private record ExecutionOfBlock(DV execution,
                                    StatementAnalyser startOfBlock,
                                    ConditionManager conditionManager,
                                    Expression condition,
                                    boolean isDefault,
                                    LocalVariableCreation catchVariable) {

        public boolean escapesAlwaysButNotWithPrecondition() {
            if (!execution.equals(FlowData.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().statementAnalysis;
                return lastStatement.flowData.interruptStatus().equals(FlowData.ALWAYS) && !lastStatement.flowData.alwaysEscapesViaException();
            }
            return false;
        }

        public boolean escapesAlways() {
            if (!execution.equals(FlowData.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().statementAnalysis;
                return lastStatement.flowData.interruptStatus().equals(FlowData.ALWAYS);
            }
            return false;
        }

        public boolean alwaysExecuted() {
            return execution.equals(FlowData.ALWAYS) && startOfBlock != null;
        }
    }

    private AnalysisStatus haveSubBlocks(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        List<ExecutionOfBlock> executions = subBlocks_determineExecution(sharedState, startOfBlocks);
        AnalysisStatus analysisStatus = DONE;


        int blocksExecuted = 0;
        for (ExecutionOfBlock executionOfBlock : executions) {
            if (executionOfBlock.startOfBlock != null) {
                if (!executionOfBlock.execution.equals(FlowData.NEVER)) {
                    ForwardAnalysisInfo forward;
                    if (statement() instanceof SwitchStatementOldStyle switchStatement) {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                switchStatement.startingPointToLabels(evaluationContext,
                                        executionOfBlock.startOfBlock.statementAnalysis),
                                statementAnalysis.stateData.valueOfExpression.get(),
                                statementAnalysis.stateData.valueOfExpression.get().causesOfDelay());
                    } else {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                null, null, CausesOfDelay.EMPTY);
                    }
                    StatementAnalyserResult result = executionOfBlock.startOfBlock
                            .analyseAllStatementsInBlock(evaluationContext.getIteration(),
                                    forward, evaluationContext.getClosure());
                    sharedState.builder.add(result);
                    analysisStatus = analysisStatus.combine(result.analysisStatus());
                    blocksExecuted++;
                } else {
                    // ensure that the first statement is unreachable
                    FlowData flowData = executionOfBlock.startOfBlock.statementAnalysis.flowData;
                    flowData.setGuaranteedToBeReachedInMethod(FlowData.NEVER);

                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.EMPTY_LOOP));
                    }

                    sharedState.builder.addMessages(executionOfBlock.startOfBlock.statementAnalysis.messageStream());
                }
            }
        }
        boolean keepCurrentLocalConditionManager = true;

        if (blocksExecuted > 0) {
            boolean atLeastOneBlockExecuted = atLeastOneBlockExecuted(executions);

            // note that isEscapeAlwaysExecuted cannot be delayed (otherwise, it wasn't ALWAYS?)
            List<StatementAnalysis.ConditionAndLastStatement> lastStatements;
            int maxTime;
            if (statementAnalysis.statement instanceof SwitchStatementOldStyle switchStatementOldStyle) {
                lastStatements = composeLastStatements(evaluationContext, switchStatementOldStyle, executions.get(0).startOfBlock);
                maxTime = executions.get(0).startOfBlock == null ? statementAnalysis.flowData.getTimeAfterEvaluation() :
                        executions.get(0).startOfBlock.lastStatement().statementAnalysis.flowData.getTimeAfterSubBlocks();
            } else {
                lastStatements = executions.stream()
                        .filter(ex -> ex.startOfBlock != null && !ex.startOfBlock.statementAnalysis.flowData.isUnreachable())
                        .map(ex -> new StatementAnalysis.ConditionAndLastStatement(ex.condition,
                                ex.startOfBlock.index(),
                                ex.startOfBlock.lastStatement(),
                                ex.startOfBlock.lastStatement().isEscapeAlwaysExecutedInCurrentBlock().valueIsTrue()))
                        .toList();
                /*
                 See VariableField_0; because we don't know which sub-block gets executed, we cannot use either
                 of the local copies, so we must create a new one.
                 */
                int increment = atLeastOneBlockExecuted ? 0 : 1;
                maxTime = lastStatements.stream()
                        .map(StatementAnalysis.ConditionAndLastStatement::lastStatement)
                        .mapToInt(sa -> sa.statementAnalysis.flowData.getTimeAfterSubBlocks())
                        .max().orElseThrow() + increment;
            }
            int maxTimeWithEscape;
            if (executions.stream().allMatch(ExecutionOfBlock::escapesAlways)) {
                maxTimeWithEscape = statementAnalysis.flowData.getTimeAfterEvaluation();
            } else {
                maxTimeWithEscape = maxTime;
            }
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterSubBlocks(maxTimeWithEscape, index());
            }

            // need timeAfterSubBlocks set already
            AnalysisStatus copyStatus = statementAnalysis.copyBackLocalCopies(evaluationContext,
                    sharedState.localConditionManager.state(), lastStatements, atLeastOneBlockExecuted, maxTimeWithEscape);
            analysisStatus = analysisStatus.combine(copyStatus);

            // compute the escape situation of the sub-blocks
            Expression addToStateAfterStatement = addToStateAfterStatement(evaluationContext, executions);
            if (!addToStateAfterStatement.isBoolValueTrue()) {
                ConditionManager newLocalConditionManager = sharedState.localConditionManager
                        .newForNextStatementDoNotChangePrecondition(evaluationContext, addToStateAfterStatement);
                statementAnalysis.stateData.setLocalConditionManagerForNextStatement(newLocalConditionManager);
                keepCurrentLocalConditionManager = false;
                log(PRECONDITION, "Continuing beyond default condition with conditional", addToStateAfterStatement);
            }
        } else {
            int maxTime = statementAnalysis.flowData.getTimeAfterEvaluation();
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterSubBlocks(maxTime, index());
            }
            AnalysisStatus copyStatus = statementAnalysis.copyBackLocalCopies(evaluationContext,
                    sharedState.localConditionManager.state(), List.of(), false, maxTime);
            analysisStatus = analysisStatus.combine(copyStatus);
        }

        if (keepCurrentLocalConditionManager) {
            statementAnalysis.stateData.setLocalConditionManagerForNextStatement(sharedState.localConditionManager);
        }
        // has to be executed AFTER merging
        potentiallyRaiseNullPointerWarningENN();

        return analysisStatus;
    }

    /*
    an old-style switch statement is analysed as a single block where return and break statements at the level
    below the statement have no hard interrupt value (see flow data).
    the aggregation of results (merging), however, is computed based on the case statements and the break/return statements.

    This method does the splitting in different groups of statements.
     */

    private List<StatementAnalysis.ConditionAndLastStatement> composeLastStatements(
            EvaluationContext evaluationContext,
            SwitchStatementOldStyle switchStatementOldStyle,
            StatementAnalyser startOfBlock) {
        Map<String, Expression> startingPointToLabels = switchStatementOldStyle
                .startingPointToLabels(evaluationContext, startOfBlock.statementAnalysis);
        return startingPointToLabels.entrySet().stream().map(e -> {
            StatementAnalyser lastStatement = startOfBlock.lastStatementOfSwitchOldStyle(e.getKey());
            boolean alwaysEscapes = statementAnalysis.flowData.alwaysEscapesViaException();
            return new StatementAnalysis.ConditionAndLastStatement(e.getValue(), e.getKey(), lastStatement, alwaysEscapes);
        }).toList();
    }

    // IMPROVE we need to use interrupts (so that returns and breaks in if's also work!) -- code also at beginning of method!!
    private StatementAnalyser lastStatementOfSwitchOldStyle(String startAt) {
        StatementAnalyser sa = this;
        while (true) {
            if (sa.index().compareTo(startAt) >= 0 &&
                    (sa.statementAnalysis.statement instanceof ReturnStatement ||
                            sa.statementAnalysis.statement instanceof BreakStatement))
                return sa;
            if (sa.navigationData.next.get().isPresent()) {
                sa = sa.navigationData.next.get().get();
            } else {
                return sa;
            }
        }
    }

    private boolean atLeastOneBlockExecuted(List<ExecutionOfBlock> list) {
        if (statementAnalysis.statement instanceof SwitchStatementOldStyle switchStatementOldStyle) {
            return switchStatementOldStyle.atLeastOneBlockExecuted();
        }
        if (statementAnalysis.statement instanceof SynchronizedStatement) return true;
        if (list.stream().anyMatch(ExecutionOfBlock::alwaysExecuted)) return true;
        // we have a default, and all conditions have code, and are possible
        return list.stream().anyMatch(e -> e.isDefault && e.startOfBlock != null) &&
                list.stream().allMatch(e -> (e.execution.equals(FlowData.CONDITIONALLY) || e.execution.isDelayed())
                        && e.startOfBlock != null);
    }

    private Expression addToStateAfterStatement(EvaluationContext evaluationContext, List<ExecutionOfBlock> list) {
        BooleanConstant TRUE = new BooleanConstant(evaluationContext.getPrimitives(), true);
        if (statementAnalysis.statement instanceof TryStatement) {
            ExecutionOfBlock main = list.get(0);
            if (main.escapesAlways()) {
                Expression[] conditionsWithoutEscape = list.stream()
                        // without escape, and remove main and finally
                        .filter(executionOfBlock -> !executionOfBlock.escapesAlways() && !executionOfBlock.condition.isBoolValueTrue())
                        .map(executionOfBlock -> executionOfBlock.condition)
                        .toArray(Expression[]::new);
                return Or.or(evaluationContext, conditionsWithoutEscape);
            }
            Expression[] conditionsWithEscape = list.stream()
                    // with escape, and remove main and finally
                    .filter(executionOfBlock -> executionOfBlock.escapesAlways() && !executionOfBlock.condition.isBoolValueTrue())
                    .map(executionOfBlock -> Negation.negate(evaluationContext, executionOfBlock.condition))
                    .toArray(Expression[]::new);
            return And.and(evaluationContext, conditionsWithEscape);
        }
        if (statementAnalysis.statement instanceof IfElseStatement) {
            ExecutionOfBlock e0 = list.get(0);
            if (list.size() == 1) {
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    return Negation.negate(evaluationContext, list.get(0).condition);
                }
                return TRUE;
            }
            if (list.size() == 2) {
                ExecutionOfBlock e1 = list.get(1);
                boolean escape1 = e1.escapesAlwaysButNotWithPrecondition();
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    if (escape1) {
                        // both if and else escape
                        return new BooleanConstant(evaluationContext.getPrimitives(), false);
                    }
                    // if escapes
                    return list.get(1).condition;
                }
                if (escape1) {
                    // else escapes
                    return list.get(0).condition;
                }
                return TRUE;
            }
            throw new UnsupportedOperationException("Impossible, if {} else {} has 2 blocks maximum.");
        }
        // a switch statement has no primary block, only subStructures, one per SwitchEntry

        // make an And of NOTs for all those conditions where the switch entry escapes
        if (statementAnalysis.statement instanceof HasSwitchLabels) {
            Expression[] components = list.stream().filter(ExecutionOfBlock::escapesAlwaysButNotWithPrecondition)
                    .map(e -> e.condition).toArray(Expression[]::new);
            if (components.length == 0) return TRUE;
            return And.and(evaluationContext, components);
        }

        /*
        loop statements: result should be !condition || <any exit of exactly this loop, no return> ...
        forEach loop does not have a condition.
         */
        if (statementAnalysis.statement instanceof LoopStatement &&
                !(statementAnalysis.statement instanceof ForEachStatement)) {
            List<Expression> ors = new ArrayList<>();
            statementAnalysis.stateData.statesOfInterruptsStream().forEach(stateOnInterrupt ->
                    ors.add(evaluationContext.replaceLocalVariables(stateOnInterrupt)));
            ors.add(evaluationContext.replaceLocalVariables(Negation.negate(evaluationContext, list.get(0).condition)));
            return Or.or(evaluationContext, ors.toArray(Expression[]::new));
        }

        if (statementAnalysis.statement instanceof SynchronizedStatement && list.get(0).startOfBlock != null) {
            Expression lastState = list.get(0).startOfBlock.lastStatement()
                    .statementAnalysis.stateData.conditionManagerForNextStatement.get().state();
            return evaluationContext.replaceLocalVariables(lastState);
        }
        return TRUE;
    }


    private List<ExecutionOfBlock> subBlocks_determineExecution(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        List<ExecutionOfBlock> executions = new ArrayList<>(startOfBlocks.size());

        Expression value = statementAnalysis.stateData.valueOfExpression.get();
        CausesOfDelay valueIsDelayed = statementAnalysis.stateData.valueOfExpressionIsDelayed();
        assert value.isDone() || valueIsDelayed.isDelayed(); // sanity check
        Structure structure = statementAnalysis.statement.getStructure();
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        // main block

        // some loops are never executed, and we can see that
        int start;
        if (statementAnalysis.statement instanceof SwitchStatementNewStyle) {
            start = 0;
        } else {
            DV firstBlockStatementsExecution = structure.statementExecution().apply(value, evaluationContext);
            DV firstBlockExecution = statementAnalysis.flowData.execution(firstBlockStatementsExecution);

            executions.add(makeExecutionOfPrimaryBlock(sharedState.evaluationContext,
                    sharedState.localConditionManager,
                    firstBlockExecution, startOfBlocks, value,
                    valueIsDelayed));
            start = 1;
        }

        for (int count = start; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements().get(count - start);
            Expression conditionForSubStatement;
            CausesOfDelay conditionForSubStatementIsDelayed;

            boolean isDefault = false;
            DV statementsExecution = subStatements.statementExecution().apply(value, evaluationContext);
            if (statementsExecution.equals(FlowData.DEFAULT_EXECUTION)) {
                isDefault = true;
                conditionForSubStatement = defaultCondition(evaluationContext, executions);
                conditionForSubStatementIsDelayed = conditionForSubStatement.causesOfDelay();
                if (conditionForSubStatement.isBoolValueFalse()) statementsExecution = FlowData.NEVER;
                else if (conditionForSubStatement.isBoolValueTrue()) statementsExecution = FlowData.ALWAYS;
                else if (conditionForSubStatement.isDelayed())
                    statementsExecution = conditionForSubStatement.causesOfDelay();
                else statementsExecution = FlowData.CONDITIONALLY;
            } else if (statementsExecution.equals(FlowData.ALWAYS)) {
                conditionForSubStatement = new BooleanConstant(statementAnalysis.primitives, true);
                conditionForSubStatementIsDelayed = CausesOfDelay.EMPTY;
            } else if (statementsExecution.equals(FlowData.NEVER)) {
                conditionForSubStatement = null; // will not be executed anyway
                conditionForSubStatementIsDelayed = CausesOfDelay.EMPTY;
            } else if (statement() instanceof TryStatement) { // catch
                conditionForSubStatement = Instance.forUnspecifiedCatchCondition(index(), statementAnalysis.primitives);
                conditionForSubStatementIsDelayed = CausesOfDelay.EMPTY;
            } else if (statement() instanceof SwitchStatementNewStyle newStyle) {
                SwitchEntry switchEntry = newStyle.switchEntries.get(count);
                conditionForSubStatement = switchEntry.structure.expression();
                conditionForSubStatementIsDelayed = conditionForSubStatement.causesOfDelay();
            } else throw new UnsupportedOperationException();

            DV execution = statementAnalysis.flowData.execution(statementsExecution);

            ConditionManager subCm = execution.equals(FlowData.NEVER) ? null :
                    sharedState.localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives,
                            conditionForSubStatement, conditionForSubStatementIsDelayed);
            boolean inCatch = statement() instanceof TryStatement && !subStatements.initialisers().isEmpty(); // otherwise, it is finally
            LocalVariableCreation catchVariable = inCatch ? (LocalVariableCreation) subStatements.initialisers().get(0) : null;
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm,
                    conditionForSubStatement, isDefault, catchVariable));
        }

        return executions;
    }

    private ExecutionOfBlock makeExecutionOfPrimaryBlock(EvaluationContext evaluationContext,
                                                         ConditionManager localConditionManager,
                                                         DV firstBlockExecution,
                                                         List<Optional<StatementAnalyser>> startOfBlocks,
                                                         Expression value,
                                                         CausesOfDelay valueIsDelayed) {
        Structure structure = statementAnalysis.statement.getStructure();
        Expression condition;
        ConditionManager cm;
        if (firstBlockExecution.equals(FlowData.NEVER)) {
            cm = null;
            condition = null;
        } else if (statementAnalysis.statement instanceof ForEachStatement) {
            // the expression is not a condition; however, we add one to ensure that the content is not empty
            condition = isNotEmpty(evaluationContext, value, valueIsDelayed.isDelayed());
            cm = localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives, condition,
                    condition.causesOfDelay());
        } else if (structure.expressionIsCondition()) {
            cm = localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives, value,
                    value.causesOfDelay());
            condition = value;
        } else {
            cm = localConditionManager;
            condition = new BooleanConstant(statementAnalysis.primitives, true);
        }
        return new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, condition,
                false, null);
    }

    private Expression isNotEmpty(EvaluationContext evaluationContext, Expression value, boolean valueIsDelayed) {
        if (value instanceof ArrayInitializer ai) {
            return new BooleanConstant(evaluationContext.getPrimitives(), ai.multiExpression.expressions().length > 0);
        }
        ParameterizedType returnType = value.returnType();
        if (returnType.arrays > 0) {
            return new GreaterThanZero(Identifier.generate(), evaluationContext.getPrimitives().booleanParameterizedType(),
                    new ArrayLength(evaluationContext.getPrimitives(), value), false);
        }
        if (returnType.typeInfo != null) {
            TypeInfo collection = returnType.typeInfo.recursivelyImplements(evaluationContext.getAnalyserContext(),
                    "java.util.Collection");
            if (collection != null) {
                MethodInfo isEmpty = collection.findUniqueMethod("isEmpty", 0);
                return Negation.negate(evaluationContext, new MethodCall(Identifier.generate(), false, value, isEmpty,
                        isEmpty.returnType(), List.of()));
            }
        }
        if (valueIsDelayed) {
            return DelayedExpression.forUnspecifiedLoopCondition(evaluationContext.getPrimitives().booleanParameterizedType(),
                    value.linkedVariables(evaluationContext).changeAllToDelay(value.causesOfDelay()), value.causesOfDelay());
        }
        return Instance.forUnspecifiedLoopCondition(index(), evaluationContext.getPrimitives());
    }

    private Expression defaultCondition(EvaluationContext evaluationContext, List<ExecutionOfBlock> executions) {
        List<Expression> previousConditions = executions.stream().map(e -> e.condition).collect(Collectors.toList());
        if (previousConditions.isEmpty()) {
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }
        Expression[] negated = previousConditions.stream().map(c -> Negation.negate(evaluationContext, c))
                .toArray(Expression[]::new);
        return And.and(evaluationContext, negated);
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
        if (!statementAnalysis.flowData.interruptsFlowIsSet()) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return statementAnalysis.flowData.interruptStatus().causesOfDelay();
        }
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData.bestAlwaysInterrupt();
        DV reached = statementAnalysis.flowData.getGuaranteedToBeReachedInMethod();
        if (reached.isDelayed()) return reached.causesOfDelay();

        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if ((atEndOfBlock || alwaysInterrupts) && myMethodAnalyser.methodInfo.isNotATestMethod()) {
            // important to be after this statement, because assignments need to be "earlier" in notReadAfterAssignment
            String indexEndOfBlock = StringUtil.beyond(index());
            statementAnalysis.variables.stream()
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
                            if (!statementAnalysis.messages.contains(unusedLv)) {
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
        if (navigationData.next.get().isEmpty() && myMethodAnalyser.methodInfo.isNotATestMethod()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variables.stream()
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
                && myMethodAnalyser.methodInfo.isNotATestMethod()) {
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().variableNature() instanceof VariableNature.LoopVariable loopVariable &&
                            loopVariable.statementIndex().equals(index()))
                    .forEach(e -> {
                        String loopVarFqn = e.getKey();
                        StatementAnalyser first = navigationData.blocks.get().get(0).orElse(null);
                        StatementAnalysis statementAnalysis = first == null ? null : first.lastStatement().statementAnalysis;
                        if (statementAnalysis == null || !statementAnalysis.variables.isSet(loopVarFqn) ||
                                !statementAnalysis.variables.get(loopVarFqn).current().isRead()) {
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
        if (statementAnalysis.statement instanceof ExpressionAsStatement eas
                && eas.expression instanceof MethodCall methodCall
                && myMethodAnalyser.methodInfo.isNotATestMethod()) {
            if (Primitives.isVoidOrJavaLangVoid(methodCall.methodInfo.returnType())) return DONE;
            MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
            DV identity = methodAnalysis.getProperty(Property.IDENTITY);
            if (identity.isDelayed()) {
                log(DELAYED, "Delaying unused return value in {} {}, waiting for @Identity of {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
                return identity.causesOfDelay();
            }
            if (identity.valueIsTrue()) return DONE;
            DV modified = methodAnalysis.getProperty(MODIFIED_METHOD);
            if (modified.isDelayed() && !methodCall.methodInfo.isAbstract()) {
                log(DELAYED, "Delaying unused return value in {} {}, waiting for @Modified of {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName);
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
                                index(), myMethodAnalyser.methodInfo.fullyQualifiedName, methodCall.methodInfo.fullyQualifiedName());
                        return delays;
                    }
                }

                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.Label.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
            }
        }
        return DONE;
    }


    public List<StatementAnalyser> lastStatementsOfNonEmptySubBlocks() {
        return navigationData.blocks.get().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sa -> !sa.statementAnalysis.flowData.isUnreachable())
                .map(StatementAnalyser::lastStatement)
                .collect(Collectors.toList());
    }

    public EvaluationContext newEvaluationContextForOutside() {
        return new EvaluationContextImpl(0, ConditionManager.initialConditionManager(statementAnalysis.primitives), null);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {
        private final boolean disableEvaluationOfMethodCallsUsingCompanionMethods;

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            this(iteration, conditionManager, closure, false);
        }

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager,
                                      EvaluationContext closure,
                                      boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            super(iteration, conditionManager, closure);
            this.disableEvaluationOfMethodCallsUsingCompanionMethods = disableEvaluationOfMethodCallsUsingCompanionMethods;
        }

        @Override
        public String statementIndex() {
            return statementAnalysis.index;
        }

        @Override
        public boolean allowedToIncrementStatementTime() {
            return !statementAnalysis.inSyncBlock;
        }

        @Override
        public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
            return getAnalyserContext().inAnnotatedAPIAnalysis() || disableEvaluationOfMethodCallsUsingCompanionMethods;
        }

        @Override
        public int getIteration() {
            return iteration;
        }

        @Override
        public TypeInfo getCurrentType() {
            return myMethodAnalyser.methodInfo.typeInfo;
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return myMethodAnalyser;
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return StatementAnalyser.this;
        }

        @Override
        public Location getLocation() {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index,
                    statementAnalysis.statement.getIdentifier());
        }

        @Override
        public Location getLocation(Identifier identifier) {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index, identifier);
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return child(condition, false);
        }

        @Override
        public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            return new EvaluationContextImpl(iteration,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, condition.causesOfDelay()),
                    closure,
                    disableEvaluationOfMethodCallsUsingCompanionMethods);
        }

        @Override
        public EvaluationContext dropConditionManager() {
            ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
            return new EvaluationContextImpl(iteration, cm, closure, disableEvaluationOfMethodCallsUsingCompanionMethods);
        }

        public EvaluationContext childState(Expression state) {
            return new EvaluationContextImpl(iteration, conditionManager.addState(state, state.causesOfDelay()), closure,
                    false);
        }

        /*
        differs sufficiently from the regular getProperty, in that it fast tracks as soon as one of the not nulls
        reaches EFFECTIVELY_NOT_NULL, and that it always reads from the initial value of variables.
         */

        @Override
        public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
            if (value instanceof IsVariableExpression ve) {
                VariableInfo variableInfo = findForReading(ve.variable(), getInitialStatementTime(), true);
                DV cnn = variableInfo.getProperty(useEnnInsteadOfCnn ? EXTERNAL_NOT_NULL : CONTEXT_NOT_NULL);
                if (cnn.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return true;
                DV nne = variableInfo.getProperty(NOT_NULL_EXPRESSION);
                if (nne.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return true;
                return notNullAccordingToConditionManager(ve.variable());
            }
            return MultiLevel.isEffectivelyNotNull(getProperty(value, NOT_NULL_EXPRESSION,
                    true, false));
        }

        /*
        this one is meant for non-eventual types (for now). After/before errors are caught in EvaluationResult
         */
        @Override
        public boolean cannotBeModified(Expression value) {
            if (value instanceof IsVariableExpression ve) {
                VariableInfo variableInfo = findForReading(ve.variable(), getInitialStatementTime(), true);
                DV cImm = variableInfo.getProperty(CONTEXT_IMMUTABLE);
                if (MultiLevel.isAtLeastEffectivelyE2Immutable(cImm)) return true;
                DV imm = variableInfo.getProperty(IMMUTABLE);
                if (MultiLevel.isAtLeastEffectivelyE2Immutable(imm)) return true;
                DV extImm = variableInfo.getProperty(EXTERNAL_IMMUTABLE);
                if (MultiLevel.isAtLeastEffectivelyE2Immutable(extImm)) return true;
                DV formal = variableInfo.variable().parameterizedType().defaultImmutable(analyserContext, false);
                return MultiLevel.isAtLeastEffectivelyE2Immutable(formal);
            }
            DV valueProperty = getProperty(value, IMMUTABLE, true, false);
            return MultiLevel.isAtLeastEffectivelyE2Immutable(valueProperty);
        }

        private DV getVariableProperty(Variable variable, Property property, boolean duringEvaluation) {
            if (duringEvaluation) {
                return getPropertyFromPreviousOrInitial(variable, property, getInitialStatementTime());
            }
            return getProperty(variable, property);
        }

        @Override
        public DV getProperty(Expression value, Property property, boolean duringEvaluation,
                              boolean ignoreStateInConditionManager) {
            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof IsVariableExpression ve) {
                Variable variable = ve.variable();
                // read what's in the property map (all values should be there) at initial or current level
                DV inMap = getVariableProperty(variable, property, duringEvaluation);
                if (property == NOT_NULL_EXPRESSION) {
                    if (Primitives.isPrimitiveExcludingVoid(variable.parameterizedType())) {
                        return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                    }
                    DV cnn = getVariableProperty(variable, CONTEXT_NOT_NULL, duringEvaluation);
                    DV cnnInMap = cnn.max(inMap);
                    if (cnnInMap.isDelayed()) {
                        // we return even if cmNn would be ENN, because our value could be higher
                        return cnnInMap;
                    }
                    boolean cmNn = notNullAccordingToConditionManager(variable);
                    return cnnInMap.max(cmNn ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV);
                }

                if (property == IMMUTABLE) {
                    DV formally = variable.parameterizedType().defaultImmutable(getAnalyserContext(), false);
                    if (formally.equals(IMMUTABLE.bestDv)) return formally; // EFFECTIVELY_E2, for primitives etc.
                    if (isMyself(variable.parameterizedType())) return MultiLevel.MUTABLE_DV;
                    DV formallyInMap = formally.max(inMap);
                    if (formallyInMap.isDelayed()) {
                        return formally;
                    }
                    DV cImm = getVariableProperty(variable, CONTEXT_IMMUTABLE, duringEvaluation);
                    if (cImm.isDelayed()) {
                        return cImm;
                    }
                    return cImm.max(formallyInMap);
                }
                return inMap;
            }

            if (NOT_NULL_EXPRESSION == property) {
                if (ignoreStateInConditionManager) {
                    EvaluationContext customEc = new EvaluationContextImpl(iteration,
                            conditionManager.withoutState(getPrimitives()), closure);
                    return value.getProperty(customEc, NOT_NULL_EXPRESSION, true);
                }

                DV directNN = value.getProperty(this, NOT_NULL_EXPRESSION, true);
                // assert !Primitives.isPrimitiveExcludingVoid(value.returnType()) || directNN == MultiLevel.EFFECTIVELY_NOT_NULL;

                if (directNN.equals(MultiLevel.NULLABLE_DV)) {
                    Expression valueIsNull = Equals.equals(Identifier.generate(),
                            this, value, NullConstant.NULL_CONSTANT, false);
                    Expression evaluation = conditionManager.evaluate(this, valueIsNull);
                    if (evaluation.isBoolValueFalse()) {
                        // IMPROVE should not necessarily be ENN, could be ContentNN depending
                        return MultiLevel.EFFECTIVELY_NOT_NULL_DV.max(directNN);
                    }
                }
                return directNN;
            }

            // redirect to Value.getProperty()
            // this is the only usage of this method; all other evaluation of a Value in an evaluation context
            // must go via the current method
            return value.getProperty(this, property, true);
        }

        @Override
        public boolean notNullAccordingToConditionManager(Variable variable) {
            return notNullAccordingToConditionManager(variable, statementAnalysis::findOrThrow);
        }

        /*
        Important that the closure is used for local variables and parameters (we'd never find them otherwise).
        However, fields will be introduced in StatementAnalysis.fromFieldAnalyserIntoInitial and should
        have their own local copy.
         */
        private VariableInfo findForReading(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            if (closure != null && isNotMine(variable) && !(variable instanceof FieldReference)) {
                return ((EvaluationContextImpl) closure).findForReading(variable, statementTime, isNotAssignmentTarget);
            }
            return statementAnalysis.initialValueForReading(variable, statementTime, isNotAssignmentTarget);
        }

        private boolean isNotMine(Variable variable) {
            return getCurrentType() != variable.getOwningType();
        }

        // we pass on the information about the potential newly created local variable copy
        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            VariableInfo variableInfo = findForReading(variable, statementTime, forwardEvaluationInfo.isNotAssignmentTarget());

            // important! do not use variable in the next statement, but variableInfo.variable()
            // we could have redirected from a variable field to a local variable copy
            if (forwardEvaluationInfo.assignToField()
                    && variable instanceof LocalVariableReference lvr && lvr.variable.nature().localCopyOf() == null) {
                return variableInfo.getValue();
            }
            return variableInfo.getVariableValue(forwardEvaluationInfo.assignmentTarget());
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime) {
            VariableInfo variableInfo = findForReading(variable, statementTime, true);
            Expression value = variableInfo.getValue();

            // redirect to other variable
            VariableExpression ve;
            if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
                assert ve.variable() != variable :
                        "Variable " + variable.fullyQualifiedName() + " has been assigned a VariableValue value pointing to itself";
                return currentValue(ve.variable(), statementTime);
            }
            return value;
        }

        @Override
        public DV getProperty(Variable variable, Property property) {
            VariableInfo vi = statementAnalysis.findOrThrow(variable);
            return vi.getProperty(property); // ALWAYS from the map!!!!
        }

        @Override
        public DV getPropertyFromPreviousOrInitial(Variable variable, Property property, int statementTime) {
            VariableInfo vi = findForReading(variable, statementTime, true);
            return vi.getProperty(property);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        /*
        The linkedVariables of a VariableExpression redirect to this method, because we have
        access to a lot more information about the variable.

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            Boolean hidden = variable.parameterizedType().isTransparent(analyserContext, myMethodAnalyser.methodInfo.typeInfo);
            int value;
            if (hidden == null) {
                value = LinkedVariables.DELAYED_VALUE;
            } else if (hidden) {
                VariableInfo variableInfo = statementAnalysis.initialValueForReading(variable, getInitialStatementTime(), true);
                int immutable = variableInfo.getProperty(IMMUTABLE);
                int level = MultiLevel.level(immutable);
                value = MultiLevel.independentCorrespondingToImmutableLevel(level);
                if (value == MultiLevel.INDEPENDENT) return LinkedVariables.EMPTY;
            } else {
                // accessible, like an assignment to the variable
                value = LinkedVariables.ASSIGNED;
            }
            return new LinkedVariables(Map.of(variable, value), value == LinkedVariables.DELAYED_VALUE);
        }
        */

        @Override
        public int getInitialStatementTime() {
            return statementAnalysis.flowData.getInitialTime();
        }

        @Override
        public int getFinalStatementTime() {
            return statementAnalysis.flowData.getTimeAfterSubBlocks();
        }

        /*
        go from local loop variable to instance when exiting a loop statement;
        both for merging and for the state

        non-null status to be copied from the variable; delays should/can work but need testing.
        A positive @NotNull is transferred, a positive @Nullable is replaced by a DELAY...
         */
        @Override
        public Expression replaceLocalVariables(Expression mergeValue) {
            if (statementAnalysis.statement instanceof LoopStatement) {
                TranslationMapImpl.Builder translationMap = new TranslationMapImpl.Builder();
                statementAnalysis.variables.stream()
                        .filter(e -> statementAnalysis.index.equals(e.getValue()
                                .variableNature().getStatementIndexOfThisLoopOrLoopCopyVariable()))
                        .forEach(e -> {
                            VariableInfo eval = e.getValue().best(EVALUATION);
                            Variable variable = eval.variable();

                            Map<Property, DV> valueProperties = getValueProperties(eval.getValue());
                            CausesOfDelay delays = valueProperties.values().stream()
                                    .map(DV::causesOfDelay)
                                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
                            if (delays.isDone()) {
                                Expression newObject = Instance.genericMergeResult(index(),
                                        e.getValue().current().variable(), valueProperties);
                                translationMap.put(new VariableExpression(variable), newObject);
                            }

                            Expression delayed = DelayedExpression.forReplacementObject(variable.parameterizedType(),
                                    eval.getLinkedVariables().remove(v -> v.equals(variable)).changeAllToDelay(delays), delays);
                            translationMap.put(DelayedVariableExpression.forVariable(variable, delays), delayed);
                        });
                return mergeValue.translate(translationMap.build());
            }
            return mergeValue;
        }

        /*
        Need to translate local copies of fields into fields.
        Should we do only their first appearance? ($0)
         */
        @Override
        public Expression acceptAndTranslatePrecondition(Expression precondition) {
            if (precondition.isBooleanConstant()) return null;
            Map<Expression, Expression> translationMap = precondition.variables().stream()
                    .filter(v -> v instanceof LocalVariableReference lvr &&
                            lvr.variable.nature() instanceof VariableNature.CopyOfVariableField)
                    .collect(Collectors.toUnmodifiableMap(VariableExpression::new,
                            v -> new VariableExpression(((LocalVariableReference) v).variable.nature().localCopyOf())));
            Expression translated;
            if (translationMap.isEmpty()) {
                translated = precondition;
            } else {
                // we need an evaluation context that simply translates, but does not interpret stuff
                EvaluationContext evaluationContext = new ConditionManager.EvaluationContextImpl(getAnalyserContext());
                translated = precondition.reEvaluate(evaluationContext, translationMap).getExpression();
            }
            if (translated.variables().stream()
                    .allMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference)) {
                return translated;
            }
            return null;
        }

        @Override
        public boolean isPresent(Variable variable) {
            return statementAnalysis.variables.isSet(variable.fullyQualifiedName());
        }

        @Override
        public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
            return localAnalysers.isSet() ? localAnalysers.get() : null;
        }

        @Override
        public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
            return statementAnalysis.variables.stream().filter(e -> e.getValue().current()
                    .variable() instanceof LocalVariableReference);
        }

        @Override
        public CausesOfDelay variableIsDelayed(Variable variable) {
            VariableInfo vi = statementAnalysis.findOrNull(variable, INITIAL);
            if (vi == null) {
                return new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(variable,
                        getLocation(), CauseOfDelay.Cause.VARIABLE_DOES_NOT_EXIST));
            }
            return vi.getValue().causesOfDelay();
        }

        @Override
        public MethodInfo concreteMethod(Variable variable, MethodInfo abstractMethodInfo) {
            assert abstractMethodInfo.isAbstract();
            VariableInfo variableInfo = findForReading(variable, getInitialStatementTime(), true);
            ParameterizedType type = variableInfo.getValue().returnType();
            if (type.typeInfo != null && !type.typeInfo.isAbstract()) {
                return type.typeInfo.findMethodImplementing(abstractMethodInfo);
            }
            return null;
        }


        @Override
        public boolean hasBeenAssigned(Variable variable) {
            VariableInfoContainer vic = statementAnalysis.variables.getOrDefaultNull(variable.fullyQualifiedName());
            if (vic == null) return false;
            VariableInfo vi = vic.getPreviousOrInitial();
            return vi.isAssigned();
        }

        @Override
        public boolean hasState(Expression expression) {
            VariableExpression ve;
            if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
                VariableInfo vi = findForReading(ve.variable(), statementAnalysis.statementTime(INITIAL), true);
                return vi.getValue() != null && vi.getValue().hasState();
            }
            return expression.hasState();
        }

        @Override
        public Expression state(Expression expression) {
            VariableExpression ve;
            if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
                VariableInfo vi = findForReading(ve.variable(), statementAnalysis.statementTime(INITIAL), true);
                return vi.getValue().state();
            }
            return expression.state();
        }

        @Override
        public VariableInfo findOrThrow(Variable variable) {
            return statementAnalysis.findOrThrow(variable);
        }
    }
}
