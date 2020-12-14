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
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.FlowData.Execution.*;
import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);
    public static final String ANALYSE_METHOD_LEVEL_DATA = "analyseMethodLevelData";

    public static final String STEP_1 = "step1"; // initialisation, variable creation for(X x: xs), int i=1
    public static final String STEP_2 = "step2"; // updaters (i++ in for)
    public static final String STEP_3 = "step3"; // main evaluation
    public static final String STEP_4 = "step4"; // recursive

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final AnalyserContext analyserContext;
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    private ConditionManager localConditionManager;
    private AnalysisStatus analysisStatus;
    private AnalyserComponents<String, SharedState> analyserComponents;

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
            for (Structure subStatements : structure.subStatements) {
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
    public StatementAnalyserResult analyseAllStatementsInBlock(int iteration, ForwardAnalysisInfo forwardAnalysisInfo) {
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
                    EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager());
                    // first attempt at detecting a transformation
                    wasReplacement = checkForPatterns(evaluationContext);
                    statementAnalyser = statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.statementAnalysis;
                StatementAnalyserResult result = statementAnalyser.analyseSingleStatement(iteration,
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
        if (statementAnalysis.flowData.isUnreachable()) {
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
        return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
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
                                                           boolean wasReplacement,
                                                           StatementAnalysis previous,
                                                           ForwardAnalysisInfo forwardAnalysisInfo) {
        try {
            if (analysisStatus == null) {
                assert localConditionManager == null : "expected null localConditionManager";
                assert analyserComponents == null : "expected null analyser components";

                analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                        .add("checkUnreachableStatement", sharedState -> checkUnreachableStatement(previous,
                                forwardAnalysisInfo.execution()))
                        .add("initialiseOrUpdateVariables", this::initialiseOrUpdateVariables)
                        .add("step1_initialisation", this::step1_initialisation)
                        .add("step2_updaters", this::step2_updaters)
                        .add("step3_evaluationOfMainExpression", this::step3_evaluationOfMainExpression)
                        .add("step4_subBlocks", this::step4_subBlocks)
                        .add("analyseFlowData", sharedState -> statementAnalysis.flowData.analyse(this, previous,
                                forwardAnalysisInfo.execution()))

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
                        .build();
            }


            boolean startOfNewBlock = previous == null;
            localConditionManager = startOfNewBlock ? forwardAnalysisInfo.conditionManager() : previous.stateData.getConditionManager();

            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, localConditionManager);
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

    // in a separate task, so that it can be skipped when the statement is unreachable
    private AnalysisStatus initialiseOrUpdateVariables(SharedState sharedState) {
        if (sharedState.evaluationContext.getIteration() == 0) {
            statementAnalysis.initIteration0(analyserContext, sharedState.previous);
        } else {
            statementAnalysis.initIteration1Plus(analyserContext, myMethodAnalyser.methodInfo, sharedState.previous);
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
                            statementAnalysis.stateData.getConditionManager().condition,
                            statementAnalysis.stateData.getConditionManager().state,
                            analyserComponents.getStatusesAsMap()));
        }
    }


    // executed only once per statement; we're assuming that the flowData are computed correctly
    private AnalysisStatus checkUnreachableStatement(StatementAnalysis previous,
                                                     FlowData.Execution execution) {
        // if the previous statement was not reachable, we won't reach this one either
        if (previous != null && previous.flowData.guaranteedToBeReachedInMethod.isSet() &&
                previous.flowData.guaranteedToBeReachedInMethod.get() == NEVER) {
            statementAnalysis.flowData.setGuaranteedToBeReached(NEVER);
            return DONE_ALL;
        }
        if (statementAnalysis.flowData.computeGuaranteedToBeReachedReturnUnreachable(statementAnalysis.primitives,
                previous, execution, localConditionManager.state) &&
                !statementAnalysis.inErrorState(Message.UNREACHABLE_STATEMENT)) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return DONE;
    }

    private boolean isEscapeAlwaysExecutedInCurrentBlock() {
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return statementAnalysis.flowData.guaranteedToBeReachedInCurrentBlock.get() == FlowData.Execution.ALWAYS;
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

        // first assignments, because they may create a new level in VariableInfoContainer
        Set<Variable> createdAssignmentLevel = new HashSet<>();

        evaluationResult.getExpressionChangeStream().forEach(e -> {
            Variable variable = e.getKey();
            EvaluationResult.ExpressionChangeData valueChangeData = e.getValue();

            VariableInfoContainer vic = statementAnalysis.findForWriting(analyserContext, variable);
            VariableInfo vi = vic.best(level);
            int read = vi.getProperty(VariableProperty.READ);
            int assigned = vi.getProperty(VariableProperty.ASSIGNED);

            vic.assignment(level);
            createdAssignmentLevel.add(variable);
            Expression value = valueChangeData.value();
            if (value != NO_VALUE) {
                log(ANALYSER, "Write value {} to variable {}", value, variable.fullyQualifiedName());
                Map<VariableProperty, Integer> propertiesToSet = sharedState.evaluationContext.getValueProperties(value);
                vic.setValueOnAssignment(level, value, propertiesToSet);
            }
            if (valueChangeData.markAssignment()) {
                vic.setProperty(level, VariableProperty.ASSIGNED, Math.max(Level.TRUE, assigned + 1));
            }
            // simply copy the READ value; nothing has changed here
            vic.setProperty(level, VariableProperty.READ, read);
            Expression stateOnAssignment = valueChangeData.stateOnAssignment();
            if (stateOnAssignment != NO_VALUE) {
                vic.setStateOnAssignment(level, stateOnAssignment);
            }
        });

        AnalysisStatus status = evaluationResult.value == NO_VALUE ? DELAYS : DONE;

        if (evaluationResult.linkedVariablesDelay()) {
            status = DELAYS;
        } else {
            evaluationResult.getLinkedVariablesStream().forEach(e -> {
                Variable variable = e.getKey();
                VariableInfoContainer vic = statementAnalysis.findForWriting(analyserContext, variable);
                log(ANALYSER, "Set linked variables of {} to {}", variable, e.getValue());
                if (!createdAssignmentLevel.contains(variable)) {
                    VariableInfo vi = vic.best(level); // before the assignment call!
                    vic.assignment(level);
                    // copy value, state on assignment
                    vic.setValueAndStateOnAssignment(level, vi.getValue(), vi.getStateOnAssignment(), vi.getProperties());
                    createdAssignmentLevel.add(variable);
                }
                vic.setLinkedVariables(level, e.getValue());
            });
        }

        // then all modifications get applied
        evaluationResult.getModificationStream().forEach(mod -> mod.accept(new ModificationData(sharedState.builder, level)));


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
                status = DELAYS;
            } else {
                statementAnalysis.methodLevelData.internalObjectFlows.freeze();
            }
        }

        // debugging...
        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration().debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.iteration, step,
                    myMethodAnalyser.methodInfo, statementAnalysis.index, evaluationResult));
        }

        return status;
        // IMPROVE check that AddOnceSet is the right data structure
    }

    // whatever that has not been picked up by the notNull and the size escapes
    // + preconditions by calling other methods with preconditions!

    private AnalysisStatus checkPrecondition(SharedState sharedState) {
        if (isEscapeAlwaysExecutedInCurrentBlock() && !statementAnalysis.stateData.precondition.isSet()) {
            EvaluationResult er = statementAnalysis.stateData.getConditionManager().escapeCondition(sharedState.evaluationContext);
            Expression precondition = er.value;
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
                ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser((ParameterInfo) nullVariable).parameterAnalysis;
                sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                // as a context property, at the highest level (AFTER summary, but we're simply increasing)
                statementAnalysis.addProperty(analyserContext, VariableInfoContainer.LEVEL_4_SUMMARY,
                        nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                disableErrorsOnIfStatement();
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

    /**
     * Situation part 1:     for(X x: xs) { ... } and catch(Exception e) { ... }
     * We create the local variable not at the level of the statement, but that of the first statement in the block
     * <p>
     * Situations part 2: normal variable creation via initialisers
     * <ul>
     * <li>String s = "xxx";
     * <li>the first component of the classical for loop: for(int i=0; i<..., i++)
     * <li>the variables created inside the try-with-resources
     * </ul>
     * <p>
     * <p>
     */
    private AnalysisStatus step1_initialisation(SharedState sharedState) {
        Structure structure = statementAnalysis.statement.getStructure();

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e)
        boolean assignedInLoop = statementAnalysis.statement instanceof LoopStatement;

        if (structure.localVariableCreation != null) {
            LocalVariableReference lvr = new LocalVariableReference(analyserContext,
                    structure.localVariableCreation, List.of());
            StatementAnalysis firstStatementInBlock = firstStatementFirstBlock();
            final int l1 = VariableInfoContainer.LEVEL_1_INITIALISER;
            VariableInfoContainer vic = firstStatementInBlock.findForWriting(analyserContext, lvr);
            vic.assignment(l1);
            Map<VariableProperty, Integer> propertiesToSet = new HashMap<>();
            if (sharedState.forwardAnalysisInfo.inCatch()) {
                propertiesToSet.put(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                propertiesToSet.put(VariableProperty.READ, Level.READ_ASSIGN_ONCE);
                vic.setLinkedVariables(l1, Set.of());
            } else if (assignedInLoop) {
                propertiesToSet.put(VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                // FIXME we must set the links after step 3, before going into the block in step 4
                vic.setLinkedVariables(l1, Set.of());
            }
            vic.setValueOnAssignment(l1, new NewObject(statementAnalysis.primitives, lvr.parameterizedType(), ObjectFlow.NO_FLOW),
                    propertiesToSet);
        }

        // part 2: initialisers

        boolean haveDelays = false;
        StatementAnalysis saToCreate = structure.createVariablesInsideBlock ? firstStatementFirstBlock() : statementAnalysis;
        for (Expression initialiser : structure.initialisers) {
            if (initialiser instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr = new LocalVariableReference(analyserContext, (lvc).localVariable, List.of());
                // the NO_VALUE here becomes the initial (and reset) value, which should not be a problem because variables
                // introduced here should not become "reset" to an initial value; they'll always be assigned one
                if (assignedInLoop) {
                    saToCreate.addProperty(analyserContext, VariableInfoContainer.LEVEL_1_INITIALISER, lvr, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                } else {
                    saToCreate.find(analyserContext, lvr); // "touch" it
                }
            }
            try {
                EvaluationResult result = initialiser.evaluate(sharedState.evaluationContext, ForwardEvaluationInfo.DEFAULT);
                AnalysisStatus status = apply(sharedState, result, saToCreate, VariableInfoContainer.LEVEL_1_INITIALISER, STEP_1);
                if (status == DELAYS) haveDelays = true;

                Expression value = result.value;
                if (value == NO_VALUE) haveDelays = true;

                // IMPROVE move to a place where *every* assignment is verified (assignment-basics)
                // are we in a loop (somewhere) assigning to a variable that already exists outside that loop?
                if (initialiser instanceof Assignment assignment) {
                    int levelLoop = saToCreate.stepsUpToLoop();
                    if (levelLoop >= 0) {
                        int levelAssignmentTarget = saToCreate.levelAtWhichVariableIsDefined(assignment.variableTarget);
                        if (levelAssignmentTarget >= levelLoop) {
                            saToCreate.addProperty(analyserContext, VariableInfoContainer.LEVEL_1_INITIALISER,
                                    assignment.variableTarget, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                        }
                    }
                }
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {} (created in {})",
                        statementAnalysis.index, saToCreate.index);
                throw rte;
            }
        }
        return haveDelays ? DELAYS : DONE;
    }

    private StatementAnalysis firstStatementFirstBlock() {
        return navigationData.blocks.get().get(0).map(sa -> sa.statementAnalysis).orElseThrow();
    }

    // for(int i=0 (step2); i<3 (step4); i++ (step3))
    // IMPROVE check if we really still must do this in the beginning rather than the end?
    private AnalysisStatus step2_updaters(SharedState sharedState) {
        Structure structure = statementAnalysis.statement.getStructure();
        boolean haveDelays = false;
        for (Expression updater : structure.updaters) {
            EvaluationResult result = updater.evaluate(sharedState.evaluationContext, ForwardEvaluationInfo.DEFAULT);
            AnalysisStatus status = apply(sharedState, result, statementAnalysis, VariableInfoContainer.LEVEL_2_UPDATER, STEP_2);
            if (status == DELAYS) haveDelays = true;
        }
        return haveDelays ? DELAYS : DONE;
    }


    private AnalysisStatus step3_evaluationOfMainExpression(SharedState sharedState) {
        Structure structure = statementAnalysis.statement.getStructure();
        if (structure.expression == EmptyExpression.EMPTY_EXPRESSION) {
            // try-statement has no main expression
            statementAnalysis.stateData.valueOfExpression.set(EmptyExpression.EMPTY_EXPRESSION);
            return DONE;
        }
        try {
            Expression expression;
            boolean returnConditionally;
            if (statementAnalysis.statement instanceof ReturnStatement) {
                expression = step3_prepare_Return(structure, sharedState.evaluationContext);
                returnConditionally = expression == structure.expression;
            } else {
                expression = structure.expression;
                returnConditionally = false;
            }


            EvaluationResult result = expression.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo);
            AnalysisStatus status = apply(sharedState, result, statementAnalysis, VariableInfoContainer.LEVEL_3_EVALUATION, STEP_3);
            if (status == DELAYS) {
                if (statementAnalysis.statement instanceof ReturnStatement) {
                    // we still need to ensure that there is a level 3 present, because a 4 could be written in this iteration,
                    // and in the next one 3 needs to exist
                    VariableInfoContainer variableInfo = findReturnAsVariableForWriting();
                    variableInfo.assignment(VariableInfoContainer.LEVEL_3_EVALUATION);
                    // all the rest in the new variableInfo object stays on DELAY
                }
                return DELAYS;
            }

            // the evaluation system should be pretty good at always returning NO_VALUE when a NO_VALUE has been encountered
            Expression value = result.value;
            boolean delays = value == NO_VALUE;

            if (returnConditionally) {
                step3_ReturnConditionally(sharedState, value);
            }
            if (statementAnalysis.statement instanceof ForEachStatement) {
                step3_ForEach(sharedState, value);
            }
            if (statementAnalysis.statement instanceof IfElseStatement ||
                    statementAnalysis.statement instanceof SwitchStatement ||
                    statementAnalysis.statement instanceof AssertStatement) {
                value = step3_IfElse_Switch_Assert(sharedState.evaluationContext, value);
            }
            if (value != NO_VALUE) {
                statementAnalysis.stateData.valueOfExpression.set(value);
            }

            return delays ? DELAYS : DONE;
        } catch (RuntimeException rte) {
            LOGGER.warn("Failed to evaluate main expression (step 3) in statement {}", statementAnalysis.index);
            throw rte;
        }
    }

    /**
     * Simple situation: if(a!=null) return b;  return c;
     * After the first statement, the expression is a!=null?b:return value; the state is a==null.
     * We simply substitute.
     * <p>
     * More complex situation: if(a!= null) return b; if(x!=null) return d; -->
     * For the return d statement, we should simply return d.
     */
    private void step3_ReturnConditionally(SharedState sharedState, Expression value) {
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);
        Expression currentRV = statementAnalysis.find(analyserContext, returnVariable).getValue();
        InlineConditional inlineConditional;

        Expression newInline;
        if ((inlineConditional = currentRV.asInstanceOf(InlineConditional.class)) != null) {
            if (inlineConditional.ifTrue.isInitialReturnExpression()) {
                newInline = new InlineConditional(inlineConditional.condition, value, inlineConditional.ifFalse, inlineConditional.objectFlow);
            } else if (inlineConditional.ifFalse.isInitialReturnExpression()) {
                newInline = new InlineConditional(inlineConditional.condition, inlineConditional.ifTrue, value, inlineConditional.objectFlow);
            } else throw new UnsupportedOperationException();

        } else if (currentRV.isInstanceOf(Or.class) || currentRV.isInstanceOf(And.class)) {
            Map<Expression, Expression> translation = Map.of(new VariableExpression(returnVariable), value);
            newInline = currentRV.reEvaluate(sharedState.evaluationContext, translation).value;
        } else throw new UnsupportedOperationException("? " + currentRV.getClass());

        VariableInfoContainer vic = statementAnalysis.findForWriting(returnVariable);
        vic.assignment(VariableInfoContainer.LEVEL_3_EVALUATION);
        Map<VariableProperty, Integer> properties = sharedState.evaluationContext.getValueProperties(newInline);
        vic.setValueOnAssignment(VariableInfoContainer.LEVEL_3_EVALUATION, newInline, properties);
        vic.setLinkedVariables(VariableInfoContainer.LEVEL_3_EVALUATION, newInline.linkedVariables(sharedState.evaluationContext));
    }

    /**
     * if we have already seen a return statement before, it must be in a conditional form (otherwise unreachable code).
     * In that case, we simply evaluate, and re-evaluate the conditional form (in step3_Return)
     * Otherwise, we do an assignment to the return variable.
     */
    private Expression step3_prepare_Return(Structure structure, EvaluationContext evaluationContext) {
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);
        Expression currentRV = statementAnalysis.find(analyserContext, returnVariable).getValue();
        if (conditionIsNotExactOpposite(currentRV, evaluationContext) || currentRV instanceof EmptyExpression) {
            VariableExpression returnVariableExpression = new VariableExpression(returnVariable);
            return new Assignment(statementAnalysis.primitives, returnVariableExpression, structure.expression);
        }
        return structure.expression; // simply evaluate
    }

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

    // a special case, which allows us to set not null
    // IMPROVE this can be generalised, as ArrayValue should have isNotNull1
    private void step3_ForEach(SharedState sharedState, Expression value) {
        Objects.requireNonNull(value);

        ArrayInitializer arrayValue;
        if (((arrayValue = value.asInstanceOf(ArrayInitializer.class)) != null) &&
                arrayValue.multiExpression.stream().allMatch(sharedState.evaluationContext::isNotNull0)) {
            Structure structure = statementAnalysis.statement.getStructure();
            LocalVariableReference localVariableReference = new LocalVariableReference(
                    analyserContext, structure.localVariableCreation, List.of());
            StatementAnalysis firstStatementInBlock = firstStatementFirstBlock();
            firstStatementInBlock.addProperty(analyserContext, VariableInfoContainer.LEVEL_3_EVALUATION,
                    localVariableReference, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        }
    }

    private Expression step3_IfElse_Switch_Assert(EvaluationContext evaluationContext, Expression value) {
        Objects.requireNonNull(value);

        Expression previousConditional = localConditionManager.condition;
        Expression combinedWithCondition = localConditionManager.evaluateWithCondition(evaluationContext, value);
        Expression combinedWithState = localConditionManager.evaluateWithState(evaluationContext, value);

        boolean noEffect = combinedWithCondition != NO_VALUE && combinedWithState != NO_VALUE &&
                (combinedWithCondition.equals(previousConditional) || combinedWithState.isConstant())
                || combinedWithCondition.isConstant();

        if (noEffect && !statementAnalysis.stateData.statementContributesToPrecondition.isSet()) {
            Expression constant = combinedWithCondition.equals(previousConditional) ?
                    new BooleanConstant(analyserContext.getPrimitives(), !combinedWithCondition.isBoolValueFalse())
                    : combinedWithState.isConstant() ? combinedWithState : combinedWithCondition;

            String message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData.blocks.get();
            if (statementAnalysis.statement instanceof IfElseStatement) {
                message = Message.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = constant.isBoolValueTrue();
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
                        boolean isTrue = constant.isBoolValueTrue();
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
                boolean isTrue = constant.isBoolValueTrue();
                if (isTrue) {
                    message = Message.ALERT_EVALUATES_TO_CONSTANT_TRUE;
                } else {
                    message = Message.ALERT_EVALUATES_TO_CONSTANT_FALSE;
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
            return constant;
        }
        return value;
    }

    private AnalysisStatus step4_subBlocks(SharedState sharedState) {
        List<Optional<StatementAnalyser>> startOfBlocks = navigationData.blocks.get();
        AnalysisStatus analysisStatus;
        if (!startOfBlocks.isEmpty()) {
            analysisStatus = step4_haveSubBlocks(sharedState, startOfBlocks);
        } else {
            if (statementAnalysis.statement instanceof AssertStatement) {
                Expression assertion = statementAnalysis.stateData.valueOfExpression.get();
                localConditionManager = localConditionManager.addToState(sharedState.evaluationContext, assertion);
                boolean atLeastFieldOrParameterInvolved = assertion.variables().stream()
                        .anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
                if (atLeastFieldOrParameterInvolved) {
                    log(VARIABLE_PROPERTIES, "Assertion escape with precondition {}", assertion);

                    statementAnalysis.stateData.precondition.set(assertion);
                    statementAnalysis.stateData.statementContributesToPrecondition.set();
                }
            }
            analysisStatus = DONE;
        }
        if (localConditionManager.notInDelayedState() && !statementAnalysis.stateData.conditionManager.isSet()) {
            statementAnalysis.stateData.conditionManager.set(localConditionManager);
        }
        return analysisStatus;
    }

    private record ExecutionOfBlock(FlowData.Execution execution, StatementAnalyser startOfBlock,
                                    ConditionManager conditionManager, Expression condition, boolean isDefault,
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
                        new ForwardAnalysisInfo(executionOfBlock.execution, executionOfBlock.conditionManager, executionOfBlock.inCatch));
                sharedState.builder.add(result);
                analysisStatus = analysisStatus.combine(result.analysisStatus);
                blocksExecuted++;
            }
        }

        if (blocksExecuted > 0) {
            boolean atLeastOneBlockExecuted = atLeastOneBlockExecuted(executions);

            List<StatementAnalyser> lastStatements = executions.stream().map(ExecutionOfBlock::startOfBlock)
                    .map(StatementAnalyser::lastStatement)
                    .collect(Collectors.toList());
            statementAnalysis.copyBackLocalCopies(evaluationContext, lastStatements, atLeastOneBlockExecuted, sharedState.previous);

            // compute the escape situation of the sub-blocks
            Expression addToStateAfterStatement = addToStateAfterStatement(evaluationContext, executions);
            if (!addToStateAfterStatement.isBoolValueTrue()) {
                localConditionManager = localConditionManager.addToState(evaluationContext, addToStateAfterStatement);
                log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", addToStateAfterStatement);
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
        BooleanConstant TRUE = new BooleanConstant(evaluationContext.getPrimitives(),  true);
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
        FlowData.Execution firstBlockStatementsExecution = structure.statementExecution.apply(value, evaluationContext);
        FlowData.Execution firstBlockExecution = statementAnalysis.flowData.execution(firstBlockStatementsExecution);

        ConditionManager cm = firstBlockExecution == NEVER ? null : structure.expressionIsCondition ?
                localConditionManager.addCondition(evaluationContext, value) : localConditionManager;
        executions.add(new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, value, false, false));

        for (int count = 1; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements.get(count - 1);
            Expression conditionForSubStatement;

            boolean isDefault = false;
            FlowData.Execution statementsExecution = subStatements.statementExecution.apply(value, evaluationContext);
            if (statementsExecution == DEFAULT) {
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
                Expression constant = switchEntry.switchVariableAsExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT).value;
                conditionForSubStatement = Equals.equals(evaluationContext, value, constant, ObjectFlow.NO_FLOW);
            } else throw new UnsupportedOperationException();

            FlowData.Execution execution = statementAnalysis.flowData.execution(statementsExecution);

            ConditionManager subCm = execution == NEVER ? null : localConditionManager.addCondition(evaluationContext, conditionForSubStatement);
            boolean inCatch = subStatements.localVariableCreation != null;
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm, conditionForSubStatement, isDefault, inCatch));
        }

        return executions;
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
     *     <li>NYR + local variable created higher up + EXIT (return stmt, anything beyond the level of the CREATED)</li>
     *     <li>NYR + escape</li>
     * </ul>
     */
    private AnalysisStatus checkUselessAssignments() {
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            // we run at the local level
            List<String> toRemove = new ArrayList<>();
            statementAnalysis.variableStream().forEach(variableInfo -> {
                int assigned = variableInfo.getProperty(VariableProperty.ASSIGNED);
                int read = variableInfo.getProperty(VariableProperty.READ);
                if (assigned >= Level.TRUE && read <= assigned) {
                    boolean notAssignedInLoop = variableInfo.getProperty(VariableProperty.ASSIGNED_IN_LOOP) != Level.TRUE;
                    // IMPROVE at some point we will do better than "notAssignedInLoop"
                    boolean isLocalAndLocalToThisBlock = statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name());
                    boolean useless = bestAlwaysInterrupt == InterruptsFlow.ESCAPE || notAssignedInLoop && (
                            variableInfo.variable().isLocal() && (alwaysInterrupts || isLocalAndLocalToThisBlock));
                    if (useless) {
                        statementAnalysis.ensure(Message.newMessage(getLocation(), Message.USELESS_ASSIGNMENT, variableInfo.name()));
                        if (!isLocalAndLocalToThisBlock) toRemove.add(variableInfo.name());
                    }
                }
            });
            if (!toRemove.isEmpty()) {
                log(VARIABLE_PROPERTIES, "Removing local info for variables {}", toRemove);
                statementAnalysis.removeAllVariables(toRemove);
            }
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variableStream().forEach(variableInfo -> {
                if (statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name())
                        && variableInfo.getProperty(VariableProperty.READ) < Level.READ_ASSIGN_ONCE) {
                    statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNUSED_LOCAL_VARIABLE, variableInfo.name()));
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

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            this(iteration, conditionManager, false);
        }

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager,
                                      boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            super(iteration, conditionManager);
            this.disableEvaluationOfMethodCallsUsingCompanionMethods = disableEvaluationOfMethodCallsUsingCompanionMethods;
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
        public MethodAnalysis getCurrentMethodAnalysis() {
            return myMethodAnalyser.methodAnalysis;
        }

        @Override
        public ObjectFlow getObjectFlow(Variable variable) {
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
            return new EvaluationContextImpl(iteration, conditionManager.addCondition(this, condition),
                    disableEvaluationOfMethodCallsUsingCompanionMethods);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty) {

            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof VariableExpression) {
                Variable variable = ((VariableExpression) value).variable();
                if (VariableProperty.NOT_NULL == variableProperty && notNullAccordingToConditionManager(variable)) {
                    return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                            statementAnalysis.getProperty(analyserContext, variable, variableProperty));
                }
                return statementAnalysis.getProperty(analyserContext, variable, variableProperty);
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

        @Override
        public Expression currentValue(Variable variable) {
            VariableInfo vi = statementAnalysis.find(analyserContext, variable);
            Expression value = vi.getValue();
            return value instanceof NewObject ? new VariableExpression(variable, vi.getObjectFlow(), vi.isVariableField()) : value;
        }

        @Override
        public NewObject currentInstance(Variable variable) {
            VariableInfo vi = statementAnalysis.find(analyserContext, variable);
            Expression value = vi.getValue();

            // redirect to other variable
            if (value instanceof VariableExpression variableValue) {
                assert variableValue.variable() != variable :
                        "Variable " + variable.fullyQualifiedName() + " has been assigned a VariableValue value pointing to itself";
                return currentInstance(variableValue.variable());
            }
            if (value instanceof NewObject instance) return instance;
            return null;
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            VariableInfo vi = statementAnalysis.find(analyserContext, variable);
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
                VariableInfo variableInfo = statementAnalysis.find(analyserContext, variable);
                int immutable = variableInfo.getProperty(VariableProperty.IMMUTABLE);
                if (immutable == MultiLevel.DELAY) return null;
                if (MultiLevel.isE2Immutable(immutable)) return Set.of();
            }
            VariableInfo variableInfo = statementAnalysis.find(analyserContext, variable);
            // we've encountered the variable before
            if (variableInfo.linkedVariablesIsSet()) {
                return SetUtil.immutableUnion(variableInfo.getLinkedVariables(), Set.of(variable));
            }
            return null; // delay
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
                modificationData.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));
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

        public MarkRead(Variable variable) {
            this.variable = variable;
        }

        @Override
        public void accept(ModificationData modificationData) {
            statementAnalysis.findForWriting(analyserContext, variable).markRead(modificationData.level);
        }

        @Override
        public String toString() {
            return "MarkRead{variable=" + variable + '}';
        }
    }

}
