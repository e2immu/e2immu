/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.config.EvaluationResultVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.FlowData.Execution.*;
import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);
    public static final String ANALYSE_METHOD_LEVEL_DATA = "analyseMethodLevelData";

    public static final String STEP_1 = "step1"; // initialisation, variable creation for(X x: xs), int i=1
    public static final String STEP_3 = "step3"; // main evaluation

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final AnalyserContext analyserContext;
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    private ConditionManager localConditionManager;
    private AnalysisStatus analysisStatus;
    private AnalyserComponents<String, SharedState> analyserComponents;

    private final SetOnce<List<MethodAnalyser>> lambdaAnalysers = new SetOnce<>();

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index,
                              boolean inSyncBlock) {
        this.analyserContext = Objects.requireNonNull(analyserContext);
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
            String iPlusSt = indices.isEmpty() ? "" + statementIndex : indices + "." + statementIndex;
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
                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                        statementAnalyser.statementAnalysis, structure.getStatements(),
                        iPlusSt + "." + blockIndex, true, newInSyncBlock);
                blocks.add(Optional.of(subStatementAnalyser));
                analysisBlocks.add(Optional.of(subStatementAnalyser.statementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements()) {
                if (subStatements.haveStatements()) {
                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                            statementAnalyser.statementAnalysis, subStatements.getStatements(),
                            iPlusSt + "." + blockIndex, true, newInSyncBlock);
                    blocks.add(Optional.of(subStatementAnalyser));
                    analysisBlocks.add(Optional.of(subStatementAnalyser.statementAnalysis));
                } else {
                    blocks.add(Optional.empty());
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            }
            statementAnalyser.statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
            statementAnalyser.navigationData.blocks.set(ImmutableList.copyOf(blocks));
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
            do {
                boolean wasReplacement;
                if (analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
                    wasReplacement = false;
                } else {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager(), closure);
                    // first attempt at detecting a transformation
                    wasReplacement = checkForPatterns(evaluationContext);
                    statementAnalyser = statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.statementAnalysis;
                StatementAnalyserResult result = statementAnalyser.analyseSingleStatement(iteration, closure,
                        wasReplacement, previousStatementAnalysis, forwardAnalysisInfo);
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

    record SharedState(EvaluationContext evaluationContext, StatementAnalyserResult.Builder builder,
                       StatementAnalysis previous, ForwardAnalysisInfo forwardAnalysisInfo) {
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
                assert localConditionManager == null : "expected null localConditionManager";
                assert analyserComponents == null : "expected null analyser components";

                analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                        .add("checkUnreachableStatement", sharedState -> checkUnreachableStatement(previous,
                                sharedState.forwardAnalysisInfo.execution()))
                        .add("initialiseOrUpdateVariables", this::initialiseOrUpdateVariables)
                        .add("analyseLambdas", this::analyseLambdas)
                        .add("step1_initialisation", this::step1_initialisation)
                        .add("step2_updaters", this::step2_level2_variables_and_updaters)
                        .add("step3_evaluationOfMainExpression", this::step3_evaluationOfMainExpression)
                        .add("step4_subBlocks", this::step4_subBlocks)
                        .add("analyseFlowData", sharedState -> statementAnalysis.flowData.analyse(this, previous,
                                sharedState.forwardAnalysisInfo.execution()))
                        .add("freezeAssignmentInBlock", this::freezeAssignmentInBlock)
                        .add("checkNotNullEscapes", this::checkNotNullEscapes)
                        .add("checkPrecondition", this::checkPrecondition)
                        .add("copyPrecondition", sharedState -> statementAnalysis.stateData.copyPrecondition(this,
                                previous, sharedState.evaluationContext))
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
            localConditionManager = startOfNewBlock ? forwardAnalysisInfo.conditionManager() : previous.stateData.getConditionManager();

            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, localConditionManager, closure);
            SharedState sharedState = new SharedState(evaluationContext, builder, previous, forwardAnalysisInfo);
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

    private AnalysisStatus freezeAssignmentInBlock(SharedState sharedState) {
        if (statementAnalysis.statement instanceof LoopStatement) {
            statementAnalysis.localVariablesAssignedInThisLoop.freeze();
        }
        return DONE; // only in first iteration
    }

    // in a separate task, so that it can be skipped when the statement is unreachable
    private AnalysisStatus initialiseOrUpdateVariables(SharedState sharedState) {
        if (sharedState.evaluationContext.getIteration() == 0) {
            statementAnalysis.initIteration0(analyserContext, sharedState.previous);
        } else {
            statementAnalysis.initIteration1Plus(analyserContext, myMethodAnalyser.methodInfo, sharedState.previous);
        }

        if (!statementAnalysis.flowData.initialTimeIsSet()) {
            int time;
            if (sharedState.previous != null) {
                time = sharedState.previous.flowData.getTimeAfterSubBlocks();
            } else if (statementAnalysis.parent != null) {
                time = statementAnalysis.parent.flowData.getTimeAfterExecution();
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
                                    variableInfoContainer.current().getProperties(),
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVisitors) {
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            result,
                            sharedState.evaluationContext.getIteration(),
                            sharedState.evaluationContext,
                            myMethodAnalyser.methodInfo,
                            statementAnalysis,
                            statementAnalysis.index,
                            statementAnalysis.stateData.getConditionManager().condition(),
                            statementAnalysis.stateData.getConditionManager().state(),
                            statementAnalysis.stateData.getConditionManager().absoluteState(sharedState.evaluationContext),
                            analyserComponents.getStatusesAsMap()));
        }
    }

    private AnalysisStatus analyseLambdas(SharedState sharedState) {
        if (!lambdaAnalysers.isSet()) {
            List<Lambda> lambdas = statementAnalysis.statement.getStructure().findLambdas();
            List<MethodAnalyser> methodAnalysers = lambdas.stream().map(lambda -> {
                MethodAnalyser methodAnalyser = new MethodAnalyser(lambda.methodInfo, analyserContext.getTypeAnalysis(lambda.methodInfo.typeInfo),
                        true, analyserContext);
                methodAnalyser.initialize();
                return methodAnalyser;
            }).collect(Collectors.toUnmodifiableList());
            lambdaAnalysers.set(methodAnalysers);
        }

        AnalysisStatus analysisStatus = DONE;
        for (MethodAnalyser lambdaAnalyser : lambdaAnalysers.get()) {
            log(ANALYSER, "------- Starting lambda analyser ------");
            AnalysisStatus lambdaStatus = lambdaAnalyser.analyse(sharedState.evaluationContext.getIteration(), sharedState.evaluationContext);
            log(ANALYSER, "------- Ending lambda analyser   ------");
            analysisStatus = analysisStatus.combine(lambdaStatus);
        }
        return analysisStatus;
    }


    // executed only once per statement, at the very beginning of the loop
    // we're assuming that the flowData are computed correctly
    private AnalysisStatus checkUnreachableStatement(StatementAnalysis previous,
                                                     FlowData.Execution execution) {
        // if the previous statement was not reachable, we won't reach this one either
        if (previous != null && previous.flowData.getGuaranteedToBeReachedInMethod() == NEVER) {
            statementAnalysis.flowData.setGuaranteedToBeReached(NEVER);
            return DONE_ALL;
        }
        if (statementAnalysis.flowData.computeGuaranteedToBeReachedReturnUnreachable(previous, execution, localConditionManager.state()) &&
                !statementAnalysis.inErrorState(Message.UNREACHABLE_STATEMENT)) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return localConditionManager.isDelayed() ? DELAYS : DONE;
    }

    private boolean isEscapeAlwaysExecutedInCurrentBlock() {
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return statementAnalysis.flowData.getGuaranteedToBeReachedInCurrentBlock() == FlowData.Execution.ALWAYS;
        }
        return false;
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
     */
    private AnalysisStatus apply(SharedState sharedState,
                                 EvaluationResult evaluationResult,
                                 StatementAnalysis statementAnalysis,
                                 int level,
                                 String step) {
        /*
        // state changes get composed into one big operation, applied, and the result is set
        // the condition is copied from the evaluation context
        // CURRENTLY only used for removing variables from the state when a modifying operation has been run on them
        // Tested by: TODO find out which test
        Function<Expression, Expression> composite = evaluationResult.getStateChangeStream()
                .reduce(v -> v, (f1, f2) -> v -> f2.apply(f1.apply(v)));
        Expression reducedState = composite.apply(localConditionManager.state);
        if (reducedState != localConditionManager.state) {
            localConditionManager = new ConditionManager(localConditionManager.condition, reducedState);
        }
         */
        AnalysisStatus status = evaluationResult.value() == NO_VALUE ? DELAYS : DONE;

        for (Map.Entry<Variable, EvaluationResult.ExpressionChangeData> entry : evaluationResult.valueChanges().entrySet()) {
            Variable variable = entry.getKey();
            EvaluationResult.ExpressionChangeData changeData = entry.getValue();

            VariableInfoContainer vic = statementAnalysis.findForWriting(analyserContext, variable,
                    statementAnalysis.statementTime(level), changeData.isNotAssignmentTarget());
            VariableInfo vi = vic.best(level);
            int read = vi.getProperty(VariableProperty.READ);
            int assigned = vi.getProperty(VariableProperty.ASSIGNED);
            VariableInfo vi1 = vic.best(1);
            Expression value = bestValue(changeData, vi1);

            boolean goAhead = value != NO_VALUE || level >= vic.getCurrentLevel();
            if (goAhead) {
                int statementTimeForVariable = statementAnalysis.statementTimeForVariable(analyserContext, variable,
                        statementAnalysis.statementTime(level));

                vic.prepareForValueChange(level, index(), statementTimeForVariable);

                // we explicitly check for NO_VALUE, because "<no return value>" is legal!
                if (value != NO_VALUE) {
                    log(ANALYSER, "Write value {} to variable {}", value, variable.fullyQualifiedName());
                    Map<VariableProperty, Integer> propertiesToSet = sharedState.evaluationContext.getValueProperties(value);
                    vic.setValueOnAssignment(level, value, propertiesToSet);
                } else {
                    log(DELAYED, "Apply of step {} in {}, {} is delayed because of unknown value for {}",
                            step, index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                    status = DELAYS;
                }

                if (changeData.markAssignment()) {
                    vic.setProperty(level, VariableProperty.ASSIGNED, Math.max(Level.TRUE, assigned + 1));

                    if (vic.isLocalVariableInLoopDefinedOutside()) {
                        addToAssignmentsInLoop(variable.fullyQualifiedName());
                    }
                }
                // simply copy the READ value; nothing has changed here
                vic.setProperty(level, VariableProperty.READ, read);


                if (changeData.linkedVariables() == EvaluationResult.LINKED_VARIABLE_DELAY) {
                    log(DELAYED, "Apply of step {} in {}, {} is delayed because of linked variables of {}",
                            step, index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                            variable.fullyQualifiedName());
                    status = DELAYS;
                } else if (changeData.linkedVariables() != null) {
                    log(ANALYSER, "Set linked variables of {} to {}", variable, changeData.linkedVariables());
                    vic.setLinkedVariables(level, changeData.linkedVariables());
                }
            } else {
                log(DELAYED, "Apply of step {} in {}, {} is delayed and skipped because of unknown value for {}",
                        step, index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                status = DELAYS;
            }
        }

        AnalysisStatus statusBeforeModificationStream = status;
        evaluationResult.getModificationStream()
                //        .filter(mod -> status.get() == DONE || mod instanceof MarkRead)
                .forEach(mod -> mod.accept(new ModificationData(sharedState.builder, statusBeforeModificationStream == DONE
                        ? level : VariableInfoContainer.LEVEL_1_INITIALISER)));

        if (status == DONE && !statementAnalysis.methodLevelData.internalObjectFlows.isFrozen()) {
            boolean delays = false;
            for (ObjectFlow objectFlow : evaluationResult.getObjectFlowStream().collect(Collectors.toSet())) {
                if (objectFlow.isDelayed()) {
                    delays = true;
                } else if (!statementAnalysis.methodLevelData.internalObjectFlows.contains(objectFlow)) {
                    statementAnalysis.methodLevelData.internalObjectFlows.add(objectFlow);
                }
            }
            if (delays) {
                log(DELAYED, "Apply of step {} in {}, {} is delayed because of internal object flows",
                        step, index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                status = DELAYS;
            } else {
                statementAnalysis.methodLevelData.internalObjectFlows.freeze();
            }
        }

        // debugging...
        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(), step,
                    myMethodAnalyser.methodInfo, statementAnalysis.index, evaluationResult));
        }

        return status;
    }

    // add this variable name to all loop statements until definition of the local variable
    private void addToAssignmentsInLoop(String fullyQualifiedName) {
        StatementAnalysis sa = statementAnalysis;
        while (sa != null) {
            if (!sa.variables.isSet(fullyQualifiedName)) return;
            VariableInfoContainer vic = sa.variables.get(fullyQualifiedName);
            if (!vic.isLocalVariableInLoopDefinedOutside()) return;
            if (sa.statement instanceof LoopStatement) {
                if (!sa.localVariablesAssignedInThisLoop.contains(fullyQualifiedName)) {
                    sa.localVariablesAssignedInThisLoop.add(fullyQualifiedName);
                }
            }
            sa = sa.parent;
        }
    }

    private Expression bestValue(EvaluationResult.ExpressionChangeData valueChangeData, VariableInfo vi1) {
        if (valueChangeData != null && valueChangeData.value() != NO_VALUE) {
            return valueChangeData.value();
        }
        if (vi1 != null) {
            Expression e = vi1.getValue();
            if (e.isInitialReturnExpression()) {
                return NO_VALUE;
            }
            return vi1.getValue();
        }
        return NO_VALUE;
    }

    // whatever that has not been picked up by the notNull and the size escapes
    // + preconditions by calling other methods with preconditions!

    private AnalysisStatus checkPrecondition(SharedState sharedState) {
        if (isEscapeAlwaysExecutedInCurrentBlock() && !statementAnalysis.stateData.precondition.isSet()) {
            EvaluationResult er = statementAnalysis.stateData.getConditionManager().escapeCondition(sharedState.evaluationContext);
            Expression precondition = er.value();
            if (!precondition.isBoolValueTrue()) {
                boolean atLeastFieldOrParameterInvolved = precondition.variables().stream()
                        .anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
                if (atLeastFieldOrParameterInvolved) {
                    log(VARIABLE_PROPERTIES, "Escape with precondition {}", precondition);

                    statementAnalysis.stateData.precondition.set(precondition);
                    disableErrorsOnIfStatement();
                }
            }
        }
        return DONE;
    }

    private AnalysisStatus checkNotNullEscapes(SharedState sharedState) {
        if (isEscapeAlwaysExecutedInCurrentBlock()) {
            Set<Variable> nullVariables = statementAnalysis.stateData.getConditionManager()
                    .findIndividualNullInCondition(sharedState.evaluationContext, true);
            for (Variable nullVariable : nullVariables) {
                log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.fullyQualifiedName());
                if (nullVariable instanceof ParameterInfo parameterInfo) {
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                    // as a context property, at the highest level (AFTER summary, but we're simply increasing)
                    statementAnalysis.addProperty(analyserContext, VariableInfoContainer.LEVEL_4_SUMMARY,
                            nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                    disableErrorsOnIfStatement();
                }
            }
        }
        return DONE;
    }

    /*
    Method to help avoiding errors in the following situation:

    if(parameter == null) throw new NullPointerException();

    This will cause a @NotNull on the parameter, which in turn renders parameter == null equal to "false", which causes errors.
    The switch avoids raising this error

    IMPROVE: check that the parent is the if-statement (there could be garbage in-between)
     */
    private void disableErrorsOnIfStatement() {
        if (!statementAnalysis.parent.stateData.statementContributesToPrecondition.isSet()) {
            log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
            statementAnalysis.parent.stateData.statementContributesToPrecondition.set();
        }
    }

    private VariableInfoContainer findReturnAsVariableForWriting() {
        String fqn = myMethodAnalyser.methodInfo.fullyQualifiedName();
        return statementAnalysis.findForWriting(fqn);
    }

    // in this section we only deal with initialisers outside loop and catch statements; i.e., normal
    // creation of variables.

    private AnalysisStatus step1_initialisation(SharedState sharedState) {
        if (!(statementAnalysis.statement instanceof ExpressionAsStatement)) return DONE;

        boolean haveDelays = false;
        for (Expression initialiser : statementAnalysis.statement.getStructure().initialisers()) {
            if (initialiser instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr = new LocalVariableReference(analyserContext, lvc.localVariable, List.of());
                // "touch" the variable
                statementAnalysis.findOrCreateL1(analyserContext, lvr, statementAnalysis.flowData.getInitialTime(), false);
            }
            try {
                EvaluationResult result = initialiser.evaluate(sharedState.evaluationContext, ForwardEvaluationInfo.DEFAULT);
                AnalysisStatus status = apply(sharedState, result, statementAnalysis, VariableInfoContainer.LEVEL_1_INITIALISER, STEP_1);
                if (status == DELAYS) haveDelays = true;

                Expression value = result.value();
                if (value == NO_VALUE) haveDelays = true;
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {}", index());
                throw rte;
            }
        }
        return haveDelays ? DELAYS : DONE;
    }

    /*
    Loop and catch variables are special in that their scope is restricted to the statement and its block.
    We deal with them here, however they are assigned in the structure.

    Explicit constructor invocation uses "updaters" in the structure, but that is essentially level 3 evaluation.
    The call is here but the code is completely independent.

    The for-statement has explicit initialisation and updating. These statements need evaluation, but the actual
    values are only used for independent for-loop analysis (not yet implemented) rather than for assigning real
    values to the loop variable.

    Loop (and catch) variables will be defined in level 2. A special local variable with a $<index> suffix will
    be created to represent a generic loop value.

    The special thing about creating variables at level 2 in a statement is that they are not transferred to the next statement,
    nor are they merged into level 4.
     */
    private AnalysisStatus step2_level2_variables_and_updaters(SharedState sharedState) {
        if (statement() instanceof ExplicitConstructorInvocation) {
            return handleExplicitConstructorInvocation(sharedState);
        }
        if (statement() instanceof ExpressionAsStatement) return DONE; // already done

        final int l2 = VariableInfoContainer.LEVEL_2_UPDATER;

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e), or for(int i=...)
        // variable will be set to a NewObject

        for (Expression expression : statementAnalysis.statement.getStructure().initialisers()) {
            Expression initialiserToEvaluate;

            if (expression instanceof LocalVariableCreation lvc) {
                if (!statementAnalysis.variables.isSet(lvc.localVariable.name())) {
                    LocalVariableReference lvr = new LocalVariableReference(analyserContext, lvc.localVariable, List.of());
                    VariableInfoContainer vic = new VariableInfoContainerImpl(lvr, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                            new VariableInLoop(index(), VariableInLoop.VariableType.LOOP));
                    vic.prepareForValueChange(l2, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD);
                    Map<VariableProperty, Integer> propertiesToSet = new HashMap<>();
                    if (sharedState.forwardAnalysisInfo.inCatch()) {
                        propertiesToSet.put(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                        propertiesToSet.put(VariableProperty.READ, Level.TRUE); // do not complain if variable not read
                    }
                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.localVariablesAssignedInThisLoop.add(lvr.fullyQualifiedName());
                    } else {
                        vic.setValueOnAssignment(l2, new NewObject(statementAnalysis.primitives, lvr.parameterizedType(), ObjectFlow.NO_FLOW),
                                propertiesToSet);
                    }
                    vic.setLinkedVariables(l2, Set.of());
                    statementAnalysis.variables.put(lvc.localVariable.name(), vic);
                }
                initialiserToEvaluate = lvc.expression;
            } else if (expression instanceof Assignment assignment) {
                initialiserToEvaluate = assignment.value;
            } else initialiserToEvaluate = null;

            if (initialiserToEvaluate != null) {
                // evaluation without assigning a value
                // TODO
            }
        }

        // part 2: all local variables assigned to in loop

        if (!(statementAnalysis.statement instanceof LoopStatement)) return DONE;
        if (!(statementAnalysis.localVariablesAssignedInThisLoop.isFrozen())) {
            for (Expression expression : statementAnalysis.statement.getStructure().updaters()) {
                if (expression instanceof Assignment assignment &&
                        assignment.target instanceof VariableExpression ve &&
                        !statementAnalysis.localVariablesAssignedInThisLoop.contains(ve.name())) {
                    statementAnalysis.localVariablesAssignedInThisLoop.add(ve.name());
                }
            }
            return DELAYS; // come back later
        }

        statementAnalysis.localVariablesAssignedInThisLoop.stream().forEach(fqn -> {
            VariableInfoContainer vic = statementAnalysis.findForWriting(fqn); // must exist already
            VariableInfo current = vic.current();
            vic.prepareForValueChange(l2, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD);

            // assign to local variable that has been created at Level 2 in this statement
            String newFqn = fqn + "$" + index();
            LocalVariableReference newLvr;
            if (!statementAnalysis.variables.isSet(newFqn)) {
                LocalVariable localVariable = new LocalVariable(Set.of(LocalVariableModifier.FINAL), newFqn,
                        current.variable().parameterizedType(),
                        List.of(), myMethodAnalyser.methodInfo.typeInfo);
                newLvr = new LocalVariableReference(analyserContext, localVariable, List.of());
                VariableInfoContainer newVic = new VariableInfoContainerImpl(newLvr, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                        new VariableInLoop(index(), VariableInLoop.VariableType.LOOP_COPY));
                statementAnalysis.variables.put(newFqn, newVic);
                newVic.prepareForValueChange(l2, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD);
                newVic.setValueOnAssignment(l2, new NewObject(statementAnalysis.primitives, newLvr.parameterizedType(), ObjectFlow.NO_FLOW),
                        current.getProperties());
                newVic.setLinkedVariables(l2, Set.of(vic.current().variable()));
            } else {
                newLvr = (LocalVariableReference) statementAnalysis.variables.get(newFqn).current().variable();
            }

            vic.setValueOnAssignment(l2, new VariableExpression(newLvr), Map.of());
        });

        return DONE;
    }

    // in a bit of a bind: we've used the updaters as storage for the parameters of the this( ) or super( ) statement
    // they need evaluating at level 3 (not 2, even if we used them as updaters)
    private AnalysisStatus handleExplicitConstructorInvocation(SharedState sharedState) {
        AnalysisStatus overallStatus = DONE;
        Structure structure = statement().getStructure();
        for (Expression updater : structure.updaters()) {
            EvaluationResult result = updater.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());

            AnalysisStatus status = apply(sharedState, result, statementAnalysis, VariableInfoContainer.LEVEL_3_EVALUATION, STEP_3);
            overallStatus = overallStatus.combine(status);
        }
        return overallStatus;
    }


    private AnalysisStatus step3_evaluationOfMainExpression(SharedState sharedState) {
        Structure structure = statementAnalysis.statement.getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION) {
            // try-statement has no main expression
            statementAnalysis.stateData.valueOfExpression.set(EmptyExpression.EMPTY_EXPRESSION);
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.copyTimeAfterExecutionFromInitialTime();
            }
            return DONE;
        }
        try {
            EvaluationResult result = structure.expression().evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());

            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterExecution(result.statementTime(), index());
            }

            AnalysisStatus status = apply(sharedState, result, statementAnalysis, VariableInfoContainer.LEVEL_3_EVALUATION, STEP_3);
            if (status == DELAYS) {
                if (statementAnalysis.statement instanceof ReturnStatement) {
                    // we still need to ensure that there is a level 3 present, because a 4 could be written in this iteration,
                    // and in the next one 3 needs to exist
                    VariableInfoContainer variableInfo = findReturnAsVariableForWriting();
                    variableInfo.prepareForValueChange(VariableInfoContainer.LEVEL_3_EVALUATION, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD);
                    variableInfo.setProperty(VariableInfoContainer.LEVEL_3_EVALUATION, VariableProperty.ASSIGNED, Level.TRUE);
                }
                log(DELAYED, "Step 3 in statement {}, {} is delayed, apply", index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                return DELAYS;
            }

            // the evaluation system should be pretty good at always returning NO_VALUE when a NO_VALUE has been encountered
            Expression value = result.value();
            boolean delays = value == NO_VALUE;

            if (statementAnalysis.statement instanceof ReturnStatement) {
                step3_Return(sharedState, value);
            } else if (statementAnalysis.statement instanceof ForEachStatement) {
                step3_ForEach(sharedState, value);
            } else if (statementAnalysis.statement instanceof IfElseStatement ||
                    statementAnalysis.statement instanceof SwitchStatement ||
                    statementAnalysis.statement instanceof AssertStatement) {
                value = step3_IfElse_Switch_Assert(sharedState.evaluationContext, value);
            }
            if (!value.isUnknown()) {
                statementAnalysis.stateData.valueOfExpression.set(value);
            }
            if (delays) {
                log(DELAYED, "Step 3 in statement {}, {} is delayed, value", index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            }
            return delays ? DELAYS : DONE;
        } catch (RuntimeException rte) {
            LOGGER.warn("Failed to evaluate main expression (step 3) in statement {}", statementAnalysis.index);
            throw rte;
        }
    }

    /*
    modify the value of the return variable according to the evaluation result and the current state

    consider
    if(x) return a;
    return b;

    after the if, the state is !x, and the return variable has the value x?a:<return>
    we should not immediately overwrite, but take the existing return value into account, and return x?a:b
     */
    private void step3_Return(SharedState sharedState, Expression level3EvaluationResult) {
        final int l3 = VariableInfoContainer.LEVEL_3_EVALUATION;
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);

        Expression currentReturnValue = statementAnalysis.findOrThrow(returnVariable, l3).getValue();
        Expression newReturnValue = EvaluateInlineConditional.conditionalValueConditionResolved(sharedState.evaluationContext,
                localConditionManager.state(), level3EvaluationResult, currentReturnValue, ObjectFlow.NO_FLOW).getExpression();

        VariableInfoContainer vic = statementAnalysis.findForWriting(returnVariable);
        vic.prepareForValueChange(l3, index(), VariableInfoContainer.NOT_A_VARIABLE_FIELD);
        Map<VariableProperty, Integer> properties = sharedState.evaluationContext.getValueProperties(newReturnValue);
        vic.setValueOnAssignment(l3, newReturnValue, properties);
        vic.setProperty(l3, VariableProperty.ASSIGNED, Level.TRUE);
        vic.setLinkedVariables(l3, sharedState.evaluationContext.linkedVariables(level3EvaluationResult));
    }

    /*
        private boolean conditionIsNotExactOpposite(Expression expression, EvaluationContext evaluationContext) {
            InlineConditional inlineConditional;
            And and;
            Or or;
            if ((inlineConditional = expression.asInstanceOf(InlineConditional.class)) != null) {
                // the inline conditional swaps if it has a negation in the condition
                boolean negate = inlineConditional.ifFalse.isInitialReturnExpression();
                Expression notCondition = negate ? Negation.negate(evaluationContext, inlineConditional.condition) : inlineConditional.condition;
                return !evaluationContext.getConditionManager().state.equals(notCondition);
            } else if ((and = expression.asInstanceOf(And.class)) != null) {
                throw new UnsupportedOperationException();
            } else if ((or = expression.asInstanceOf(Or.class)) != null) {
                Expression negatedOrWithoutReturnExpression = Negation.negate(evaluationContext,
                        new Or(or.primitives()).append(evaluationContext,
                                or.expressions().stream().filter(e -> !e.isInitialReturnExpression()).toArray(Expression[]::new)));
                Expression combined = evaluationContext.getConditionManager().combineWithState(evaluationContext, negatedOrWithoutReturnExpression);
                boolean exactOpposite = combined instanceof BooleanConstant bc && bc.constant() ||
                        combined.equals(evaluationContext.getConditionManager().state);
                return !exactOpposite;
            }
            return true;
        }
    */
    // a special case, which allows us to set not null
    private void step3_ForEach(SharedState sharedState, Expression value) {
        Objects.requireNonNull(value);

        if (value.getProperty(sharedState.evaluationContext, VariableProperty.NOT_NULL) >= MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL) {
            Structure structure = statementAnalysis.statement.getStructure();
            LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
            VariableInfoContainer vic = statementAnalysis.findForWriting(lvc.localVariable.name());
            vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        }
    }

    private Expression step3_IfElse_Switch_Assert(EvaluationContext evaluationContext, Expression value) {
        Objects.requireNonNull(value);

        Expression evaluated = localConditionManager.evaluate(evaluationContext, value);

        boolean noEffect = evaluated.isConstant();

        if (noEffect && !statementAnalysis.stateData.statementContributesToPrecondition.isSet()) {
            String message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData.blocks.get();
            if (statementAnalysis.statement instanceof IfElseStatement) {
                message = Message.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = evaluated.isBoolValueTrue();
                    if (!isTrue) {
                        // important: we register the error on the IfElse rather than the first statement in the block, because that one
                        // really is excluded from all analysis
                        statementAnalysis.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo, firstStatement.index),
                                Message.UNREACHABLE_STATEMENT));
                    }
                    firstStatement.flowData.setGuaranteedToBeReached(isTrue ? ALWAYS : NEVER);
                });
                if (blocks.size() == 2) {
                    blocks.get(1).ifPresent(firstStatement -> {
                        boolean isTrue = evaluated.isBoolValueTrue();
                        if (isTrue) {
                            // important: we register the error on the IfElse rather than the first statement in the block, because that one
                            // really is excluded from all analysis
                            statementAnalysis.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo, firstStatement.index),
                                    Message.UNREACHABLE_STATEMENT));
                        }
                        firstStatement.flowData.setGuaranteedToBeReached(isTrue ? NEVER : ALWAYS);
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
            } else {
                // switch
                message = Message.CONDITION_EVALUATES_TO_CONSTANT;
            }
            statementAnalysis.ensure(Message.newMessage(evaluationContext.getLocation(), message));
            return evaluated;
        }
        return value;
    }

    private AnalysisStatus step4_subBlocks(SharedState sharedState) {
        List<Optional<StatementAnalyser>> startOfBlocks = navigationData.blocks.get();
        AnalysisStatus analysisStatus = localConditionManager.isDelayed() ? DELAYS : DONE;

        if (!startOfBlocks.isEmpty()) {
            analysisStatus = step4_haveSubBlocks(sharedState, startOfBlocks).combine(analysisStatus);
        } else {
            if (statementAnalysis.statement instanceof AssertStatement) {
                if (statementAnalysis.stateData.valueOfExpression.isSet()) {
                    Expression assertion = statementAnalysis.stateData.valueOfExpression.get();
                    localConditionManager = localConditionManager.newForNextStatement(sharedState.evaluationContext, assertion);
                    boolean atLeastFieldOrParameterInvolved = assertion.variables().stream()
                            .anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
                    if (atLeastFieldOrParameterInvolved) {
                        log(VARIABLE_PROPERTIES, "Assertion escape with precondition {}", assertion);

                        statementAnalysis.stateData.precondition.set(assertion);
                        statementAnalysis.stateData.statementContributesToPrecondition.set();
                    }
                } else {
                    analysisStatus = DELAYS;
                }
            }
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.copyTimeAfterSubBlocksFromTimeAfterExecution();
            }
        }
        if (!localConditionManager.isDelayed() && !statementAnalysis.stateData.conditionManager.isSet()) {
            statementAnalysis.stateData.conditionManager.set(localConditionManager);
        }
        return analysisStatus;
    }

    private record ExecutionOfBlock(FlowData.Execution execution,
                                    StatementAnalyser startOfBlock,
                                    ConditionManager conditionManager,
                                    Expression condition,
                                    boolean isDefault,
                                    boolean inCatch) {
        public boolean escapesAlways() {
            return execution != NEVER && startOfBlock != null && startOfBlock.statementAnalysis.flowData.interruptStatus() == ALWAYS;
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
            if (executionOfBlock.execution != NEVER && executionOfBlock.startOfBlock != null) {

                StatementAnalyserResult result = executionOfBlock.startOfBlock.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                        new ForwardAnalysisInfo(executionOfBlock.execution, executionOfBlock.conditionManager, executionOfBlock.inCatch),
                        evaluationContext.getClosure());
                sharedState.builder.add(result);
                analysisStatus = analysisStatus.combine(result.analysisStatus);
                blocksExecuted++;
            } else if (executionOfBlock.startOfBlock != null) {
                // ensure that the first statement is unreachable
                FlowData flowData = executionOfBlock.startOfBlock.statementAnalysis.flowData;
                flowData.setGuaranteedToBeReached(NEVER);

                if (statement() instanceof LoopStatement) {
                    statementAnalysis.ensure(Message.newMessage(getLocation(), Message.EMPTY_LOOP));
                }
            }
        }

        if (blocksExecuted > 0) {
            boolean atLeastOneBlockExecuted = atLeastOneBlockExecuted(executions);

            Map<Expression, StatementAnalyser> lastStatements = executions.stream()
                    .filter(ex -> !ex.startOfBlock.statementAnalysis.flowData.isUnreachable())
                    .collect(Collectors.toUnmodifiableMap(ex -> ex.condition, ex -> ex.startOfBlock.lastStatement()));
            int maxTime = lastStatements.values().stream().mapToInt(sa -> sa.statementAnalysis.flowData.getTimeAfterSubBlocks())
                    .max().orElseThrow();
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterSubBlocks(maxTime, index());
            }

            // need timeAfterSubBlocks set already
            statementAnalysis.copyBackLocalCopies(evaluationContext, localConditionManager.state(), lastStatements,
                    atLeastOneBlockExecuted, maxTime);

            // compute the escape situation of the sub-blocks
            Expression addToStateAfterStatement = addToStateAfterStatement(evaluationContext, executions);
            if (!addToStateAfterStatement.isBoolValueTrue()) {
                localConditionManager = localConditionManager.newForNextStatement(evaluationContext, addToStateAfterStatement);
                log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", addToStateAfterStatement);
            }
        } else {
            int maxTime = statementAnalysis.flowData.getTimeAfterExecution();
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterSubBlocks(maxTime, index());
            }
        }
        return analysisStatus;
    }

    private boolean atLeastOneBlockExecuted(List<ExecutionOfBlock> list) {
        if (list.stream().anyMatch(ExecutionOfBlock::alwaysExecuted)) return true;
        // we have a default, and all conditions have code, and are possible
        return list.stream().anyMatch(e -> e.isDefault && e.startOfBlock != null) &&
                list.stream().allMatch(e -> e.execution == CONDITIONALLY && e.startOfBlock != null);
    }

    private Expression addToStateAfterStatement(EvaluationContext evaluationContext, List<ExecutionOfBlock> list) {
        BooleanConstant TRUE = new BooleanConstant(evaluationContext.getPrimitives(), true);
        if (statementAnalysis.statement instanceof IfElseStatement) {
            ExecutionOfBlock e0 = list.get(0);
            if (list.size() == 1) {
                if (e0.escapesAlways()) {
                    return Negation.negate(evaluationContext, list.get(0).condition);
                }
                return TRUE;
            }
            if (list.size() == 2) {
                ExecutionOfBlock e1 = list.get(1);
                boolean escape1 = e1.escapesAlways();
                if (e0.escapesAlways()) {
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
            Expression[] components = list.stream().filter(ExecutionOfBlock::escapesAlways).map(e -> e.condition).toArray(Expression[]::new);
            if (components.length == 0) return TRUE;
            return new And(evaluationContext.getPrimitives()).append(evaluationContext, components);
        }
        // TODO SwitchExpressions?
        return TRUE;
    }


    private List<ExecutionOfBlock> step4a_determineExecution(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        List<ExecutionOfBlock> executions = new ArrayList<>(startOfBlocks.size());

        Expression value = statementAnalysis.stateData.getValueOfExpression();
        Structure structure = statementAnalysis.statement.getStructure();
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        // main block

        // some loops are never executed, and we can see that
        FlowData.Execution firstBlockStatementsExecution = structure.statementExecution().apply(value, evaluationContext);
        FlowData.Execution firstBlockExecution = statementAnalysis.flowData.execution(firstBlockStatementsExecution);

        executions.add(makeExecutionOfBlock(firstBlockExecution, startOfBlocks, value));

        for (int count = 1; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements().get(count - 1);
            Expression conditionForSubStatement;

            boolean isDefault = false;
            FlowData.Execution statementsExecution = subStatements.statementExecution().apply(value, evaluationContext);
            if (statementsExecution == FlowData.Execution.DEFAULT) {
                isDefault = true;
                conditionForSubStatement = defaultCondition(evaluationContext, executions);
                if (conditionForSubStatement.isBoolValueFalse()) statementsExecution = NEVER;
                else if (conditionForSubStatement.isBoolValueTrue()) statementsExecution = ALWAYS;
                else statementsExecution = CONDITIONALLY;
            } else if (statementsExecution == ALWAYS) {
                conditionForSubStatement = new BooleanConstant(statementAnalysis.primitives, true);
            } else if (statementsExecution == NEVER) {
                conditionForSubStatement = null; // will not be executed anyway
            } else if (statement() instanceof SwitchEntry switchEntry) {
                Expression constant = switchEntry.switchVariableAsExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT).value();
                conditionForSubStatement = Equals.equals(evaluationContext, value, constant, ObjectFlow.NO_FLOW);
            } else throw new UnsupportedOperationException();

            FlowData.Execution execution = statementAnalysis.flowData.execution(statementsExecution);

            ConditionManager subCm = execution == NEVER ? null :
                    localConditionManager.newAtStartOfNewBlock(statementAnalysis.primitives, conditionForSubStatement);
            boolean inCatch = statement() instanceof TryStatement && !subStatements.initialisers().isEmpty(); // otherwise, it is finally
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm, conditionForSubStatement, isDefault, inCatch));
        }

        return executions;
    }

    private ExecutionOfBlock makeExecutionOfBlock(FlowData.Execution firstBlockExecution,
                                                  List<Optional<StatementAnalyser>> startOfBlocks,
                                                  Expression value) {
        Structure structure = statementAnalysis.statement.getStructure();
        Expression condition;
        ConditionManager cm;
        if (firstBlockExecution == NEVER) {
            cm = null;
            condition = null;
        } else if (structure.expressionIsCondition()) {
            cm = localConditionManager.newAtStartOfNewBlock(statementAnalysis.primitives, value);
            condition = value;
        } else {
            cm = localConditionManager;
            condition = new BooleanConstant(statementAnalysis.primitives, true);
        }
        return new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, condition,
                false, false);
    }

    private Expression defaultCondition(EvaluationContext evaluationContext, List<ExecutionOfBlock> executions) {
        Primitives primitives = evaluationContext.getPrimitives();
        List<Expression> previousConditions = executions.stream().map(e -> e.condition).collect(Collectors.toList());
        if (previousConditions.isEmpty()) {
            return new BooleanConstant(primitives, true);
        }
        Expression[] negated = previousConditions.stream().map(c -> Negation.negate(evaluationContext, c))
                .toArray(Expression[]::new);
        return new And(primitives, ObjectFlow.NO_FLOW).append(evaluationContext, negated);
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
        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            statementAnalysis.variableStream().forEach(variableInfo -> {
                int assigned = variableInfo.getProperty(VariableProperty.ASSIGNED);
                int read = variableInfo.getProperty(VariableProperty.READ);
                if (assigned >= Level.TRUE && read <= assigned) {
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

    private boolean localVariableAssignmentInThisBlock(VariableInfo variableInfo) {
        assert variableInfo.variable().isLocal();
        if (statementAnalysis.parent == null) return true;
        int assigned = variableInfo.getProperty(VariableProperty.ASSIGNED);
        String fqn = variableInfo.variable().fullyQualifiedName();
        if (statementAnalysis.parent.variables.isSet(fqn)) {
            VariableInfo oneUp = statementAnalysis.parent.variables.get(fqn).best(VariableInfoContainer.LEVEL_3_EVALUATION);
            int assignedOneUp = oneUp.getProperty(VariableProperty.ASSIGNED);
            return assignedOneUp < assigned;
        }
        return false;
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().getStatementIndexOfThisLoopVariable() == null)
                    .map(e -> e.getValue().current())
                    .filter(vi -> statementAnalysis.isLocalVariableAndLocalToThisBlock(vi.name())
                            && vi.getProperty(VariableProperty.READ) < Level.TRUE)
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
                                statementAnalysis.variables.get(loopVarFqn).current().getProperty(VariableProperty.READ) < Level.TRUE) {
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
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.FALSE) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
            }
        }
        return DONE;
    }

    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalysers().get(methodInfo);
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
        public boolean allowedToIncrementStatementTime() {
            return !statementAnalysis.inSyncBlock;
        }

        @Override
        public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
            return getAnalyserContext().inAnnotatedAPIAnalysis() || disableEvaluationOfMethodCallsUsingCompanionMethods;
        }

        @Override
        public Set<String> allUnqualifiedVariableNames() {
            return statementAnalysis.allUnqualifiedVariableNames(analyserContext, getCurrentType());
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
        public ObjectFlow getObjectFlow(Variable variable, int statementTime) {
            return null;
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
            return new EvaluationContextImpl(iteration, conditionManager.newAtStartOfNewBlock(getPrimitives(), condition),
                    closure,
                    disableEvaluationOfMethodCallsUsingCompanionMethods);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty) {

            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof VariableExpression) {
                Variable variable = ((VariableExpression) value).variable();
                if (VariableProperty.NOT_NULL == variableProperty && notNullAccordingToConditionManager(variable)) {
                    return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                            statementAnalysis.getProperty(variable, variableProperty));
                }
                return statementAnalysis.getProperty(variable, variableProperty);
            }

            // redirect to Value.getProperty()
            // this is the only usage of this method; all other evaluation of a Value in an evaluation context
            // must go via the current method
            return value.getProperty(this, variableProperty);

        }

        private boolean notNullAccordingToConditionManager(Variable variable) {
            Set<Variable> notNullVariablesInState = conditionManager.findIndividualNullInState(this, false);
            if (notNullVariablesInState.contains(variable)) return true;
            Set<Variable> notNullVariablesInCondition = conditionManager.findIndividualNullInCondition(this, false);
            return notNullVariablesInCondition.contains(variable);
        }

        private VariableInfo findOrCreateL1(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            if (closure != null && isNotMine(variable)) {
                return ((EvaluationContextImpl) closure).findOrCreateL1(variable, statementTime, isNotAssignmentTarget);
            }
            return statementAnalysis.findOrCreateL1(analyserContext, variable, statementTime, isNotAssignmentTarget);
        }

        private VariableInfo findOrThrow(Variable variable) {
            if (closure != null && isNotMine(variable)) {
                return ((EvaluationContextImpl) closure).findOrThrow(variable);
            }
            return statementAnalysis.findOrThrow(variable);
        }

        private boolean isNotMine(Variable variable) {
            return getCurrentType() != variable.getOwningType();
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            VariableInfo vi = findOrCreateL1(variable, statementTime, isNotAssignmentTarget);
            Expression value = vi.getValue();
            // important! do not use variable in the next statement, but vi.variable()
            // we could have redirected from a variable field to a local variable copy
            return value instanceof NewObject ? new VariableExpression(vi.variable(), vi.getObjectFlow()) : value;
        }

        @Override
        public NewObject currentInstance(Variable variable, int statementTime) {
            VariableInfo vi = findOrCreateL1(variable, statementTime, true);
            Expression value = vi.getValue();

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
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return Stream.empty(); // TODO
        }

        @Override
        public Set<Variable> linkedVariables(Expression value) {
            assert value != null;
            if (value instanceof VariableExpression variableValue) {
                return linkedVariables(variableValue.variable());
            }
            return value.linkedVariables(this);
        }

        @Override
        public Set<Variable> linkedVariables(Variable variable) {
            TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
            boolean notSelf = typeInfo != getCurrentType();
            if (notSelf) {
                VariableInfo variableInfo = statementAnalysis.findOrThrow(variable);
                int immutable = variableInfo.getProperty(VariableProperty.IMMUTABLE);
                if (immutable == MultiLevel.DELAY) return null;
                if (MultiLevel.isE2Immutable(immutable)) return Set.of();
            }
            VariableInfo variableInfo = statementAnalysis.findOrThrow(variable);
            // we've encountered the variable before
            if (variableInfo.linkedVariablesIsSet()) {
                return SetUtil.immutableUnion(variableInfo.getLinkedVariables(), Set.of(variable));
            }
            return null; // delay
        }

        @Override
        public void ensureVariableAtTimeOfSubBlocks(Variable variable) {
            findOrCreateL1(variable, statementAnalysis.flowData.getTimeAfterSubBlocks(), false);
        }

        @Override
        public int getInitialStatementTime() {
            return statementAnalysis.flowData.getInitialTime();
        }

        @Override
        public int getFinalStatementTime() {
            return statementAnalysis.flowData.getTimeAfterSubBlocks();
        }

    }

    public record ModificationData(StatementAnalyserResult.Builder builder, int level) {
    }

    public interface StatementAnalysisModification extends Consumer<ModificationData> {
        // nothing extra at the moment
    }

    public class SetProperty implements StatementAnalysisModification {
        public final Variable variable;
        public final VariableProperty property;
        public final int value;

        public SetProperty(Variable variable, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = variable;
        }

        @Override
        public void accept(ModificationData modificationData) {
            statementAnalysis.addProperty(analyserContext, modificationData.level, variable, property, value);

            // some properties need propagation directly to parameters
            if (property == VariableProperty.NOT_NULL && variable instanceof ParameterInfo parameterInfo) {
                log(VARIABLE_PROPERTIES, "Propagating not-null value of {} to {}", value, variable.fullyQualifiedName());
                ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                modificationData.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, value));
            }
        }

        @Override
        public String toString() {
            return "SetProperty{variable=" + variable + ", property=" + property + ", value=" + value + '}';
        }
    }


    public class RaiseErrorMessage implements StatementAnalysisModification {
        private final Message message;

        public RaiseErrorMessage(Message message) {
            this.message = message;
        }

        @Override
        public void accept(ModificationData modificationData) {
            statementAnalysis.ensure(message);
        }

        @Override
        public String toString() {
            return "RaiseErrorMessage{message=" + message + '}';
        }
    }


    public class ErrorAssigningToFieldOutsideType implements StatementAnalysisModification {
        private final FieldInfo fieldInfo;
        private final Location location;

        public ErrorAssigningToFieldOutsideType(FieldInfo fieldInfo, Location location) {
            this.fieldInfo = fieldInfo;
            this.location = location;
        }

        @Override
        public void accept(ModificationData modificationData) {
            statementAnalysis.ensure(Message.newMessage(location, Message.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE));
        }

        @Override
        public String toString() {
            return "ErrorAssigningToFieldOutsideType{fieldInfo=" + fieldInfo + ", location=" + location + '}';
        }
    }

    public class ParameterShouldNotBeAssignedTo implements StatementAnalysisModification {
        private final ParameterInfo parameterInfo;
        private final Location location;

        public ParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo, Location location) {
            this.parameterInfo = parameterInfo;
            this.location = location;
        }

        @Override
        public void accept(ModificationData modificationData) {
            statementAnalysis.ensure(Message.newMessage(location, Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        @Override
        public String toString() {
            return "ParameterShouldNotBeAssignedTo{parameterInfo=" + parameterInfo + ", location=" + location + '}';
        }
    }

    public class MarkRead implements StatementAnalysisModification {
        public final Variable variable;
        public final int statementTime;

        public MarkRead(Variable variable, int statementTime) {
            this.variable = variable;
            this.statementTime = statementTime;
        }

        @Override
        public void accept(ModificationData modificationData) {
            statementAnalysis.findForWriting(analyserContext, variable, statementTime, true)
                    .markRead(modificationData.level);
        }

        @Override
        public String toString() {
            return "MarkRead{variable=" + variable + '}';
        }
    }

}
