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
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
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
    The main loop calls the apply method with the results of an evaluation.
    For every variable, a number of steps are executed:
    - it is created, some local copies are created, and it is prepared for EVAL level
    - values, linked variables are set

    Finally, general modifications are carried out
     */
    private AnalysisStatus apply(SharedState sharedState,
                                 EvaluationResult evaluationResult,
                                 StatementAnalysis statementAnalysis) {
        AnalysisStatus status = evaluationResult.value() == NO_VALUE ? DELAYS : DONE;

        if (evaluationResult.addCircularCallOrUndeclaredFunctionalInterface()) {
            statementAnalysis.methodLevelData.addCircularCallOrUndeclaredFunctionalInterface();
        }


        for (Map.Entry<Variable, EvaluationResult.ExpressionChangeData> entry : evaluationResult.valueChanges().entrySet()) {

            Variable variable = entry.getKey();
            EvaluationResult.ExpressionChangeData changeData = entry.getValue();

            Set<Variable> additionalLinks = ensureVariables(sharedState, variable, changeData, evaluationResult.statementTime());

            // we're now guaranteed to find the variable
            VariableInfoContainer vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
            VariableInfo vi = vic.best(VariableInfoContainer.Level.EVALUATION);
            VariableInfo vi1 = vic.getPreviousOrInitial();
            assert vi != vi1 : "There should already be a different EVALUATION object";

            Expression value = bestValue(changeData, vi1);
            Expression valueToWrite = maybeValueNeedsState(sharedState.evaluationContext, vic, vi1, value);


            // we explicitly check for NO_VALUE, because "<no return value>" is legal!
            boolean haveValue = valueToWrite != NO_VALUE;
            if (haveValue) {
                log(ANALYSER, "Write value {} to variable {}", valueToWrite, variable.fullyQualifiedName());
                Map<VariableProperty, Integer> propertiesToSet = VariableInfoImpl.mergeProperties
                        (sharedState.evaluationContext.getValueProperties(valueToWrite), changeData.properties());
                vic.setValue(valueToWrite, propertiesToSet, false);
            } else {
                log(DELAYED, "Apply of {}, {} is delayed because of unknown value for {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                status = DELAYS;
            }

            if (changeData.markAssignment() && vic.isLocalVariableInLoopDefinedOutside()) {
                addToAssignmentsInLoop(variable.fullyQualifiedName());
            }

            Set<Variable> mergedLinkedVariables = writeMergedLinkedVariables(changeData, variable, vi, vi1, additionalLinks);
            if (mergedLinkedVariables != null && haveValue) {
                vic.setLinkedVariables(mergedLinkedVariables, false);
            } else {
                status = DELAYS;
            }

            // set properties
            // there's one that we intercept, to make sure that not-null travels to the parameter analyser asap
            // via the statement analyser result

            for (Map.Entry<VariableProperty, Integer> e : changeData.properties().entrySet()) {
                VariableProperty property = e.getKey();
                int pv = e.getValue();

                vic.setProperty(property, pv, false, VariableInfoContainer.Level.EVALUATION);
                if (property == VariableProperty.NOT_NULL && variable instanceof ParameterInfo parameterInfo) {
                    log(VARIABLE_PROPERTIES, "Propagating not-null value of {} to {}", value, variable.fullyQualifiedName());
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, pv));
                }
            }
        }

        evaluationResult.messages().getMessageStream().forEach(statementAnalysis.messages::add);

        if (status == DONE && evaluationResult.precondition() != null) {
            if (evaluationResult.precondition() == NO_VALUE) {
                log(DELAYED, "Apply of {} in {}, delayed because of precondition", index(),
                        myMethodAnalyser.methodInfo.fullyQualifiedName);
                status = DELAYS;
            } else {
                statementAnalysis.stateData.setPrecondition(evaluationResult.precondition());
            }
        }
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
                log(DELAYED, "Apply of {}, {} is delayed because of internal object flows",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                status = DELAYS;
            } else {
                statementAnalysis.methodLevelData.internalObjectFlows.freeze();
            }
        }

        // debugging...
        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(),
                    myMethodAnalyser.methodInfo, statementAnalysis.index, evaluationResult));
        }

        return status;
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
    private Set<Variable> ensureVariables(SharedState sharedState,
                                          Variable variable,
                                          EvaluationResult.ExpressionChangeData changeData,
                                          int newStatementTime) {
        VariableInfoContainer vic;
        if (!statementAnalysis.variables.isSet(variable.fullyQualifiedName())) {
            vic = statementAnalysis.createVariable(analyserContext, variable, statementAnalysis.flowData.getInitialTime());
        } else {
            vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
        }
        VariableInfo initial = vic.getPreviousOrInitial();
        Set<Variable> additionalLinksForThisVariable;
        if (variable instanceof FieldReference fieldReference &&
                initial.isConfirmedVariableField() && !changeData.readAtStatementTime().isEmpty()) {
            // ensure all of the local copies
            additionalLinksForThisVariable =
                    ensureLocalCopiesOfVariableField(sharedState, changeData.readAtStatementTime(), fieldReference, initial);
        } else {
            additionalLinksForThisVariable = Set.of();
        }
        String id = index() + VariableInfoContainer.Level.EVALUATION;
        String assignmentId = changeData.markAssignment() ? id : VariableInfoContainer.NOT_YET_ASSIGNED;
        String readId = changeData.readAtStatementTime().isEmpty() ? VariableInfoContainer.NOT_YET_ASSIGNED : id;
        int statementTime = statementAnalysis.statementTimeForVariable(analyserContext, variable, newStatementTime);

        vic.ensureEvaluation(assignmentId, readId, statementTime);

        return additionalLinksForThisVariable;
    }

    private Set<Variable> ensureLocalCopiesOfVariableField(SharedState sharedState,
                                                           Set<Integer> statementTimes,
                                                           FieldReference fieldReference,
                                                           VariableInfo initial) {
        FieldAnalysis fieldAnalysis = sharedState.evaluationContext().getFieldAnalysis(fieldReference.fieldInfo);
        Primitives primitives = sharedState.evaluationContext.getPrimitives();
        Map<VariableProperty, Integer> propertyMap = VariableProperty.FROM_ANALYSER_TO_PROPERTIES.stream()
                .collect(Collectors.toUnmodifiableMap(vp -> vp, fieldAnalysis::getProperty));

        Set<Variable> set = new HashSet<>();
        for (int statementTime : statementTimes) {
            LocalVariableReference localCopy = statementAnalysis.variableInfoOfFieldWhenReading(analyserContext,
                    fieldReference, initial, statementTime);
            if (!statementAnalysis.variables.isSet(localCopy.fullyQualifiedName())) {
                VariableInfoContainer lvrVic = statementAnalysis.createVariable(analyserContext, localCopy, statementTime);
                String indexOfStatementTime = statementAnalysis.flowData.assignmentIdOfStatementTime.get(statementTime);

                Expression initialValue = statementTime == initial.getStatementTime() && initial.getAssignmentId().compareTo(indexOfStatementTime) >= 0 ?
                        initial.getValue() : new NewObject(primitives, fieldReference.parameterizedType(), fieldAnalysis.getObjectFlow());
                assert initialValue != EmptyExpression.NO_VALUE;
                lvrVic.setValue(initialValue, propertyMap, false);
            }
            set.add(localCopy);
        }
        return set;
    }

    private Set<Variable> writeMergedLinkedVariables(EvaluationResult.ExpressionChangeData changeData,
                                                     Variable variable,
                                                     VariableInfo vi,
                                                     VariableInfo vi1,
                                                     Set<Variable> additionalLinks) {
        if (changeData.linkedVariables() == EvaluationResult.LINKED_VARIABLE_DELAY) {
            log(DELAYED, "Apply of {}, {} is delayed because of linked variables of {}",
                    index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                    variable.fullyQualifiedName());
            return null;
        }
        if (changeData.linkedVariables() != null) {
            if (vi.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
                log(DELAYED, "Apply of step {}, {} is delayed because of variable field delay",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                return null;
            }
            Set<Variable> previousValue = vi1.getLinkedVariables();
            Set<Variable> mergedValue1 = previousValue != null ?
                    SetUtil.immutableUnion(previousValue, changeData.linkedVariables()) : changeData.linkedVariables();
            Set<Variable> mergedValue = additionalLinks == null ? mergedValue1 : SetUtil.immutableUnion(mergedValue1, additionalLinks);
            log(ANALYSER, "Set linked variables of {} to {} in {}, {}",
                    variable, mergedValue, index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            return mergedValue;
        }
        return null;
    }

    /*
    See among others Loops_1: when a variable is assigned in a loop, it is possible that interrupts (break, etc.) have
    caused a state. If this variable is defined outside the loop, it'll have to have a value when coming out of the loop.
    The added state helps to avoid unconditional assignments.
    In i=3; while(c) { if(x) break; i=5; }, we return x?3:5; x will most likely be dependent on the loop and, be
    turned into some generic integer

    In i=3; while(c) { i=5; if(x) break; }, we return c?5:3, as soon as c has a value

    Q: what is the best place for this piece of code? EvalResult?? This here seems to late
     */
    private Expression maybeValueNeedsState(EvaluationContext evaluationContext, VariableInfoContainer vic, VariableInfo vi1, Expression value) {
        if (value != NO_VALUE
                && vic.getVariableInLoop().variableType() == VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE) {
            if (localConditionManager.isDelayed()) return NO_VALUE;
            Expression state;
            if (!localConditionManager.state().isBoolValueTrue()) {
                state = localConditionManager.state();
            } else if (!localConditionManager.condition().isBoolValueTrue()) {
                state = localConditionManager.condition();
            } else {
                state = null;
            }
            if (state != null) {
                return EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, state, value,
                        vi1.getValue(), ObjectFlow.NO_FLOW).value();
            }
        }
        return value;
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

    /*
    Not-null escapes should not contribute to preconditions.
    All the rest does.
     */
    private AnalysisStatus checkNotNullEscapesAndPreconditions(SharedState sharedState) {
        Boolean escapeAlwaysExecuted = isEscapeAlwaysExecutedInCurrentBlock();
        if (escapeAlwaysExecuted == null) {
            log(DELAYED, "Delaying check precondition of statement {}, interrupt condition unknown", index());
            return DELAYS;
        }
        if (!statementAnalysis.stateData.conditionManagerIsSet()) {
            log(DELAYED, "Delaying check precondition of statement {}, no condition manager", index());
            return DELAYS;
        }
        if (escapeAlwaysExecuted) {
            Set<Variable> nullVariables = statementAnalysis.stateData.getConditionManager()
                    .findIndividualNullInCondition(sharedState.evaluationContext, true);
            for (Variable nullVariable : nullVariables) {
                log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.fullyQualifiedName());
                if (nullVariable instanceof ParameterInfo parameterInfo) {
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                    // as a context property, at the highest level (AFTER summary, but we're simply increasing)
                    statementAnalysis.addProperty(nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                    disableErrorsOnIfStatement();
                }
            }
            // escapeCondition should filter out all != null, == null clauses
            Expression precondition = statementAnalysis.stateData.getConditionManager().precondition(sharedState.evaluationContext);
            if (precondition == NO_VALUE) {
                log(DELAYED, "Delaying check precondition of statement {}, escape condition delayed", index());
                return DELAYS;
            }
            Expression translated = sharedState.evaluationContext.acceptAndTranslatePrecondition(precondition);
            if (translated != null) {
                log(VARIABLE_PROPERTIES, "Escape with precondition {}", translated);
                statementAnalysis.stateData.setPrecondition(translated);
                disableErrorsOnIfStatement();
                return DONE;
            }
        }

        if (!statementAnalysis.stateData.preconditionIsSet()) {
            // it could have been set from the assert (step4) or apply via a method call
            statementAnalysis.stateData.setPrecondition(new BooleanConstant(statementAnalysis.primitives, true));
        }
        return DONE;
    }

    /*
    Method to help avoiding errors in the following situation:

    if(parameter == null) throw new NullPointerException();

    This will cause a @NotNull on the parameter, which in turn renders parameter == null equal to "false", which causes errors.
    The switch avoids raising this error

     */
    private void disableErrorsOnIfStatement() {
        StatementAnalysis sa = statementAnalysis.enclosingConditionalStatement();
        if (!sa.stateData.statementContributesToPrecondition.isSet()) {
            log(VARIABLE_PROPERTIES, "Disable errors on enclosing if-statement {}", sa.index);
            sa.stateData.statementContributesToPrecondition.set();
        }
    }

    private VariableInfoContainer findReturnAsVariableForWriting() {
        String fqn = myMethodAnalyser.methodInfo.fullyQualifiedName();
        return statementAnalysis.findForWriting(fqn);
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

        for (Expression expression : statementAnalysis.statement.getStructure().initialisers()) {
            Expression initialiserToEvaluate;

            if (expression instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr;
                if (!statementAnalysis.variables.isSet(lvc.localVariable.name())) {

                    // create the local (loop, catch) variable

                    lvr = new LocalVariableReference(analyserContext, lvc.localVariable, List.of());
                    VariableInLoop variableInLoop = statement() instanceof LoopStatement ?
                            new VariableInLoop(index(), VariableInLoop.VariableType.LOOP) : VariableInLoop.NOT_IN_LOOP;
                    VariableInfoContainer vic = new VariableInfoContainerImpl(lvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                            variableInLoop, statementAnalysis.navigationData.hasSubBlocks());

                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.localVariablesAssignedInThisLoop.add(lvr.fullyQualifiedName());
                    }
                    statementAnalysis.variables.put(lvc.localVariable.name(), vic);
                } else {
                    lvr = (LocalVariableReference) statementAnalysis.variables.get(lvc.localVariable.name()).current().variable();
                }

                // what should we evaluate? catch: assign a value which will be read; for(int i=0;...) --> 0 instead of i=0;
                if (sharedState.forwardAnalysisInfo.inCatch()) {
                    initialiserToEvaluate = new Assignment(statementAnalysis.primitives,
                            new VariableExpression(lvr),
                            PropertyWrapper.propertyWrapper(sharedState.evaluationContext,
                                    new NewObject(statementAnalysis.primitives, lvr.parameterizedType(), ObjectFlow.NO_FLOW),
                                    Map.of(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL), null));
                    // so that the variable has always been read, no errors
                } else if (statement() instanceof LoopStatement) {
                    if (lvc.expression instanceof Assignment assignment) {
                        initialiserToEvaluate = assignment.value;
                    } else {
                        throw new UnsupportedOperationException();
                    }
                } else {
                    initialiserToEvaluate = lvc.expression;
                }
            } else if (expression instanceof Assignment assignment) {
                initialiserToEvaluate = assignment.value;
            } else initialiserToEvaluate = null;

            if (initialiserToEvaluate != null && initialiserToEvaluate != EmptyExpression.EMPTY_EXPRESSION) {
                expressionsToEvaluate.add(initialiserToEvaluate);
            }
        }

        // part 2: updaters, + determine which local variables are modified in the updaters

        if (statementAnalysis.statement instanceof LoopStatement) {
            for (Expression expression : statementAnalysis.statement.getStructure().updaters()) {
                if (expression instanceof Assignment assignment && assignment.target instanceof VariableExpression ve) {
                    if (!(statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) &&
                            !statementAnalysis.localVariablesAssignedInThisLoop.contains(ve.name())) {
                        statementAnalysis.localVariablesAssignedInThisLoop.add(ve.name());
                    }
                    expressionsToEvaluate.add(assignment.value); // we do not want to actually change the value
                } // else: IMPROVE extract all updaters, this code only does the simplest case
            }
        } else if (statementAnalysis.statement instanceof ExplicitConstructorInvocation) {
            Structure structure = statement().getStructure();
            expressionsToEvaluate.addAll(structure.updaters());
        }

        // part 3, iteration 1+: ensure local loop variable copies and their values

        if (statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) {
            statementAnalysis.localVariablesAssignedInThisLoop.stream().forEach(fqn -> {
                assert statement() instanceof LoopStatement;

                VariableInfoContainer vic = statementAnalysis.findForWriting(fqn); // must exist already

                // assign to local variable that has been created at Level 2 in this statement
                String newFqn = fqn + "$" + index();
                LocalVariableReference newLvr;
                if (!statementAnalysis.variables.isSet(newFqn)) {
                    Variable loopVariable = vic.current().variable();
                    LocalVariable localVariable = new LocalVariable.Builder()
                            .addModifier(LocalVariableModifier.FINAL)
                            .setName(newFqn)
                            .setParameterizedType(loopVariable.parameterizedType())
                            .setOwningType(myMethodAnalyser.methodInfo.typeInfo)
                            .setIsLocalCopyOf(loopVariable)
                            .build();
                    newLvr = new LocalVariableReference(analyserContext, localVariable, List.of());
                    VariableInfoContainer newVic = new VariableInfoContainerImpl(newLvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                            new VariableInLoop(index(), VariableInLoop.VariableType.LOOP_COPY), true);
                    statementAnalysis.variables.put(newFqn, newVic);

                    expressionsToEvaluate.add(new Assignment(statementAnalysis.primitives, new VariableExpression(newLvr),
                            new NewObject(statementAnalysis.primitives, newLvr.parameterizedType(), ObjectFlow.NO_FLOW)));
                }
            });
        }

        return expressionsToEvaluate;
    }

    private AnalysisStatus evaluationOfMainExpression(SharedState sharedState) {

        List<Expression> expressionsFromInitAndUpdate = initialisersAndUpdaters(sharedState);

        Structure structure = statementAnalysis.statement.getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
            // try-statement has no main expression, and it may not have initialisers; break; continue; ...
            if (!statementAnalysis.stateData.valueOfExpression.isSet()) {
                statementAnalysis.stateData.valueOfExpression.set(EmptyExpression.EMPTY_EXPRESSION);
            }
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.copyTimeAfterExecutionFromInitialTime();
            }
            if (statementAnalysis.statement instanceof BreakStatement breakStatement) {
                StatementAnalysis.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(breakStatement);
                Expression state = localConditionManager.stateUpTo(sharedState.evaluationContext, correspondingLoop.steps());
                correspondingLoop.statementAnalysis().stateData.addStateOfInterrupt(index(), state);
                if (state == NO_VALUE) return DELAYS;
            }
            return DONE;
        }
        try {
            expressionsFromInitAndUpdate.add(structure.expression());
            Expression toEvaluate = CommaExpression.comma(sharedState.evaluationContext, expressionsFromInitAndUpdate);
            EvaluationResult result = toEvaluate.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());

            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterEvaluation(result.statementTime(), index());
            }

            AnalysisStatus statusApply = apply(sharedState, result, statementAnalysis);
            if (statusApply == DELAYS) {
                if (statementAnalysis.statement instanceof ReturnStatement) {
                    // ensure that the evaluation level is present, even if there is a delay. We must mark assignment!
                    VariableInfoContainer vic = findReturnAsVariableForWriting();
                    vic.ensureEvaluation(index() + VariableInfoContainer.Level.EVALUATION.label,
                            VariableInfoContainer.NOT_YET_READ, VariableInfoContainer.NOT_A_VARIABLE_FIELD);
                }
                log(DELAYED, "Evaluation of {}, {} is delayed by apply", index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                return DELAYS;
            }

            // the evaluation system should be pretty good at always returning NO_VALUE when a NO_VALUE has been encountered
            Expression value = result.value();
            AnalysisStatus statusPost = value == NO_VALUE ? DELAYS : DONE;

            if (statementAnalysis.statement instanceof ReturnStatement) {
                statusPost = step3_Return(sharedState, value).combine(statusPost);
            } else if (statementAnalysis.statement instanceof ForEachStatement) {
                step3_ForEach(sharedState, value);
            } else if (statementAnalysis.statement instanceof IfElseStatement ||
                    statementAnalysis.statement instanceof SwitchStatement ||
                    statementAnalysis.statement instanceof AssertStatement) {
                value = step3_IfElse_Switch_Assert(sharedState.evaluationContext, value);
            }

            if (statusPost == DELAYS) {
                log(DELAYED, "Step 3 in statement {}, {} is delayed, value", index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            } else if (!value.isUnknown()) {
                statementAnalysis.stateData.valueOfExpression.set(value);
            }

            return statusPost;
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
    private AnalysisStatus step3_Return(SharedState sharedState, Expression level3EvaluationResult) {
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);
        Expression currentReturnValue = statementAnalysis.initialValueOfReturnVariable(returnVariable);
        // do NOT check for delays on currentReturnValue, we need to make the VIC

        Expression newReturnValue;
        if (localConditionManager.isDelayed()) return DELAYS;
        // no state, or no previous return statements
        if (localConditionManager.state().isBoolValueTrue() || currentReturnValue instanceof UnknownExpression) {
            newReturnValue = level3EvaluationResult;
        } else if (myMethodAnalyser.methodInfo.returnType().equals(statementAnalysis.primitives.booleanParameterizedType)) {
            newReturnValue = new And(statementAnalysis.primitives).append(sharedState.evaluationContext, localConditionManager.state(),
                    level3EvaluationResult);
        } else {
            newReturnValue = EvaluateInlineConditional.conditionalValueConditionResolved(sharedState.evaluationContext,
                    localConditionManager.state(), level3EvaluationResult, currentReturnValue, ObjectFlow.NO_FLOW).getExpression();
        }
        VariableInfoContainer vic = statementAnalysis.findForWriting(returnVariable);
        vic.ensureEvaluation(index() + VariableInfoContainer.Level.EVALUATION.label,
                VariableInfoContainer.NOT_YET_READ, VariableInfoContainer.NOT_A_VARIABLE_FIELD);
        Map<VariableProperty, Integer> properties = sharedState.evaluationContext.getValueProperties(newReturnValue);
        vic.setValue(newReturnValue, properties, false);
        vic.setLinkedVariables(sharedState.evaluationContext.linkedVariables(level3EvaluationResult), false);
        return DONE;
    }

    // a special case, which allows us to set not null
    private void step3_ForEach(SharedState sharedState, Expression value) {
        Objects.requireNonNull(value);

        if (sharedState.evaluationContext.getProperty(value, VariableProperty.NOT_NULL) >= MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL) {
            Structure structure = statementAnalysis.statement.getStructure();
            LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
            VariableInfoContainer vic = statementAnalysis.findForWriting(lvc.localVariable.name());
            vic.ensureEvaluation(index() + VariableInfoContainer.Level.EVALUATION.label,
                    VariableInfoContainer.NOT_YET_READ, VariableInfoContainer.NOT_A_VARIABLE_FIELD);
            vic.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL, VariableInfoContainer.Level.EVALUATION);
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

    private AnalysisStatus subBlocks(SharedState sharedState) {
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

                        statementAnalysis.stateData.setPrecondition(assertion);
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
        if (!localConditionManager.isDelayed() && !statementAnalysis.stateData.conditionManagerIsSet()) {
            statementAnalysis.stateData.setConditionManager(localConditionManager);
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

            // linkedHashMap to ensure consistency in tests
            Map<Expression, StatementAnalyser> lastStatements = executions.stream()
                    .filter(ex -> !ex.startOfBlock.statementAnalysis.flowData.isUnreachable())
                    .collect(Collectors.toMap(ex -> ex.condition, ex -> ex.startOfBlock.lastStatement(),
                            (sa1, sa2) -> {
                                throw new UnsupportedOperationException();
                            }, LinkedHashMap::new));
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
            int maxTime = statementAnalysis.flowData.getTimeAfterEvaluation();
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
            Expression lastState = list.get(0).startOfBlock.lastStatement().statementAnalysis.stateData.getConditionManager().state();
            return evaluationContext.replaceLocalVariables(lastState);
        }
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
        if (bestAlwaysInterrupt == InterruptsFlow.DELAYED) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return DELAYS;
        }
        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            statementAnalysis.variableStream().forEach(variableInfo -> {
                if (variableInfo.notReadAfterAssignment()) {
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
        if (!variableInfo.isAssigned()) return false;
        return StringUtil.inSameBlock(variableInfo.getAssignmentId(), index());
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().getStatementIndexOfThisLoopVariable() == null)
                    .map(e -> e.getValue().current())
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

        private VariableInfo findForReading(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            if (closure != null && isNotMine(variable)) {
                return ((EvaluationContextImpl) closure).findForReading(variable, statementTime, isNotAssignmentTarget);
            }
            return statementAnalysis.findForReading(analyserContext, variable, statementTime, isNotAssignmentTarget);
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
            return value instanceof NewObject ?
                    new VariableExpression(variableInfo.variable(), variableInfo.getObjectFlow()) : value;
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
            findForReading(variable, statementAnalysis.flowData.getTimeAfterSubBlocks(), false);
        }

        @Override
        public int getInitialStatementTime() {
            return statementAnalysis.flowData.getInitialTime();
        }

        @Override
        public int getFinalStatementTime() {
            return statementAnalysis.flowData.getTimeAfterSubBlocks();
        }

        @Override
        public Expression replaceLocalVariables(Expression mergeValue) {
            if (statementAnalysis.statement instanceof LoopStatement && mergeValue != NO_VALUE) {
                Map<Expression, Expression> map = statementAnalysis.variables.stream()
                        .filter(e -> statementAnalysis.index.equals(e.getValue().getStatementIndexOfThisShadowVariable()))
                        .collect(Collectors.toUnmodifiableMap(e -> new VariableExpression(e.getValue().current().variable()),
                                e -> wrap(new NewObject(getPrimitives(), e.getValue().current().variable().parameterizedType(),
                                        e.getValue().current().getObjectFlow()), e.getValue().current())));
                return mergeValue.reEvaluate(this, map).value();
            }
            return mergeValue;
        }

        private Expression wrap(NewObject newObject, VariableInfo vi) {
            if (Primitives.isPrimitiveExcludingVoid(vi.variable().parameterizedType())) return newObject;
            Map<VariableProperty, Integer> properties = Map.of(VariableProperty.NOT_NULL,
                    getProperty(vi.variable(), VariableProperty.NOT_NULL));
            return PropertyWrapper.propertyWrapperForceProperties(newObject, properties);
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
                EvaluationContext evaluationContext = new ConditionManager.EvaluationContextImpl(getPrimitives());
                translated = precondition.reEvaluate(evaluationContext, translationMap).getExpression();
            }
            if (translated.variables().stream()
                    .allMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference)) {
                return translated;
            }
            return null;
        }
    }
}
