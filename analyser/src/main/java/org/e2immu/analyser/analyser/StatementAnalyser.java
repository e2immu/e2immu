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
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.FlowData.Execution.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser>, HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);
    public static final String ANALYSE_METHOD_LEVEL_DATA = "analyseMethodLevelData";

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

        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
        }
        StatementAnalyser first = null;
        StatementAnalyser previous = null;
        for (Statement statement : statements) {
            String padded = pad(statementIndex, statements.size());
            String iPlusSt = indices.isEmpty() ? "" + padded : indices + "." + padded;
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
                if (analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
                    wasReplacement = false;
                } else {
                    // first attempt at detecting a transformation
                    wasReplacement = checkForPatterns(evaluationContext);
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
        } catch (RuntimeException rte) {
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
                            new Or(evaluationContext.getPrimitives()).append(evaluationContext,
                                    base.switchIdToLabels().values().stream()
                                            .filter(e -> e != EmptyExpression.DEFAULT_EXPRESSION).toArray(Expression[]::new)));
                } else {
                    toAdd = label;
                }
                if (startFrom.isBoolValueTrue()) return toAdd;
                return new And(evaluationContext.getPrimitives()).append(evaluationContext, startFrom, toAdd);
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
        return followReplacements().navigationData.next.get().map(statementAnalyser -> {
            if (statementAnalyser.statementAnalysis.flowData.isUnreachable()) {
                return this;
            }
            return statementAnalyser.lastStatement();
        }).orElse(this);
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
        if (!analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
            MethodInfo methodInfo = myMethodAnalyser.methodInfo;
            return patternMatcher.matchAndReplace(methodInfo, this, evaluationContext);
        }
        return false;
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
                        .add("checkUnreachableStatement", this::checkUnreachableStatement)
                        .add("initialiseOrUpdateVariables", this::initialiseOrUpdateVariables)
                        .add("analyseTypesInStatement", this::analyseTypesInStatement)
                        .add("evaluationOfMainExpression", this::evaluationOfMainExpression)
                        .add("subBlocks", this::subBlocks)
                        .add("analyseFlowData", sharedState -> statementAnalysis.flowData.analyse(this, previous,
                                sharedState.forwardAnalysisInfo.execution()))
                        .add("freezeAssignmentInBlock", this::freezeAssignmentInBlock)
                        .add("checkNotNullEscapesAndPreconditions", this::checkNotNullEscapesAndPreconditions)
                        .add(ANALYSE_METHOD_LEVEL_DATA, sharedState -> statementAnalysis.methodLevelData.analyse(sharedState, statementAnalysis,
                                previous == null ? null : previous.methodLevelData,
                                statementAnalysis.stateData))
                        .add("checkUnusedReturnValue", sharedState -> checkUnusedReturnValueOfMethodCall())
                        .add("checkUselessAssignments", sharedState -> checkUselessAssignments())
                        .add("checkUnusedLocalVariables", sharedState -> checkUnusedLocalVariables())
                        .add("checkUnusedLoopVariables", sharedState -> checkUnusedLoopVariables())
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
                    .addMessages(statementAnalysis.messages.stream())
                    .setAnalysisStatus(overallStatus)
                    .combineAnalysisStatus(wasReplacement ? PROGRESS : DONE).build();
            analysisStatus = result.analysisStatus;

            visitStatementVisitors(statementAnalysis.index, result, sharedState);

            log(ANALYSER, "Returning from statement {} of {} with analysis status {}", statementAnalysis.index,
                    myMethodAnalyser.methodInfo.name, analysisStatus);
            return result;
        } catch (RuntimeException rte) {
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
                                                       boolean conditionIsDelayed) {
        Precondition combinedPrecondition;
        boolean combinedPreconditionIsDelayed;
        if (previous.methodLevelData.combinedPrecondition.isFinal()) {
            combinedPrecondition = previous.methodLevelData.combinedPrecondition.get();
            combinedPreconditionIsDelayed = false;
        } else {
            combinedPreconditionIsDelayed = true;
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
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVariableVisitors) {
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
                                    sharedState.evaluationContext.isDelayed(variableInfoContainer.current().getValue()),
                                    variableInfoContainer.current().getProperties(),
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVisitors) {
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
                                sortedType,
                                analyserContext.getConfiguration(),
                                analyserContext.getPrimitives(),
                                analyserContext.getE2ImmuAnnotationExpressions());
                        primaryTypeAnalyser.initialize();
                        return primaryTypeAnalyser;
                    }).collect(Collectors.toUnmodifiableList());
            localAnalysers.set(analysers);
            analysers.forEach(analyserContext::addPrimaryTypeAnalyser);

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
        if (sharedState.previous != null && sharedState.previous.flowData.getGuaranteedToBeReachedInMethod() == NEVER) {
            statementAnalysis.flowData.setGuaranteedToBeReached(NEVER);
            return DONE_ALL;
        }
        FlowData.Execution execution = sharedState.forwardAnalysisInfo().execution();
        Expression state = sharedState.localConditionManager.state();
        boolean stateIsDelayed = sharedState.evaluationContext.isDelayed(state);
        boolean localConditionManagerIsDelayed = sharedState.localConditionManager.isDelayed();
        AnalysisStatus analysisStatus = statementAnalysis.flowData.computeGuaranteedToBeReachedReturnUnreachable
                (sharedState.previous, execution, state, stateIsDelayed, localConditionManagerIsDelayed);
        if (analysisStatus == DONE_ALL) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return analysisStatus;
    }

    private Boolean isEscapeAlwaysExecutedInCurrentBlock() {
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        if (bestAlways == InterruptsFlow.DELAYED) return null;
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return statementAnalysis.flowData.getGuaranteedToBeReachedInCurrentBlock() == FlowData.Execution.ALWAYS;
        }
        return false;
    }

    /*
    delays on ENN are dealt with later than normal delays on values
     */
    record ApplyStatusAndEnnStatus(AnalysisStatus status, AnalysisStatus ennStatus) {
        public AnalysisStatus combinedStatus() {
            return status.combine(ennStatus);
        }
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
    For every variable, a number of steps are executed:
    - it is created, some local copies are created, and it is prepared for EVAL level
    - values, linked variables are set

    Finally, general modifications are carried out
     */
    private ApplyStatusAndEnnStatus apply(SharedState sharedState,
                                          EvaluationResult evaluationResult) {
        AnalysisStatus status = evaluationResult.someValueWasDelayed() ? DELAYS : DONE;

        if (evaluationResult.addCircularCallOrUndeclaredFunctionalInterface()) {
            statementAnalysis.methodLevelData.addCircularCallOrUndeclaredFunctionalInterface();
        }
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();
        Map<Variable, LinkedVariables> remapStaticallyAssignedVariables = new HashMap<>();

        // the first part is per variable
        // order is important because we need to re-map statically assigned variables
        // but first, we need to ensure that all variables exist, independent of the later ordering

        // make a copy because we might add a variable when linking the local loop copy
        evaluationResult.changeData().forEach((v, cd) ->
                ensureVariables(sharedState.evaluationContext, v, cd, evaluationResult.statementTime()));
        Map<Variable, VariableInfoContainer> existingVariablesNotVisited = statementAnalysis.variables.stream()
                .collect(Collectors.toMap(e -> e.getValue().current().variable(), Map.Entry::getValue, (v1, v2) -> v2, HashMap::new));

        List<Map.Entry<Variable, EvaluationResult.ChangeData>> sortedEntries = new ArrayList<>(evaluationResult.changeData().entrySet());
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

        boolean linked1Delays = false;
        boolean linkedDelays = false;
        AnalysisStatus immutableAtAssignment = DONE;

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
                Expression valueToWrite = maybeValueNeedsState(sharedState, vic, vi1, bestValue(changeData, vi1));
                boolean valueToWriteIsDelayed = sharedState.evaluationContext.isDelayed(valueToWrite);

                log(ANALYSER, "Write value {} to variable {}", valueToWrite, variable.fullyQualifiedName());
                // first do the properties that come with the value; later, we'll write the ones in changeData
                Map<VariableProperty, Integer> valueProperties = sharedState.evaluationContext.getValueProperties(valueToWrite);
                Map<VariableProperty, Integer> varProperties = sharedState.evaluationContext.getVariableProperties(valueToWrite, statementAnalysis.statementTime(EVALUATION));
                Map<VariableProperty, Integer> merged = mergeAssignment(variable, valueToWriteIsDelayed,
                        valueProperties, varProperties, changeData.properties(), groupPropertyValues);

                remapStaticallyAssignedVariables.put(variable, vi1.getStaticallyAssignedVariables());
                vic.setValue(valueToWrite, valueToWriteIsDelayed, changeData.staticallyAssignedVariables(),
                        merged, false);

                int immutable = merged.getOrDefault(IMMUTABLE, Level.DELAY);
                if (immutable == Level.DELAY) {
                    // we want to revisit this one without blocking everything
                    immutableAtAssignment = DELAYS;
                }

                if (vic.isLocalVariableInLoopDefinedOutside()) {
                    VariableInfoContainer local = addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
                    // assign the value of the assignment to the local copy created
                    if (local != null) {
                        Variable localVar = local.current().variable();
                        log(ANALYSER, "Write value {} to local copy variable {}", valueToWrite, localVar.fullyQualifiedName());
                        Map<VariableProperty, Integer> merged2 = mergeAssignment(localVar, valueToWriteIsDelayed, valueProperties, varProperties,
                                changeData.properties(), groupPropertyValues);
                        remapStaticallyAssignedVariables.put(localVar, local.getPreviousOrInitial().getStaticallyAssignedVariables());
                        local.setValue(valueToWrite, valueToWriteIsDelayed, changeData.staticallyAssignedVariables(), merged2,
                                false);
                        // because of the static assignment we can start empty
                        local.setLinkedVariables(LinkedVariables.EMPTY, false);
                    }
                }
            } else {
                Map<VariableProperty, Integer> merged = mergePreviousAndChange(variable, vi1.getProperties().toImmutableMap(),
                        changeData.properties(), groupPropertyValues);
                if (changeData.value() != null) {
                    // a modifying method caused an updated instance value
                    // for statically assigned variables, EMPTY means: take the value of the initial, unless it has no value
                    LinkedVariables staticallyAssigned = remap(remapStaticallyAssignedVariables,
                            changeData.staticallyAssignedVariables().isEmpty() ?
                                    vi1.staticallyAssignedVariablesIsSet() ? vi1.getStaticallyAssignedVariables() : LinkedVariables.EMPTY :
                                    changeData.staticallyAssignedVariables());
                    boolean valueIsDelayed = sharedState.evaluationContext.isDelayed(changeData.value());
                    vic.setValue(changeData.value(), valueIsDelayed, staticallyAssigned, merged, false);
                } else {
                    LinkedVariables sav = remap(remapStaticallyAssignedVariables, vi1.getStaticallyAssignedVariables());
                    if (variable instanceof This || !evaluationResult.someValueWasDelayed()
                            && !changeData.haveDelaysCausedByMethodCalls()) {
                        // we're not assigning (and there is no change in instance because of a modifying method)
                        // only then we copy from INIT to EVAL
                        // so we must integrate set properties
                        vic.setValue(vi1.getValue(), vi1.isDelayed(), sav, merged, false);
                    } else {
                        // delayed situation;
                        // not an assignment, so we must copy the statically assigned variables!
                        vic.setStaticallyAssignedVariables(sav, false);
                        merged.forEach((k, v) -> vic.setProperty(k, v, false, EVALUATION));
                    }
                }
            }
            if (vi.isDelayed()) {
                log(DELAYED, "Apply of {}, {} is delayed because of unknown value for {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                status = DELAYS;
            } else if (changeData.haveContextMethodDelay()) {
                log(DELAYED, "Apply of {}, {} is delayed because of delay in method call on {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                status = DELAYS;
            }

            LinkedVariables mergedLinkedVariables = writeMergedLinkedVariables(changeData, variable, vi, vi1);
            if (mergedLinkedVariables != LinkedVariables.DELAY && vi.isNotDelayed() || mergedLinkedVariables == EMPTY_OVERRIDE) {
                vic.setLinkedVariables(mergedLinkedVariables, false);
            } else if (vi.getLinkedVariables() == LinkedVariables.DELAY) {
                status = DELAYS;
                linkedDelays = true;
            }

            // the method analyser must have both context not null and not null expression
            // we need to revisit until we have a value (Basics_1, e.g.)
            if (variable instanceof ReturnVariable) {
                int expressionNotNull = vi.getProperty(NOT_NULL_EXPRESSION);
                if (expressionNotNull == Level.DELAY) {
                    log(DELAYED, "Apply of {}, {} is delayed because of assignment on return value without not null",
                            index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                    status = DELAYS;
                }
            }

            if (changeData.linked1Variables() == LinkedVariables.DELAY) {
                status = DELAYS;
                linked1Delays = true;
            }
        }

        // remap of statically assigned variables not seen in apply, caused by an assignment
        // IMPROVE there are some situations where field values are written directly into eval
        // in StatementAnalysis.fromFieldAnalyserIntoInitial
        if (!remapStaticallyAssignedVariables.isEmpty() && !existingVariablesNotVisited.isEmpty()) {
            for (Map.Entry<Variable, VariableInfoContainer> e : existingVariablesNotVisited.entrySet()) {
                VariableInfoContainer vic = e.getValue();
                Variable variable = e.getKey();
                VariableInfo vi1 = vic.getPreviousOrInitial();
                if (!(variable instanceof This) && !(variable instanceof ReturnVariable) && !vic.isLocalCopy()
                        && !vi1.isConfirmedVariableField()) {
                    LinkedVariables lv = remap(remapStaticallyAssignedVariables, vi1.getStaticallyAssignedVariables());
                    if (!lv.equals(vi1.getStaticallyAssignedVariables())) {
                        vic.writeStaticallyAssignedVariablesToEvaluation(lv);
                    }
                }
            }
        }

        // the second one is across clusters of variables

        addToMap(groupPropertyValues, CONTEXT_NOT_NULL, x -> x.parameterizedType().defaultNotNull(), true);
        if (statement() instanceof ForEachStatement) {
            potentiallyUpgradeCnnOfLocalLoopVariableAndCopy(sharedState.evaluationContext,
                    groupPropertyValues.getMap(EXTERNAL_NOT_NULL),
                    groupPropertyValues.getMap(CONTEXT_NOT_NULL), evaluationResult.value());
        }
        ContextPropertyWriter contextPropertyWriter = new ContextPropertyWriter();
        AnalysisStatus cnnStatus = contextPropertyWriter.write(statementAnalysis, sharedState.evaluationContext,
                VariableInfo::getStaticallyAssignedVariables,
                CONTEXT_NOT_NULL, groupPropertyValues.getMap(CONTEXT_NOT_NULL), EVALUATION, Set.of());
        status = cnnStatus.combine(status);

        addToMap(groupPropertyValues, EXTERNAL_NOT_NULL, x -> MultiLevel.DELAY, false);
        AnalysisStatus ennStatus = contextPropertyWriter.write(statementAnalysis, sharedState.evaluationContext,
                VariableInfo::getStaticallyAssignedVariables,
                EXTERNAL_NOT_NULL, groupPropertyValues.getMap(EXTERNAL_NOT_NULL), EVALUATION, Set.of());

        potentiallyRaiseErrorsOnNotNullInContext(evaluationResult.changeData());

        addToMap(groupPropertyValues, EXTERNAL_IMMUTABLE, x -> MultiLevel.NOT_INVOLVED, false);
        AnalysisStatus extImmStatus = contextPropertyWriter.write(statementAnalysis, sharedState.evaluationContext,
                VariableInfo::getStaticallyAssignedVariables,
                EXTERNAL_IMMUTABLE, groupPropertyValues.getMap(EXTERNAL_IMMUTABLE), EVALUATION, Set.of());

        addToMap(groupPropertyValues, CONTEXT_IMMUTABLE, x -> MultiLevel.NOT_INVOLVED, true);
        AnalysisStatus cImmStatus = contextPropertyWriter.write(statementAnalysis, sharedState.evaluationContext,
                VariableInfo::getStaticallyAssignedVariables,
                CONTEXT_IMMUTABLE, groupPropertyValues.getMap(CONTEXT_IMMUTABLE), EVALUATION, Set.of());
        if (cImmStatus != DONE) {
            log(DELAYED, "Context immutable causes delay in {} {}", index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
        }

        addToMap(groupPropertyValues, CONTEXT_MODIFIED, x -> Level.FALSE, true);
        // we add the linked variables on top of the statically assigned variables
        AnalysisStatus cmStatus = contextPropertyWriter.write(statementAnalysis, sharedState.evaluationContext,
                VariableInfo::getLinkedVariables,
                CONTEXT_MODIFIED, groupPropertyValues.getMap(CONTEXT_MODIFIED), EVALUATION, Set.of());
        status = cmStatus.combine(status);

        addToMap(groupPropertyValues, CONTEXT_PROPAGATE_MOD, x -> Level.FALSE, true);
        // the delay for PM is ignored (if any, it will come from CM)
        contextPropertyWriter.write(statementAnalysis, sharedState.evaluationContext,
                VariableInfo::getLinkedVariables,
                CONTEXT_PROPAGATE_MOD, groupPropertyValues.getMap(CONTEXT_PROPAGATE_MOD), EVALUATION, Set.of());

        if (!linked1Delays && !linkedDelays) {
            AnalysisStatus linked1 = new Linked1Writer(statementAnalysis, sharedState.evaluationContext,
                    VariableInfo::getStaticallyAssignedVariables).write(evaluationResult.changeData());
            status = status.combine(linked1);
        }

        // odds and ends

        evaluationResult.messages().getMessageStream().forEach(statementAnalysis::ensure);

        // not checking on DONE anymore because any delay will also have crept into the precondition itself??
        if (evaluationResult.precondition() != null) {
            Expression preconditionExpression = evaluationResult.precondition().expression();
            boolean preconditionIsDelayed = sharedState.evaluationContext
                    .isDelayed(preconditionExpression);
            if (!preconditionIsDelayed && preconditionExpression.isBoolValueFalse()) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.INCOMPATIBLE_PRECONDITION));
                setFinalAllowEquals(statementAnalysis.stateData.precondition, Precondition.empty(statementAnalysis.primitives));
            } else {
                Expression translated = sharedState.evaluationContext
                        .acceptAndTranslatePrecondition(evaluationResult.precondition().expression());
                if (translated != null) {
                    Precondition pc = new Precondition(translated, evaluationResult.precondition().causes());
                    statementAnalysis.stateData.setPrecondition(pc, preconditionIsDelayed);
                }
                if (preconditionIsDelayed) {
                    log(DELAYED, "Apply of {}, {} is delayed because of precondition",
                            index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                    status = DELAYS;
                } else {
                    Expression untranslated = evaluationResult.untranslatedPrecondition();
                    if (untranslated != null) {
                        checkPreconditionCompatibilityWithConditionManager(sharedState.evaluationContext, untranslated,
                                sharedState.localConditionManager);
                    }
                }
            }
        } else if (!statementAnalysis.stateData.precondition.isFinal()) {
            // undo a potential previous delay, so that no precondition is seen to be present
            statementAnalysis.stateData.precondition.setVariable(null);
        }

        // debugging...

        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(),
                    myMethodAnalyser.methodInfo, statementAnalysis.index, statementAnalysis, evaluationResult));
        }

        return new ApplyStatusAndEnnStatus(status, ennStatus.combine(extImmStatus
                .combine(cImmStatus.combine(immutableAtAssignment))));
    }

    private void checkPreconditionCompatibilityWithConditionManager(EvaluationContext evaluationContext,
                                                                    Expression untranslated,
                                                                    ConditionManager localConditionManager) {
        Expression result = localConditionManager.evaluate(evaluationContext, untranslated);
        if (result.isBoolValueFalse()) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.INCOMPATIBLE_PRECONDITION));
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
            if (changeData.getProperty(IN_NOT_NULL_CONTEXT) == Level.TRUE) {
                VariableInfo vi = statementAnalysis.findOrNull(variable, EVALUATION);
                if (vi != null && !(vi.variable() instanceof ParameterInfo)) {
                    int externalNotNull = vi.getProperty(VariableProperty.EXTERNAL_NOT_NULL);
                    int notNullExpression = vi.getProperty(NOT_NULL_EXPRESSION);
                    if (vi.valueIsSet() && externalNotNull == MultiLevel.NULLABLE
                            && notNullExpression == MultiLevel.NULLABLE) {
                        statementAnalysis.ensure(Message.newMessage(getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                                "Variable: " + variable.simpleName()));
                    }
                }
            }
            if (changeData.getProperty(CANDIDATE_FOR_NULL_PTR_WARNING) == Level.TRUE) {
                if (!statementAnalysis.candidateVariablesForNullPtrWarning.contains(variable)) {
                    statementAnalysis.candidateVariablesForNullPtrWarning.add(variable);
                }
            }
        }
    }

    private void potentiallyRaiseNullPointerWarningENN() {
        statementAnalysis.candidateVariablesForNullPtrWarning.stream().forEach(variable -> {
            VariableInfo vi = statementAnalysis.findOrNull(variable, VariableInfoContainer.Level.MERGE);
            int cnn = vi.getProperty(CONTEXT_NOT_NULL); // after merge, CNN should still be too low
            if (cnn < MultiLevel.EFFECTIVELY_NOT_NULL) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.CONDITION_EVALUATES_TO_CONSTANT_ENN,
                        "Variable: " + variable.fullyQualifiedName()));
            }
        });
    }

    private void potentiallyUpgradeCnnOfLocalLoopVariableAndCopy(EvaluationContext evaluationContext,
                                                                 Map<Variable, Integer> externalNotNull,
                                                                 Map<Variable, Integer> contextNotNull,
                                                                 Expression value) {
        boolean variableNotNull = evaluationContext.getProperty(value, NOT_NULL_EXPRESSION, false)
                >= MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
        if (variableNotNull) {
            Structure structure = statementAnalysis.statement.getStructure();
            LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
            Variable loopVar = lvc.localVariableReference;
            assert contextNotNull.containsKey(loopVar); // must be present!
            contextNotNull.put(loopVar, MultiLevel.EFFECTIVELY_NOT_NULL);
            externalNotNull.put(loopVar, MultiLevel.NOT_INVOLVED);

            String copy = lvc.localVariable.name() + "$" + index();
            LocalVariableReference copyVar = createLocalCopyOfLoopVariable(loopVar, copy);
            if (contextNotNull.containsKey(copyVar)) {
                // can be delayed to the next iteration
                contextNotNull.put(copyVar, MultiLevel.EFFECTIVELY_NOT_NULL);
            }
        }
    }

    private LinkedVariables remap(Map<Variable, LinkedVariables> remap, LinkedVariables linkedVariables) {
        if (linkedVariables.isEmpty()) return linkedVariables;
        Set<Variable> set = new HashSet<>(linkedVariables.variables());
        remap.forEach((v, lv) -> {
            if (set.contains(v)) {
                set.remove(v);
                set.addAll(lv.variables());
            }
        });
        return new LinkedVariables(set);
    }

    private void addToMap(GroupPropertyValues groupPropertyValues,
                          VariableProperty variableProperty,
                          Function<Variable, Integer> falseValue,
                          boolean complainDelay0) {
        Map<Variable, Integer> map = groupPropertyValues.getMap(variableProperty);
        statementAnalysis.variables.stream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            if (!map.containsKey(vi1.variable())) { // variables that don't occur in contextNotNull
                int prev = vi1.getProperty(variableProperty);
                if (prev != Level.DELAY) {
                    if (vic.hasEvaluation()) {
                        VariableInfo vi = vic.best(EVALUATION);
                        int eval = vi.getProperty(variableProperty);
                        if (eval == Level.DELAY) {
                            map.put(vi.variable(), Math.max(prev, falseValue.apply(vi.variable())));
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
                                        + ", prop " + variableProperty);
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
    private static Map<VariableProperty, Integer> mergeAssignment(Variable variable,
                                                                  boolean valueIsDelayed,
                                                                  Map<VariableProperty, Integer> valueProps,
                                                                  Map<VariableProperty, Integer> variableProps,
                                                                  Map<VariableProperty, Integer> changeData,
                                                                  GroupPropertyValues groupPropertyValues) {
        Map<VariableProperty, Integer> res = new HashMap<>(valueProps);
        variableProps.forEach(res::put);
        changeData.forEach(res::put);
        Integer enn = res.remove(EXTERNAL_NOT_NULL);
        groupPropertyValues.set(EXTERNAL_NOT_NULL, variable, enn == null ?
                (valueIsDelayed ? Level.DELAY : MultiLevel.NOT_INVOLVED) : enn);
        Integer cnn = res.remove(CONTEXT_NOT_NULL);
        groupPropertyValues.set(CONTEXT_NOT_NULL, variable, cnn == null ? MultiLevel.NULLABLE : cnn);
        Integer cm = res.remove(CONTEXT_MODIFIED);
        groupPropertyValues.set(CONTEXT_MODIFIED, variable, cm == null ? Level.FALSE : cm);
        Integer pm = res.remove(CONTEXT_PROPAGATE_MOD);
        groupPropertyValues.set(CONTEXT_PROPAGATE_MOD, variable, pm == null ? Level.FALSE : pm);
        Integer extImm = res.remove(EXTERNAL_IMMUTABLE);
        groupPropertyValues.set(EXTERNAL_IMMUTABLE, variable, extImm == null ? (valueIsDelayed ? Level.DELAY : MultiLevel.NOT_INVOLVED) : extImm);
        Integer cImm = res.remove(CONTEXT_IMMUTABLE);
        groupPropertyValues.set(CONTEXT_IMMUTABLE, variable, cImm == null ? MultiLevel.FALSE : cImm);
        return res;
    }

    private Map<VariableProperty, Integer> mergePreviousAndChange(Variable variable,
                                                                  Map<VariableProperty, Integer> previous,
                                                                  Map<VariableProperty, Integer> changeData,
                                                                  GroupPropertyValues groupPropertyValues) {
        Set<VariableProperty> both = new HashSet<>(previous.keySet());
        both.addAll(changeData.keySet());
        both.addAll(GroupPropertyValues.PROPERTIES);
        Map<VariableProperty, Integer> res = new HashMap<>(changeData);

        both.forEach(k -> {
            int prev = previous.getOrDefault(k, Level.DELAY);
            int change = changeData.getOrDefault(k, Level.DELAY);
            if (GroupPropertyValues.PROPERTIES.contains(k)) {
                int value = switch (k) {
                    case EXTERNAL_IMMUTABLE -> prev != Level.DELAY ? Math.max(MultiLevel.DELAY, prev) : Level.DELAY;
                    case CONTEXT_IMMUTABLE -> {
                        if (changeData.getOrDefault(CONTEXT_IMMUTABLE_DELAY, Level.DELAY) != Level.TRUE && prev != Level.DELAY) {
                            yield Math.max(prev, change);
                        } else {
                            yield Level.DELAY;
                        }
                    }
                    // values simply travel downward (delay until there's a value from another analyser)
                    case EXTERNAL_NOT_NULL -> prev != Level.DELAY ? Math.max(MultiLevel.DELAY, prev) : Level.DELAY;
                    case CONTEXT_NOT_NULL -> {
                        if (changeData.getOrDefault(CONTEXT_NOT_NULL_DELAY, Level.DELAY) != Level.TRUE && prev != Level.DELAY) {
                            yield Math.max(variable.parameterizedType().defaultNotNull(), Math.max(prev, change));
                        } else {
                            yield Level.DELAY;
                        }
                    }
                    case CONTEXT_MODIFIED -> {
                        if (changeData.getOrDefault(CONTEXT_MODIFIED_DELAY, Level.DELAY) != Level.TRUE && prev != Level.DELAY) {
                            yield Math.max(Level.FALSE, Math.max(prev, change));
                        } else {
                            yield Level.DELAY;
                        }
                    }
                    case CONTEXT_PROPAGATE_MOD -> Math.max(Level.FALSE, Math.max(prev, change));
                    default -> throw new UnsupportedOperationException();
                };
                groupPropertyValues.set(k, variable, value);
            } else {
                switch (k) {
                    // value properties are copied from previous, because there is NO assignment!
                    case CONTAINER, IMMUTABLE, NOT_NULL_EXPRESSION, MODIFIED_OUTSIDE_METHOD, IDENTITY, FLUENT -> {
                        if (prev != Level.DELAY) res.put(k, prev);
                    }
                    // all other are copied from change data
                    default -> {
                        if (change != Level.DELAY) res.put(k, change);
                    }
                }
            }
        });
        res.keySet().removeAll(GroupPropertyValues.PROPERTIES);
        return res;
    }

    /*
    first of all in 'apply' we need to ensure that all variables exist, and have a proper assignmentId and readId.

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
        VariableInfo initial;
        if (!statementAnalysis.variables.isSet(variable.fullyQualifiedName())) {
            vic = statementAnalysis.createVariable(evaluationContext, variable,
                    statementAnalysis.flowData.getInitialTime(), VariableInLoop.NOT_IN_LOOP);
            initial = vic.getPreviousOrInitial();
            if (initial.variable().needsNewVariableWithoutValueCall()) {
                vic.newVariableWithoutValue();
            }
        } else {
            vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
            initial = vic.getPreviousOrInitial();
        }

        if (variable instanceof FieldReference fieldReference &&
                initial.isConfirmedVariableField() && !changeData.readAtStatementTime().isEmpty()) {
            for (int statementTime : changeData.readAtStatementTime()) {
                statementAnalysis.variableInfoOfFieldWhenReading(analyserContext, fieldReference, initial, statementTime);
            }
        }
        String id = index() + EVALUATION;
        String assignmentId = changeData.markAssignment() ? id : initial.getAssignmentId();
        // we do not set readId to the empty set when markAssignment... we'd rather keep the old value
        // we will compare the recency anyway
        String readId = changeData.readAtStatementTime().isEmpty() ? initial.getReadId() : id;
        int statementTime = statementAnalysis.statementTimeForVariable(analyserContext, variable, newStatementTime);

        vic.ensureEvaluation(assignmentId, readId, statementTime, changeData.readAtStatementTime());
    }

    private static final LinkedVariables EMPTY_OVERRIDE = new LinkedVariables(Set.of());

    private LinkedVariables writeMergedLinkedVariables(EvaluationResult.ChangeData changeData,
                                                       Variable variable,
                                                       VariableInfo vi,
                                                       VariableInfo vi1) {
        // regardless of what's being delayed or not, if the type is immutable there cannot be links
        TypeInfo bestType = variable.parameterizedType().bestTypeInfo();
        if (bestType != null && bestType != myMethodAnalyser.methodInfo.typeInfo) {
            int immutable = analyserContext.getTypeAnalysis(bestType).getProperty(IMMUTABLE);
            if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                return EMPTY_OVERRIDE;
            }
        }
        if (changeData.linkedVariables() == LinkedVariables.DELAY) {
            log(DELAYED, "Apply of {}, {} is delayed because of linked variables of {}",
                    index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                    variable.fullyQualifiedName());
            return LinkedVariables.DELAY;
        }

        if (vi.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            log(DELAYED, "Apply of step {}, {} is delayed because of variable field delay",
                    index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            return LinkedVariables.DELAY;
        }

        // no assignment, we need to copy, potentially add to previous value

        LinkedVariables previousValue = vi1.getLinkedVariables();
        LinkedVariables toAddFromPreviousValue;
        if (changeData.markAssignment()) {
            if (vi.isConfirmedVariableField()) {
                if (previousValue == LinkedVariables.DELAY) {
                    log(DELAYED, "Apply of {}, {} is delayed because of previous value delay of linked variables of variable field {}",
                            index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                            variable.fullyQualifiedName());
                    return LinkedVariables.DELAY;
                }
                toAddFromPreviousValue = previousValue.removeAllButLocalCopiesOf(variable);
            } else {
                toAddFromPreviousValue = LinkedVariables.EMPTY;
            }
        } else {
            if (previousValue == LinkedVariables.DELAY) {
                log(DELAYED, "Apply of {}, {} is delayed because of previous value delay of linked variables of {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                        variable.fullyQualifiedName());
                return LinkedVariables.DELAY;
            }
            toAddFromPreviousValue = previousValue;
        }
        // note that the null here is actual presence or absence in a map...
        LinkedVariables mergedValue = toAddFromPreviousValue.merge(changeData.linkedVariables());
        log(ANALYSER, "Set linked variables of {} to {} in {}, {}",
                variable, mergedValue, index(), myMethodAnalyser.methodInfo.fullyQualifiedName);

        assert mergedValue != LinkedVariables.DELAY;
        return mergedValue;
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
    private Expression maybeValueNeedsState(SharedState sharedState, VariableInfoContainer vic, VariableInfo vi1,
                                            Expression value) {
        boolean valueIsDelayed = sharedState.evaluationContext.isDelayed(value);
        if (valueIsDelayed ||
                vic.getVariableInLoop().variableType() != VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE) {
            // not applicable
            return value;
        }
        ConditionManager localConditionManager = sharedState.localConditionManager;
        //if (localConditionManager.isDelayed()) return NoValue.EMPTY;
        Expression state;
        if (!localConditionManager.state().isBoolValueTrue()) {
            state = localConditionManager.state();
        } else if (!localConditionManager.condition().isBoolValueTrue()) {
            state = localConditionManager.condition();
        } else {
            state = null;
        }
        if (state != null) {
            // do not take vi1 itself, but "the" local copy of the variable
            Expression valueOfVariablePreAssignment = sharedState.evaluationContext.currentValue(vi1.variable(),
                    statementAnalysis.statementTime(VariableInfoContainer.Level.INITIAL), true);
            InlineConditional inlineConditional = new InlineConditional(analyserContext, state, value, valueOfVariablePreAssignment);
            return inlineConditional.optimise(sharedState.evaluationContext);
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
        while (sa != null) {
            if (!sa.variables.isSet(fullyQualifiedName)) return null;
            VariableInfoContainer localVic = sa.variables.get(fullyQualifiedName);
            if (!localVic.isLocalVariableInLoopDefinedOutside()) return null;
            if (sa.statement instanceof LoopStatement) {
                if (!sa.localVariablesAssignedInThisLoop.contains(fullyQualifiedName)) {
                    sa.localVariablesAssignedInThisLoop.add(fullyQualifiedName);
                }
                loopIndex = sa.index;
            }
            sa = sa.parent;
        }
        assert loopIndex != null;

        VariableInfo vi = vic.best(EVALUATION);
        String newFqn = statementAnalysis.createLocalLoopCopyFQN(vic, vi);
        if (!statementAnalysis.variables.isSet(newFqn)) {
            LocalVariableReference newLvr = createLocalCopyOfLoopVariable(vi.variable(), newFqn);
            VariableInLoop variableInLoop = new VariableInLoop(loopIndex, index(), VariableInLoop.VariableType.LOOP_COPY);
            VariableInfoContainer newVic = VariableInfoContainerImpl.newVariable(newLvr,
                    VariableInfoContainer.NOT_A_VARIABLE_FIELD, variableInLoop, navigationData.hasSubBlocks());
            newVic.newVariableWithoutValue(); // at initial level
            newVic.setStaticallyAssignedVariables(new LinkedVariables(Set.of(vi.variable())), true);
            String assigned = index() + VariableInfoContainer.Level.INITIAL;
            String read = index() + EVALUATION;
            newVic.ensureEvaluation(assigned, read, VariableInfoContainer.NOT_A_VARIABLE_FIELD, Set.of());

            statementAnalysis.variables.put(newFqn, newVic);

            // value and properties will be set in main apply
            return newVic;
        }
        return statementAnalysis.variables.get(newFqn);
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
        Boolean escapeAlwaysExecuted = isEscapeAlwaysExecutedInCurrentBlock();
        boolean delays = escapeAlwaysExecuted == null || statementAnalysis.stateData.conditionManagerForNextStatement.isVariable();
        if (escapeAlwaysExecuted != Boolean.FALSE) {
            Set<Variable> nullVariables = statementAnalysis.stateData.conditionManagerForNextStatement.get()
                    .findIndividualNullInCondition(sharedState.evaluationContext, true);
            for (Variable nullVariable : nullVariables) {
                log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.fullyQualifiedName());

                // move from condition (x!=null) to property
                VariableInfoContainer vic = statementAnalysis.findForWriting(nullVariable);
                if (!vic.hasEvaluation()) {
                    VariableInfo initial = vic.getPreviousOrInitial();
                    vic.ensureEvaluation(initial.getAssignmentId(), initial.getReadId(), initial.getStatementTime(), initial.getReadAtStatementTimes());
                }
                if (delays) {
                    vic.setProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, Level.TRUE, EVALUATION);
                } else {
                    vic.setProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, Level.TRUE, EVALUATION);
                    if (escapeAlwaysExecuted) {
                        vic.setProperty(CONTEXT_NOT_NULL_FOR_PARENT, MultiLevel.EFFECTIVELY_NOT_NULL, EVALUATION);
                    }
                }
                if (nullVariable instanceof LocalVariableReference lvr && lvr.variable.isLocalCopyOf() instanceof FieldReference fr) {
                    VariableInfoContainer vicF = statementAnalysis.findForWriting(fr);
                    if (!vicF.hasEvaluation()) {
                        VariableInfo initial = vicF.getPreviousOrInitial();
                        vicF.ensureEvaluation(initial.getAssignmentId(), initial.getReadId(), initial.getStatementTime(), initial.getReadAtStatementTimes());
                    }
                    if (delays) {
                        vicF.setProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, Level.TRUE, EVALUATION);
                    } else {
                        vicF.setProperty(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, Level.TRUE, EVALUATION);
                        if (escapeAlwaysExecuted) {
                            vicF.setProperty(CONTEXT_NOT_NULL_FOR_PARENT, MultiLevel.EFFECTIVELY_NOT_NULL, EVALUATION);
                        }
                    }
                }
            }
            if (!delays && escapeAlwaysExecuted) {
                // escapeCondition should filter out all != null, == null clauses
                Expression precondition = statementAnalysis.stateData.conditionManagerForNextStatement.get()
                        .precondition(sharedState.evaluationContext);
                boolean preconditionIsDelayed = sharedState.evaluationContext.isDelayed(precondition);
                Expression translated = sharedState.evaluationContext.acceptAndTranslatePrecondition(precondition);
                if (translated != null) {
                    log(VARIABLE_PROPERTIES, "Escape with precondition {}", translated);
                    Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
                    statementAnalysis.stateData.setPrecondition(pc, preconditionIsDelayed);
                    return preconditionIsDelayed ? DELAYS : DONE;
                }
            }

            if (delays) return DELAYS;
        }
        if (statementAnalysis.stateData.preconditionIsEmpty()) {
            // it could have been set from the assert (step4) or apply via a method call
            setFinalAllowEquals(statementAnalysis.stateData.precondition, Precondition.empty(statementAnalysis.primitives));
        } else if (statementAnalysis.stateData.precondition.isVariable()) {
            return DELAYS;
        }
        return DONE;
    }

    /*
    we create the variable(s), to make sure they exist in INIT level, but defer computation of their value to step 3.
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
    private List<Expression> initialisersAndUpdaters(SharedState sharedState) {
        List<Expression> expressionsToEvaluate = new ArrayList<>();

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e), or for(int i=...), or int i=3, j=4;
        // variable will be set to a NewObject case of a catch

        if (sharedState.forwardAnalysisInfo.catchVariable() != null) {
            // inject a catch(E1 | E2 e) { } exception variable, directly with assigned value, "read"
            LocalVariableCreation catchVariable = sharedState.forwardAnalysisInfo.catchVariable();
            String name = catchVariable.localVariable.name();
            if (!statementAnalysis.variables.isSet(name)) {
                LocalVariableReference lvr = new LocalVariableReference(analyserContext, catchVariable.localVariable, List.of());
                VariableInfoContainer vic = VariableInfoContainerImpl.newCatchVariable(lvr, index(),
                        NewObject.forCatchOrThis(index() + "-" + lvr.fullyQualifiedName(),
                                statementAnalysis.primitives, lvr.parameterizedType()),
                        statementAnalysis.navigationData.hasSubBlocks());
                statementAnalysis.variables.put(name, vic);
            }
        }

        for (Expression expression : statementAnalysis.statement.getStructure().initialisers()) {
            Expression initialiserToEvaluate;

            if (expression instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr;
                VariableInfoContainer vic;
                boolean newVariable;
                String name = lvc.localVariable.name();
                if (!statementAnalysis.variables.isSet(name)) {

                    // create the local (loop) variable

                    lvr = new LocalVariableReference(analyserContext, lvc.localVariable, List.of());
                    VariableInLoop variableInLoop = statement() instanceof LoopStatement ?
                            new VariableInLoop(index(), null, VariableInLoop.VariableType.LOOP) : VariableInLoop.NOT_IN_LOOP;
                    vic = VariableInfoContainerImpl.newVariable(lvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                            variableInLoop, statementAnalysis.navigationData.hasSubBlocks());
                    newVariable = true;
                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.localVariablesAssignedInThisLoop.add(lvr.fullyQualifiedName());
                    }
                    statementAnalysis.variables.put(name, vic);
                } else {
                    vic = statementAnalysis.variables.get(name);
                    lvr = (LocalVariableReference) vic.current().variable();
                    newVariable = false;
                }

                // what should we evaluate? catch: assign a value which will be read; for(int i=0;...) --> 0 instead of i=0;
                if (statement() instanceof LoopStatement) {
                    initialiserToEvaluate = lvc.expression;
                    // but, because we don't evaluate the assignment, we need to assign some value to the loop variable
                    // otherwise we'll get delays
                    // especially in the case of forEach, the lvc.expression is empty, anyway
                    // an assignment may be difficult. The value is never used, only local copies are

                    Map<VariableProperty, Integer> properties =
                            Map.of(CONTEXT_MODIFIED, Level.FALSE,
                                    CONTEXT_PROPAGATE_MOD, Level.FALSE,
                                    EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED,
                                    CONTEXT_NOT_NULL, lvr.parameterizedType().defaultNotNull(),
                                    EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED,
                                    CONTEXT_IMMUTABLE, lvr.parameterizedType().defaultImmutable(analyserContext));

                    vic.setValue(NewObject.forCatchOrThis(index() + "-" + name,
                            statementAnalysis.primitives, lvr.parameterizedType()), false,
                            LinkedVariables.EMPTY, properties, true);
                    vic.setLinkedVariables(LinkedVariables.EMPTY, true);
                } else {
                    initialiserToEvaluate = lvc; // == expression
                    if (newVariable) {
                        vic.newVariableWithoutValue();
                    }
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
                    if (e instanceof Assignment assignment && assignment.target instanceof VariableExpression ve) {
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

    private List<Expression> localVariablesInLoop(SharedState sharedState) {
        if (statementAnalysis.localVariablesAssignedInThisLoop == null) {
            return List.of(); // not for us
        }
        // part 3, iteration 1+: ensure local loop variable copies and their values

        if (!statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) {
            return null; // DELAY
        }
        List<Expression> expressionsToEvaluate = new ArrayList<>();
        statementAnalysis.localVariablesAssignedInThisLoop.stream().forEach(fqn -> {
            assert statement() instanceof LoopStatement;

            VariableInfoContainer vic = statementAnalysis.findForWriting(fqn); // must exist already
            VariableInfo vi = vic.best(EVALUATION); // NOT merge, merge is after the loop
            // assign to local variable that has been created at Level 2 in this statement
            String newFqn = fqn + "$" + index(); // must be compatible with statementAnalysis.createLocalLoopCopyFQN
            if (!statementAnalysis.variables.isSet(newFqn)) {
                LocalVariableReference newLvr = createLocalCopyOfLoopVariable(vi.variable(), newFqn);
                String assigned = index() + VariableInfoContainer.Level.INITIAL;
                String read = index() + EVALUATION;
                Expression newValue = NewObject.localVariableInLoop(index() + "-" + newFqn,
                        statementAnalysis.primitives, newLvr.parameterizedType());
                Map<VariableProperty, Integer> valueProps = sharedState.evaluationContext.getValueProperties(newValue);
                VariableInfoContainer newVic = VariableInfoContainerImpl.newLoopVariable(newLvr, assigned,
                        read,
                        newValue,
                        mergeValueAndLoopVar(valueProps, vi.getProperties().toImmutableMap()),
                        new LinkedVariables(Set.of(vi.variable())),
                        new VariableInLoop(index(), null, VariableInLoop.VariableType.LOOP_COPY),
                        true);
                statementAnalysis.variables.put(newFqn, newVic);
            }
        });
        return expressionsToEvaluate;
    }

    private static Map<VariableProperty, Integer> mergeValueAndLoopVar(Map<VariableProperty, Integer> value,
                                                                       Map<VariableProperty, Integer> loopVar) {
        Map<VariableProperty, Integer> res = new HashMap<>(value);
        loopVar.forEach(res::put);
        return res;
    }

    private LocalVariableReference createLocalCopyOfLoopVariable(Variable loopVariable, String newFqn) {
        LocalVariable localVariable = new LocalVariable.Builder()
                .addModifier(LocalVariableModifier.FINAL)
                .setName(newFqn)
                .setParameterizedType(loopVariable.parameterizedType())
                .setOwningType(myMethodAnalyser.methodInfo.typeInfo)
                .setIsLocalCopyOf(loopVariable)
                .build();
        return new LocalVariableReference(analyserContext, localVariable, List.of());
    }

    private AnalysisStatus evaluationOfMainExpression(SharedState sharedState) {
        List<Expression> expressionsFromInitAndUpdate = initialisersAndUpdaters(sharedState);
        List<Expression> expressionsFromLocalVariablesInLoop = localVariablesInLoop(sharedState);
        /*
        if we're in a loop statement and there are delays (localVariablesAssignedInThisLoop not frozen)
        we have to come back!
         */
        AnalysisStatus analysisStatus = expressionsFromLocalVariablesInLoop == null ? DELAYS : DONE;
        if (expressionsFromLocalVariablesInLoop != null) {
            expressionsFromInitAndUpdate.addAll(expressionsFromLocalVariablesInLoop);
        }

        Structure structure = statementAnalysis.statement.getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
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
                boolean stateIsDelayed = sharedState.evaluationContext.isDelayed(state);
                correspondingLoop.statementAnalysis().stateData.addStateOfInterrupt(index(), state, stateIsDelayed);
                if (sharedState.evaluationContext.isDelayed(state)) return DELAYS;
            } else if (statement() instanceof LocalClassDeclaration localClassDeclaration) {
                EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext);
                PrimaryTypeAnalyser primaryTypeAnalyser =
                        localAnalysers.get().stream().filter(pta -> pta.primaryType == localClassDeclaration.typeInfo).findFirst().orElseThrow();
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
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterEvaluation(result.statementTime(), index());
            }
            ApplyStatusAndEnnStatus applyResult = apply(sharedState, result);
            AnalysisStatus statusPost = applyResult.status.combine(analysisStatus);
            AnalysisStatus ennStatus = applyResult.ennStatus;

            if (statementAnalysis.statement instanceof ExplicitConstructorInvocation eci) {
                Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, result);
                if (!assignments.isBooleanConstant()) {
                    result = assignments.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());
                    ApplyStatusAndEnnStatus assignmentResult = apply(sharedState, result);
                    statusPost = assignmentResult.status.combine(analysisStatus);
                    ennStatus = applyResult.ennStatus.combine(assignmentResult.ennStatus);
                }
            }

            Expression value = result.value();
            assert value != null; // EmptyExpression in case there really is no value
            boolean valueIsDelayed = sharedState.evaluationContext.isDelayed(value) || statusPost != DONE;

            if (!valueIsDelayed && (statementAnalysis.statement instanceof IfElseStatement ||
                    statementAnalysis.statement instanceof AssertStatement)) {
                value = step3_IfElse_Assert(sharedState, value);
            } else if (!valueIsDelayed && statementAnalysis.statement instanceof SwitchStatement switchStatement) {
                step3_Switch(sharedState, value, switchStatement);
            }

            // the value can be delayed even if it is "true", for example (Basics_3)
            // see Precondition_3 for an example where different values arise, because preconditions kick in
            boolean valueIsDelayed2 = sharedState.evaluationContext.isDelayed(value) || statusPost != DONE;
            if (statementAnalysis.stateData.valueOfExpression.isVariable()) { // FIXME this check should probably go; effect of another error
                statementAnalysis.stateData.setValueOfExpression(value, valueIsDelayed2);
            }

            if (ennStatus != DONE) {
                log(DELAYED, "Delaying statement {} in {} because of external not null/external immutable",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            }
            return ennStatus.combine(statusPost);
        } catch (RuntimeException rte) {
            LOGGER.warn("Failed to evaluate main expression (step 3) in statement {}", statementAnalysis.index);
            throw rte;
        }
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
        This thisVar = new This(analyserContext, myMethodAnalyser.methodInfo.typeInfo);
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
            for (VariableInfo variableInfo : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                if (variableInfo.isAssigned()) {
                    EvaluationResult translated = variableInfo.getValue()
                            .reEvaluate(sharedState.evaluationContext, translation);
                    Assignment assignment = new Assignment(statementAnalysis.primitives,
                            new VariableExpression(new FieldReference(analyserContext, fieldInfo, thisVar)),
                            translated.value(), null, null, false);
                    builder.compose(translated);
                    assignments.add(assignment);
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
            if (myMethodAnalyser.methodInfo.returnType().equals(statementAnalysis.primitives.booleanParameterizedType)) {
                // state, boolean; evaluation of And will add clauses to the context one by one
                toEvaluate = new And(statementAnalysis.primitives).append(evaluationContext, localConditionManager.state(),
                        structure.expression());
            } else {
                // state, not boolean
                InlineConditional inlineConditional = new InlineConditional(analyserContext, localConditionManager.state(),
                        structure.expression(), currentReturnValue);
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
    private void step3_Switch(SharedState sharedState, Expression switchExpression, SwitchStatement switchStatement) {
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
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.TRIVIAL_CASES_IN_SWITCH, msg));
        }
    }

    private Expression step3_IfElse_Assert(SharedState sharedState, Expression value) {
        assert value != null;

        Expression evaluated = sharedState.localConditionManager.evaluate(sharedState.evaluationContext, value);

        if (evaluated.isConstant()) {
            String message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData.blocks.get();
            if (statementAnalysis.statement instanceof IfElseStatement) {
                message = Message.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = evaluated.isBoolValueTrue();
                    if (!isTrue) {
                        firstStatement.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo,
                                firstStatement.index), Message.UNREACHABLE_STATEMENT));
                    }
                    // guaranteed to be reached in block is always ALWAYS because it is the first statement
                    firstStatement.flowData.setGuaranteedToBeReachedInMethod(isTrue ? ALWAYS : NEVER);
                });
                if (blocks.size() == 2) {
                    blocks.get(1).ifPresent(firstStatement -> {
                        boolean isTrue = evaluated.isBoolValueTrue();
                        if (isTrue) {
                            firstStatement.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo,
                                    firstStatement.index), Message.UNREACHABLE_STATEMENT));
                        }
                        firstStatement.flowData.setGuaranteedToBeReachedInMethod(isTrue ? NEVER : ALWAYS);
                    });
                }
            } else if (statementAnalysis.statement instanceof AssertStatement) {
                boolean isTrue = evaluated.isBoolValueTrue();
                if (isTrue) {
                    message = Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE;
                } else {
                    message = Message.ASSERT_EVALUATES_TO_CONSTANT_FALSE;
                    Optional<StatementAnalysis> next = statementAnalysis.navigationData.next.get();
                    next.ifPresent(nextAnalysis -> {
                        nextAnalysis.flowData.setGuaranteedToBeReached(NEVER);
                        nextAnalysis.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo, nextAnalysis.index),
                                Message.UNREACHABLE_STATEMENT));
                    });
                }
            } else throw new UnsupportedOperationException();
            statementAnalysis.ensure(Message.newMessage(sharedState.evaluationContext.getLocation(), message));
            return evaluated;
        }
        return value;
    }

    private AnalysisStatus subBlocks(SharedState sharedState) {
        List<Optional<StatementAnalyser>> startOfBlocks = navigationData.blocks.get();
        AnalysisStatus analysisStatus = sharedState.localConditionManager.isDelayed() ? DELAYS : DONE;

        if (!startOfBlocks.isEmpty()) {
            return step4_haveSubBlocks(sharedState, startOfBlocks).combine(analysisStatus);
        }

        if (statementAnalysis.statement instanceof AssertStatement) {
            Expression assertion = statementAnalysis.stateData.valueOfExpression.get();
            boolean expressionIsDelayed = statementAnalysis.stateData.valueOfExpression.isVariable();
            Expression translated = Objects.requireNonNullElse(
                    sharedState.evaluationContext.acceptAndTranslatePrecondition(assertion),
                    new BooleanConstant(statementAnalysis.primitives, true));
            Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
            statementAnalysis.stateData.setPrecondition(pc, expressionIsDelayed);

            if (expressionIsDelayed) {
                analysisStatus = DELAYS;
            }
        }

        if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
            statementAnalysis.flowData.copyTimeAfterSubBlocksFromTimeAfterExecution();
        }

        statementAnalysis.stateData.setLocalConditionManagerForNextStatement(sharedState.localConditionManager);
        return analysisStatus;
    }

    public StatementAnalyser navigateTo(String index) {
        String myIndex = index();
        if (myIndex.equals(index)) return this;
        if (index.startsWith(myIndex)) {
            // go into sub-block
            int n = myIndex.length();
            int blockIndex = Integer.parseInt(index.substring(n + 1, index.indexOf('.', n + 1)));
            return navigationData.blocks.get().get(blockIndex)
                    .orElseThrow(() -> new UnsupportedOperationException("Looking for " + index + ", block " + blockIndex));
        }
        if (myIndex.compareTo(index) < 0 && navigationData.next.get().isPresent()) {
            return navigationData.next.get().get().navigateTo(index);
        }
        throw new UnsupportedOperationException("? have index " + myIndex + ", looking for " + index);
    }

    private record ExecutionOfBlock(FlowData.Execution execution,
                                    StatementAnalyser startOfBlock,
                                    ConditionManager conditionManager,
                                    Expression condition,
                                    boolean isDefault,
                                    LocalVariableCreation catchVariable) {

        public boolean escapesAlwaysButNotWithPrecondition() {
            if (execution != NEVER && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().statementAnalysis;
                return lastStatement.flowData.interruptStatus() == ALWAYS && !lastStatement.flowData.escapesViaException();
            }
            return false;
        }

        public boolean escapesAlways() {
            if (execution != NEVER && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().statementAnalysis;
                return lastStatement.flowData.interruptStatus() == ALWAYS;
            }
            return false;
        }

        public boolean alwaysExecuted() {
            return execution == ALWAYS && startOfBlock != null;
        }
    }

    private AnalysisStatus step4_haveSubBlocks(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        List<ExecutionOfBlock> executions = step4a_determineExecution(sharedState, startOfBlocks);
        AnalysisStatus analysisStatus = DONE;


        int blocksExecuted = 0;
        for (ExecutionOfBlock executionOfBlock : executions) {
            if (executionOfBlock.startOfBlock != null) {
                if (executionOfBlock.execution != NEVER) {
                    ForwardAnalysisInfo forward;
                    if (statement() instanceof SwitchStatementOldStyle switchStatement) {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                switchStatement.startingPointToLabels(evaluationContext, executionOfBlock.startOfBlock.statementAnalysis),
                                statementAnalysis.stateData.valueOfExpression.get(),
                                statementAnalysis.stateData.valueOfExpression.isVariable());
                    } else {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable, null, null, false);
                    }
                    StatementAnalyserResult result = executionOfBlock.startOfBlock.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                            forward, evaluationContext.getClosure());
                    sharedState.builder.add(result);
                    analysisStatus = analysisStatus.combine(result.analysisStatus);
                    blocksExecuted++;
                } else {
                    // ensure that the first statement is unreachable
                    FlowData flowData = executionOfBlock.startOfBlock.statementAnalysis.flowData;
                    flowData.setGuaranteedToBeReachedInMethod(NEVER);

                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.ensure(Message.newMessage(getLocation(), Message.EMPTY_LOOP));
                    }

                    sharedState.builder.addMessages(executionOfBlock.startOfBlock.statementAnalysis.messages.stream());
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
                                ex.startOfBlock.lastStatement().isEscapeAlwaysExecutedInCurrentBlock() == Boolean.TRUE))
                        .collect(Collectors.toUnmodifiableList());
                maxTime = lastStatements.stream()
                        .map(StatementAnalysis.ConditionAndLastStatement::lastStatement)
                        .mapToInt(sa -> sa.statementAnalysis.flowData.getTimeAfterSubBlocks())
                        .max().orElseThrow();
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
                log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", addToStateAfterStatement);
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
            boolean alwaysEscapes = statementAnalysis.flowData.escapesViaException();
            return new StatementAnalysis.ConditionAndLastStatement(e.getValue(), e.getKey(), lastStatement, alwaysEscapes);
        }).collect(Collectors.toUnmodifiableList());
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
                list.stream().allMatch(e -> (e.execution == CONDITIONALLY || e.execution == DELAYED_EXECUTION)
                        && e.startOfBlock != null);
    }

    private Expression addToStateAfterStatement(EvaluationContext evaluationContext, List<ExecutionOfBlock> list) {
        BooleanConstant TRUE = new BooleanConstant(evaluationContext.getPrimitives(), true);
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
        if (statementAnalysis.statement instanceof SwitchStatement) {
            Expression[] components = list.stream().filter(ExecutionOfBlock::escapesAlwaysButNotWithPrecondition)
                    .map(e -> e.condition).toArray(Expression[]::new);
            if (components.length == 0) return TRUE;
            return new And(evaluationContext.getPrimitives()).append(evaluationContext, components);
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
            return new Or(statementAnalysis.primitives).append(evaluationContext, ors);
        }

        if (statementAnalysis.statement instanceof SynchronizedStatement && list.get(0).startOfBlock != null) {
            Expression lastState = list.get(0).startOfBlock.lastStatement()
                    .statementAnalysis.stateData.conditionManagerForNextStatement.get().state();
            return evaluationContext.replaceLocalVariables(lastState);
        }
        return TRUE;
    }


    private List<ExecutionOfBlock> step4a_determineExecution(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        List<ExecutionOfBlock> executions = new ArrayList<>(startOfBlocks.size());

        Expression value = statementAnalysis.stateData.valueOfExpression.get();
        boolean valueIsDelayed = statementAnalysis.stateData.valueOfExpression.isVariable();
        assert !sharedState.evaluationContext.isDelayed(value) || valueIsDelayed; // sanity check
        Structure structure = statementAnalysis.statement.getStructure();
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        // main block

        // some loops are never executed, and we can see that
        FlowData.Execution firstBlockStatementsExecution = structure.statementExecution().apply(value, evaluationContext);
        FlowData.Execution firstBlockExecution = statementAnalysis.flowData.execution(firstBlockStatementsExecution);

        executions.add(makeExecutionOfPrimaryBlock(sharedState.localConditionManager, firstBlockExecution, startOfBlocks, value,
                valueIsDelayed));

        for (int count = 1; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements().get(count - 1);
            Expression conditionForSubStatement;
            boolean conditionForSubStatementIsDelayed;

            boolean isDefault = false;
            FlowData.Execution statementsExecution = subStatements.statementExecution().apply(value, evaluationContext);
            if (statementsExecution == FlowData.Execution.DEFAULT) {
                isDefault = true;
                conditionForSubStatement = defaultCondition(evaluationContext, executions);
                conditionForSubStatementIsDelayed = evaluationContext.isDelayed(conditionForSubStatement);
                if (conditionForSubStatement.isBoolValueFalse()) statementsExecution = NEVER;
                else if (conditionForSubStatement.isBoolValueTrue()) statementsExecution = ALWAYS;
                else if (conditionForSubStatementIsDelayed) statementsExecution = DELAYED_EXECUTION;
                else statementsExecution = CONDITIONALLY;
            } else if (statementsExecution == ALWAYS) {
                conditionForSubStatement = new BooleanConstant(statementAnalysis.primitives, true);
                conditionForSubStatementIsDelayed = false;
            } else if (statementsExecution == NEVER) {
                conditionForSubStatement = null; // will not be executed anyway
                conditionForSubStatementIsDelayed = false;
            } else if (statement() instanceof TryStatement) { // catch
                conditionForSubStatement = NewObject.forCatchOrThis(index() + "-condition",
                        statementAnalysis.primitives, statementAnalysis.primitives.booleanParameterizedType);
                conditionForSubStatementIsDelayed = false;
            } else if (statement() instanceof SwitchEntry switchEntry) {
                Expression constant = switchEntry.switchVariableAsExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT).value();
                conditionForSubStatement = Equals.equals(evaluationContext, value, constant);
                conditionForSubStatementIsDelayed = evaluationContext.isDelayed(conditionForSubStatement);
            } else throw new UnsupportedOperationException();

            FlowData.Execution execution = statementAnalysis.flowData.execution(statementsExecution);

            ConditionManager subCm = execution == NEVER ? null :
                    sharedState.localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives,
                            conditionForSubStatement, conditionForSubStatementIsDelayed);
            boolean inCatch = statement() instanceof TryStatement && !subStatements.initialisers().isEmpty(); // otherwise, it is finally
            LocalVariableCreation catchVariable = inCatch ? (LocalVariableCreation) subStatements.initialisers().get(0) : null;
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm,
                    conditionForSubStatement, isDefault, catchVariable));
        }

        return executions;
    }

    private ExecutionOfBlock makeExecutionOfPrimaryBlock(ConditionManager localConditionManager,
                                                         FlowData.Execution firstBlockExecution,
                                                         List<Optional<StatementAnalyser>> startOfBlocks,
                                                         Expression value,
                                                         boolean valueIsDelayed) {
        Structure structure = statementAnalysis.statement.getStructure();
        Expression condition;
        ConditionManager cm;
        if (firstBlockExecution == NEVER) {
            cm = null;
            condition = null;
        } else if (structure.expressionIsCondition()) {
            cm = localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives, value,
                    valueIsDelayed);
            condition = value;
        } else {
            cm = localConditionManager;
            condition = new BooleanConstant(statementAnalysis.primitives, true);
        }
        return new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, condition,
                false, null);
    }

    private Expression defaultCondition(EvaluationContext evaluationContext, List<ExecutionOfBlock> executions) {
        Primitives primitives = evaluationContext.getPrimitives();
        List<Expression> previousConditions = executions.stream().map(e -> e.condition).collect(Collectors.toList());
        if (previousConditions.isEmpty()) {
            return new BooleanConstant(primitives, true);
        }
        Expression[] negated = previousConditions.stream().map(c -> Negation.negate(evaluationContext, c))
                .toArray(Expression[]::new);
        return new And(primitives).append(evaluationContext, negated);
    }

    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li NYR + local variable created higher up + return: <code>int i=0; if(xxx) { i=3; return; }</code></li>
     *     <li>NYR + escape: <code>int i=0; if(xxx) { i=3; throw new UnsupportedOperationException(); }</code></li>
     * </ul>
     */
    private AnalysisStatus checkUselessAssignments() {
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData.bestAlwaysInterrupt();
        if (bestAlwaysInterrupt == InterruptsFlow.DELAYED) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return DELAYS;
        }
        FlowData.Execution reached = statementAnalysis.flowData.getGuaranteedToBeReachedInMethod();
        if (reached == DELAYED_EXECUTION) return DELAYS;

        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().getVariableInLoop() != VariableInLoop.COPY_FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof ReturnVariable)) // that's for the compiler!
                    .forEach(variableInfo -> {
                        if (variableInfo.notReadAfterAssignment() && uselessForDependentVariable(variableInfo)) {
                            boolean isLocalAndLocalToThisBlock = statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name());
                            if (bestAlwaysInterrupt == InterruptsFlow.ESCAPE ||
                                    isLocalAndLocalToThisBlock ||
                                    variableInfo.variable().isLocal() && bestAlwaysInterrupt == InterruptsFlow.RETURN &&
                                            localVariableAssignmentInThisBlock(variableInfo)) {
                                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.USELESS_ASSIGNMENT, variableInfo.name()));
                            }
                        }
                    });
        }
        return DONE;
    }

    private boolean uselessForDependentVariable(VariableInfo variableInfo) {
        if (variableInfo.variable() instanceof DependentVariable dv) {
            return dv.arrayVariable != null && !variableHasBeenReadAfter(dv.arrayVariable, variableInfo.getAssignmentId());
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
        return StringUtil.inSameBlock(variableInfo.getAssignmentId(), index());
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().getStatementIndexOfThisLoopVariable() == null &&
                            e.getValue().getVariableInLoop() != VariableInLoop.COPY_FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof DependentVariable))
                    .filter(vi -> statementAnalysis.isLocalVariableAndLocalToThisBlock(vi.name()) && !vi.isRead())
                    .forEach(vi -> statementAnalysis.ensure(Message.newMessage(getLocation(),
                            Message.UNUSED_LOCAL_VARIABLE, vi.name())));
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedLoopVariables() {
        if (statement() instanceof LoopStatement && !statementAnalysis.containsMessage(Message.EMPTY_LOOP)) {
            statementAnalysis.variables.stream()
                    .filter(e -> index().equals(e.getValue().getStatementIndexOfThisLoopVariable()))
                    .forEach(e -> {
                        String loopVarFqn = e.getKey();
                        StatementAnalyser first = navigationData.blocks.get().get(0).orElse(null);
                        StatementAnalysis statementAnalysis = first == null ? null : first.lastStatement().statementAnalysis;
                        if (statementAnalysis == null || !statementAnalysis.variables.isSet(loopVarFqn) ||
                                !statementAnalysis.variables.get(loopVarFqn).current().isRead()) {
                            this.statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNUSED_LOOP_VARIABLE, loopVarFqn));
                        }
                    });
        }
        return DONE;
    }

    /*
     * Can be delayed
     */
    private AnalysisStatus checkUnusedReturnValueOfMethodCall() {
        if (statementAnalysis.statement instanceof ExpressionAsStatement eas && eas.expression instanceof MethodCall) {
            MethodCall methodCall = (MethodCall) (((ExpressionAsStatement) statementAnalysis.statement).expression);
            if (Primitives.isVoid(methodCall.methodInfo.returnType())) return DONE;
            MethodAnalysis methodAnalysis = getMethodAnalysis(methodCall.methodInfo);
            int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
            if (identity == Level.DELAY) return DELAYS;
            if (identity == Level.TRUE) return DONE;
            int modified = methodAnalysis.getProperty(MODIFIED_METHOD);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.FALSE) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
            }
        }
        return DONE;
    }

    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalyser(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
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
        public String newObjectIdentifier() {
            return index();
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
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
        }

        @Override
        public Location getLocation(Expression expression) {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index, expression);
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return child(condition, false);
        }

        @Override
        public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            boolean conditionIsDelayed = isDelayed(condition);
            return new EvaluationContextImpl(iteration,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, conditionIsDelayed),
                    closure,
                    disableEvaluationOfMethodCallsUsingCompanionMethods);
        }

        public EvaluationContext childState(Expression state) {
            boolean stateIsDelayed = isDelayed(state);
            return new EvaluationContextImpl(iteration, conditionManager.addState(state, stateIsDelayed), closure, false);
        }

        /*
        differs sufficiently from the regular getProperty, in that it fast tracks as soon as one of the not nulls
        reaches EFFECTIVELY_NOT_NULL, and that it always reads from the initial value of variables.
         */

        @Override
        public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
            if (value instanceof IsVariableExpression ve) {
                VariableInfo variableInfo = findForReading(ve.variable(), getInitialStatementTime(), true);
                int cnn = variableInfo.getProperty(useEnnInsteadOfCnn ? EXTERNAL_NOT_NULL : CONTEXT_NOT_NULL);
                if (cnn >= MultiLevel.EFFECTIVELY_NOT_NULL) return true;
                int nne = variableInfo.getProperty(NOT_NULL_EXPRESSION);
                if (nne >= MultiLevel.EFFECTIVELY_NOT_NULL) return true;
                return notNullAccordingToConditionManager(ve.variable());
            }
            return MultiLevel.isEffectivelyNotNull(getProperty(value, NOT_NULL_EXPRESSION, true));
        }

        /*
        this one is meant for non-eventual types (for now). After/before errors are caught in EvaluationResult
         */
        @Override
        public boolean cannotBeModified(Expression value) {
            if (value instanceof IsVariableExpression ve) {
                VariableInfo variableInfo = findForReading(ve.variable(), getInitialStatementTime(), true);
                int cImm = variableInfo.getProperty(CONTEXT_IMMUTABLE);
                if (cImm >= MultiLevel.EFFECTIVELY_E2IMMUTABLE) return true;
                int imm = variableInfo.getProperty(IMMUTABLE);
                if (imm >= MultiLevel.EFFECTIVELY_E2IMMUTABLE) return true;
                int extImm = variableInfo.getProperty(EXTERNAL_IMMUTABLE);
                if (extImm >= MultiLevel.EFFECTIVELY_E2IMMUTABLE) return true;
                int formal = variableInfo.variable().parameterizedType().defaultImmutable(analyserContext);
                return formal >= MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            }
            return getProperty(value, IMMUTABLE, true) >= MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        }

        private int getVariableProperty(Variable variable, VariableProperty variableProperty, boolean duringEvaluation) {
            if (duringEvaluation) {
                return getPropertyFromPreviousOrInitial(variable, variableProperty, getInitialStatementTime());
            }
            return getProperty(variable, variableProperty);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty, boolean duringEvaluation) {
            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof IsVariableExpression ve) {
                // read what's in the property map (all values should be there) at initial or current level
                int inMap = getVariableProperty(ve.variable(), variableProperty, duringEvaluation);
                if (variableProperty == NOT_NULL_EXPRESSION) {
                    if (Primitives.isPrimitiveExcludingVoid(ve.variable().parameterizedType())) {
                        return MultiLevel.EFFECTIVELY_NOT_NULL;
                    }
                    int cnn = getVariableProperty(ve.variable(), CONTEXT_NOT_NULL, duringEvaluation);
                    if (cnn == Level.DELAY || inMap == Level.DELAY) return Level.DELAY;
                    int best = MultiLevel.bestNotNull(inMap, cnn);
                    boolean cmNn = notNullAccordingToConditionManager(ve.variable());
                    return MultiLevel.bestNotNull(cmNn ? MultiLevel.EFFECTIVELY_NOT_NULL : MultiLevel.NULLABLE, best);
                }
                if (variableProperty == IMMUTABLE) {
                    int formally = ve.variable().parameterizedType().defaultImmutable(getAnalyserContext());
                    if (formally == IMMUTABLE.best) return formally; // EFFECTIVELY_E2, for primitives etc.
                    int cImm = getVariableProperty(ve.variable(), CONTEXT_IMMUTABLE, duringEvaluation);
                    if (cImm == Level.DELAY || inMap == Level.DELAY) return Level.DELAY;
                    return MultiLevel.bestImmutable(inMap, MultiLevel.bestImmutable(cImm, formally));
                }
                return inMap;
            }

            if (NOT_NULL_EXPRESSION == variableProperty) {
                int directNN = value.getProperty(this, NOT_NULL_EXPRESSION, true);
                assert !Primitives.isPrimitiveExcludingVoid(value.returnType()) || directNN == MultiLevel.EFFECTIVELY_NOT_NULL;

                if (directNN == MultiLevel.NULLABLE) {
                    Expression valueIsNull = Equals.equals(this, value, NullConstant.NULL_CONSTANT, false);
                    Expression evaluation = conditionManager.evaluate(this, valueIsNull);
                    if (evaluation.isBoolValueFalse()) {
                        return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL, directNN);
                    }
                }
                return directNN;
            }

            // redirect to Value.getProperty()
            // this is the only usage of this method; all other evaluation of a Value in an evaluation context
            // must go via the current method
            return value.getProperty(this, variableProperty, true);

        }

        @Override
        public boolean notNullAccordingToConditionManager(Variable variable) {
            Set<Variable> notNullVariablesInState = conditionManager.findIndividualNullInState(this, false);
            if (notNullVariablesInState.contains(variable)) return true;
            Set<Variable> notNullVariablesInCondition = conditionManager
                    .findIndividualNullInCondition(this, false);
            if (notNullVariablesInCondition.contains(variable)) return true;
            FieldReference fieldReference;
            if (variable instanceof FieldReference fr) {
                fieldReference = fr;
            } else if (variable instanceof LocalVariableReference lvr && lvr.variable.isLocalCopyOf() instanceof FieldReference fr) {
                fieldReference = fr;
                VariableInfo variableInfo = statementAnalysis.findOrThrow(fr);
                if (variableInfo.isAssigned()) return false;
                // IMPROVE this is only valid if the statement time of the local copy is the same as that of the precondition
                // but how to do that?
            } else return false;

            Set<Variable> notNullVariablesInPrecondition = conditionManager
                    .findIndividualNullInPrecondition(this, false);
            return notNullVariablesInPrecondition.contains(fieldReference);
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
        public Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            VariableInfo variableInfo = findForReading(variable, statementTime, isNotAssignmentTarget);
            Expression value = variableInfo.getValue();
            // important! do not use variable in the next statement, but vi.variable()
            // we could have redirected from a variable field to a local variable copy
            return value instanceof NewObject ? new VariableExpression(variableInfo.variable()) : value;
        }

        @Override
        public NewObject currentInstance(Variable variable, int statementTime) {
            VariableInfo variableInfo = findForReading(variable, statementTime, true);
            Expression value = variableInfo.getValue();

            // redirect to other variable
            if (value instanceof VariableExpression variableValue) {
                assert variableValue.variable() != variable :
                        "Variable " + variable.fullyQualifiedName() + " has been assigned a VariableValue value pointing to itself";
                return currentInstance(variableValue.variable(), statementTime);
            }
            if (value instanceof NewObject instance) return instance;
            return null;
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            VariableInfo vi = statementAnalysis.findOrThrow(variable);
            return vi.getProperty(variableProperty); // ALWAYS from the map!!!!
        }

        @Override
        public int getPropertyFromPreviousOrInitial(Variable variable, VariableProperty variableProperty, int statementTime) {
            VariableInfo vi = findForReading(variable, statementTime, true);
            return vi.getProperty(variableProperty);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public LinkedVariables linkedVariables(Expression value) {
            assert value != null;
            if (value instanceof VariableExpression variableValue) {
                return linkedVariables(variableValue.variable());
            }
            return value.linkedVariables(this);
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            Boolean implicit = variable.parameterizedType().isImplicitlyImmutable(analyserContext, myMethodAnalyser.methodInfo.typeInfo);
            if (implicit == Boolean.TRUE) {
                return LinkedVariables.EMPTY;
            }
            // if implicit is null, we can only return EMPTY or DELAY!
            TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
            boolean notSelf = typeInfo != getCurrentType();
            VariableInfo variableInfo = statementAnalysis.initialValueForReading(variable, getInitialStatementTime(), true);
            if (notSelf) {
                int immutable = variableInfo.getProperty(IMMUTABLE);
                if (immutable == Level.DELAY) return LinkedVariables.DELAY;
                if (MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) return LinkedVariables.EMPTY;
            }
            // we've encountered the variable before
            if (variableInfo.linkedVariablesIsSet() && implicit != null) {
                return variableInfo.getLinkedVariables().merge(new LinkedVariables(Set.of(variable)));
            }
            return LinkedVariables.DELAY; // delay
        }

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
                Map<Expression, Expression> map = new HashMap<>();
                statementAnalysis.variables.stream()
                        .filter(e -> statementAnalysis.index.equals(e.getValue().getStatementIndexOfThisLoopOrShadowVariable()))
                        .forEach(e -> {
                            VariableInfo eval = e.getValue().best(EVALUATION);
                            Variable variable = eval.variable();
                            int nne = getProperty(eval.getValue(), NOT_NULL_EXPRESSION, true);
                            if (nne != Level.DELAY) {
                                Expression newObject = NewObject.genericMergeResult(index() + "-" + variable.fullyQualifiedName(),
                                        getPrimitives(), e.getValue().current(), nne);
                                map.put(new VariableExpression(variable), newObject);
                            }

                            Expression delayed = DelayedExpression.forNewObject(variable.parameterizedType());
                            map.put(DelayedVariableExpression.forVariable(e.getValue().current().variable()), delayed);
                        });
                return mergeValue.reEvaluate(this, map).value();
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
                            lvr.variable.isLocalCopyOf() instanceof FieldReference)
                    .collect(Collectors.toUnmodifiableMap(VariableExpression::new,
                            v -> new VariableExpression(((LocalVariableReference) v).variable.isLocalCopyOf())));
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
        public LinkedVariables getStaticallyAssignedVariables(Variable variable, int statementTime) {
            VariableInfo variableInfo = findForReading(variable, statementTime, true);
            return variableInfo.getStaticallyAssignedVariables();
        }

        @Override
        public boolean variableIsDelayed(Variable variable) {
            VariableInfo vi = statementAnalysis.findOrNull(variable, EVALUATION);
            return vi == null || vi.isDelayed();
        }

        @Override
        public Boolean isCurrentlyLinkedToField(Expression objectValue) {
            if (objectValue instanceof VariableExpression ve && ve.variable() instanceof This) return true;
            Linked1Writer linked1Writer = new Linked1Writer(statementAnalysis, this,
                    VariableInfo::getStaticallyAssignedVariables);
            return linked1Writer.isLinkedToField(objectValue);
        }

        @Override
        public MethodInfo concreteMethod(Variable variable, MethodInfo abstractMethodInfo) {
            assert abstractMethodInfo.isAbstract();
            VariableInfo variableInfo = findForReading(variable, getInitialStatementTime(), true);
            ParameterizedType type = variableInfo.getValue().returnType();
            if(type.typeInfo != null && !type.typeInfo.isAbstract()) {
                return type.typeInfo.findMethodImplementing(abstractMethodInfo);
            }
            return null;
        }
    }
}
