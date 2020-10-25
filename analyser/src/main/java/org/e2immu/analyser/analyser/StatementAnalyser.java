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
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.ConstantValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.VariableProperty.FINAL;
import static org.e2immu.analyser.model.StatementAnalysis.FieldReferenceState.EFFECTIVELY_FINAL_DELAYED;
import static org.e2immu.analyser.model.StatementAnalysis.FieldReferenceState.SINGLE_COPY;
import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final AnalyserContext analyserContext;
    private final Messages messages = new Messages();
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    private ConditionManager localConditionManager;
    private AnalysisStatus analysisStatus;
    private AnalyserComponents<String, SharedState> analyserComponents;
    // if true, we should ignore errors on the condition in the next iterations
    // if(x == null) throw ... causes x to become @NotNull; in the next iteration, x==null cannot happen,
    // which would cause an error; it is this error that is eliminated
    private boolean ignoreErrorsOnCondition;

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index,
                              boolean inSyncBlock,
                              boolean inPartOfConstruction) {
        this.analyserContext = Objects.requireNonNull(analyserContext);
        this.myMethodAnalyser = Objects.requireNonNull(methodAnalyser);
        this.statementAnalysis = new StatementAnalysis(statement, parent, index, inSyncBlock, inPartOfConstruction);
    }

    public static StatementAnalyser recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock,
            boolean inPartOfConstruction) {
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
            StatementAnalyser statementAnalyser = new StatementAnalyser(analyserContext, myMethodAnalyser, statement, parent, iPlusSt, inSyncBlock, inPartOfConstruction);
            if (previous != null) {
                previous.statementAnalysis.navigationData.next.set(Optional.of(statementAnalyser.statementAnalysis));
                previous.navigationData.next.set(Optional.of(statementAnalyser));
            }
            previous = statementAnalyser;
            if (first == null) first = statementAnalyser;

            int blockIndex = 0;
            List<StatementAnalyser> blocks = new ArrayList<>();
            List<StatementAnalysis> analysisBlocks = new ArrayList<>();

            Structure structure = statement.getStructure();
            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;

            if (structure.haveStatements()) {
                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser, parent, statements,
                        iPlusSt + "." + blockIndex, true, newInSyncBlock, inPartOfConstruction);
                blocks.add(subStatementAnalyser);
                analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser, parent, statements,
                            iPlusSt + "." + blockIndex, true, newInSyncBlock, inPartOfConstruction);
                    blocks.add(subStatementAnalyser);
                    analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                    blockIndex++;
                }
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
            StatementAnalyser statementAnalyser = goToFirstStatementToAnalyse();
            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();

            if (statementAnalyser == null) {
                // nothing left to analyse
                return builder.setAnalysisStatus(DONE).build();
            }

            StatementAnalyser previousStatement = null;
            do {
                boolean wasReplacement;
                if (analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
                    wasReplacement = false;
                } else {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager);
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
            LOGGER.warn("Caught exception in while analysing block of: {}", this);
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

    @Override
    public StatementAnalyser lastStatement() {
        return followReplacements().navigationData.next.get().map(StatementAnalyser::lastStatement).orElse(this);
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
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentMethod(),
                parent(), statements, startIndex, false, statementAnalysis.inSyncBlock, statementAnalysis.inPartOfConstruction);
    }

    private StatementAnalyser goToFirstStatementToAnalyse() {
        StatementAnalyser statementAnalyser = followReplacements();
        while (statementAnalyser != null && statementAnalyser.analysisStatus == DONE) {
            statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            if (statementAnalyser != null) statementAnalyser = statementAnalyser.followReplacements();
        }
        return statementAnalyser;
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

    // executed only once per statement; we're assuming that the flowData are computed correctly
    private AnalysisStatus checkUnreachableStatement(StatementAnalysis previousStatementAnalysis, FlowData.Execution execution) {
        if (statementAnalysis.flowData.computeGuaranteedToBeReached(previousStatementAnalysis, execution) &&
                !statementAnalysis.inErrorState()) {
            messages.add(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
            statementAnalysis.errorFlags.errorValue.set(true);
        }
        return DONE;
    }

    private boolean isEscapeAlwaysExecuted() {
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return statementAnalysis.flowData.guaranteedToBeReachedInMethod.get() == FlowData.Execution.ALWAYS;
        }
        return false;
    }

    record SharedState(EvaluationContext evaluationContext, StatementAnalyserResult.Builder builder) {
    }

    /**
     * @param iteration                 the iteration
     * @param wasReplacement            boolean, to ensure that the effect of a replacement warrants continued analysis
     * @param previousStatementAnalysis null if there was no previous statement in this block
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    private StatementAnalyserResult analyseSingleStatement(int iteration,
                                                           boolean wasReplacement,
                                                           StatementAnalysis previousStatementAnalysis,
                                                           ForwardAnalysisInfo forwardAnalysisInfo) {
        if (analysisStatus == null) {
            assert localConditionManager == null : "expected null localConditionManager";

            statementAnalysis.initialise(previousStatementAnalysis);

            boolean startOfNewBlock = previousStatementAnalysis == null;
            localConditionManager = startOfNewBlock ? (statementAnalysis.parent == null ?
                    ConditionManager.INITIAL :
                    statementAnalysis.parent.stateData.conditionManager.get().addCondition(forwardAnalysisInfo.conditionManager.condition)) :
                    previousStatementAnalysis.stateData.conditionManager.get();

            analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                    .add("checkUnreachableStatement", sharedState -> checkUnreachableStatement(previousStatementAnalysis, forwardAnalysisInfo.execution))

                    .add("singleStatementAnalysisSteps", sharedState -> singleStatementAnalysisSteps(sharedState, forwardAnalysisInfo))
                    .add("tryToFreezeDependencyGraph", sharedState -> tryToFreezeDependencyGraph())
                    .add("analyseFlowData", sharedState -> statementAnalysis.flowData.analyse(this, previousStatementAnalysis))

                    .add("analyseMethodLevelData", sharedState -> statementAnalysis.methodLevelData.analyse(sharedState, statementAnalysis,
                            previousStatementAnalysis == null ? null : previousStatementAnalysis.methodLevelData,
                            statementAnalysis.stateData))
                    .add("copyErrorFlags", sharedState -> statementAnalysis.errorFlags.copy(statementAnalysis, previousStatementAnalysis))
                    .add("copyPrecondition", sharedState -> statementAnalysis.stateData.copyPrecondition(this, previousStatementAnalysis))

                    .add("checkNotNullEscapes", this::checkNotNullEscapes)
                    .add("checkSizeEscapes", this::checkSizeEscapes)
                    .add("checkPrecondition", this::checkPrecondition)
                    .add("checkUnusedReturnValue", sharedState -> checkUnusedReturnValue())
                    .add("checkUselessAssignments", sharedState -> checkUselessAssignments())
                    .add("checkUnusedLocalVariables", sharedState -> checkUnusedLocalVariables())
                    .build();
        } else {
            statementAnalysis.updateStatements(analyserContext, myMethodAnalyser.methodInfo, previousStatementAnalysis);
        }

        StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager);
        SharedState sharedState = new SharedState(evaluationContext, builder);
        AnalysisStatus overallStatus = analyserComponents.run(sharedState);

        StatementAnalyserResult result = sharedState.builder()
                .setAnalysisStatus(overallStatus)
                .combineAnalysisStatus(wasReplacement ? PROGRESS : DONE).build();
        analysisStatus = result.analysisStatus;

        visitStatementVisitors(statementAnalysis.index, sharedState);

        if (analysisStatus == DONE) {
            localConditionManager = null;
            analyserComponents = null;
            // note that we must keep analysisStatus to DONE
        }

        log(ANALYSER, "Returning from statement {} of {} with analysis status {}", statementAnalysis.index,
                myMethodAnalyser.methodInfo.name, analysisStatus);
        return result;
    }

    private AnalysisStatus tryToFreezeDependencyGraph() {
        if (statementAnalysis.dependencyGraph.isFrozen()) return DONE;
        AtomicBoolean haveDelays = new AtomicBoolean();
        statementAnalysis.dependencyGraph.visit((v, list) -> {
            statementAnalysis.assertVariableExists(v);

            VariableInfo variableInfo = statementAnalysis.find(analyserContext, v);
            if (variableInfo != null && !variableInfo.expressionValue.isSet()) haveDelays.set(true);
        });
        if (haveDelays.get()) return DELAYS;
        statementAnalysis.dependencyGraph.freeze();
        return DONE;
    }

    private void visitStatementVisitors(String statementId, SharedState sharedState) {
        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVariableVisitors) {
            statementAnalysis.variables.stream()
                    .map(Map.Entry::getValue)
                    .forEach(variableInfo -> statementAnalyserVariableVisitor.visit(
                            new StatementAnalyserVariableVisitor.Data(
                                    sharedState.evaluationContext.getIteration(),
                                    myMethodAnalyser.methodInfo,
                                    statementId,
                                    variableInfo.name,
                                    variableInfo.variable,
                                    variableInfo.valueForNextStatement(),
                                    variableInfo.getStateOnAssignment(),
                                    variableInfo.getObjectFlow(),
                                    variableInfo.properties)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVisitors) {
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            sharedState.evaluationContext.getIteration(),
                            myMethodAnalyser.methodInfo,
                            statementAnalysis,
                            statementAnalysis.index,
                            statementAnalysis.stateData.conditionManager.get().condition,
                            statementAnalysis.stateData.conditionManager.get().state,
                            analyserComponents.getStatusesAsMap()));
        }
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
     */
    private AnalysisStatus apply(EvaluationResult evaluationResult, Function<Variable, SetOnce<Value>> assignmentDestination) {
        // debugging...
        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration().debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.iteration,
                    myMethodAnalyser.methodInfo, statementAnalysis.index, evaluationResult));
        }

        // state changes get composed into one big operation, applied, and the result is set
        // the condition is copied from the evaluation context
        Function<Value, Value> composite = evaluationResult.getStateChangeStream()
                .reduce(v -> v, (f1, f2) -> v -> f2.apply(f1.apply(v)));
        Value reducedState = composite.apply(localConditionManager.state);
        if (reducedState != localConditionManager.state) {
            localConditionManager = new ConditionManager(localConditionManager.condition, reducedState);
        }

        // all modifications get applied
        evaluationResult.getModificationStream().forEach(statementAnalysis::apply);

        evaluationResult.getValueChangeStream().forEach(e -> {
            SetOnce<Value> setOnce = assignmentDestination.apply(e.getKey());
            if (!setOnce.isSet()) {
                log(ANALYSER, "Write value {} to variable {}", e.getValue(), e.getKey().fullyQualifiedName());
                setOnce.set(e.getValue());
            }
            statementAnalysis.addProperty(analyserContext, e.getKey(), VariableProperty.ASSIGNED, Level.TRUE);
        });

        if (evaluationResult.value == NO_VALUE) return DELAYS;

        if (statementAnalysis.methodLevelData.internalObjectFlows.isFrozen()) {
            return DONE;
        }

        boolean delays = false;
        for (ObjectFlow objectFlow : evaluationResult.getObjectFlowStream().collect(Collectors.toSet())) {
            if (objectFlow.isDelayed()) {
                delays = true;
            } else if (!statementAnalysis.methodLevelData.internalObjectFlows.contains(objectFlow)) {
                statementAnalysis.methodLevelData.internalObjectFlows.add(objectFlow);
            }
        }
        if (delays) return DELAYS;
        statementAnalysis.methodLevelData.internalObjectFlows.freeze();
        return DONE;
        // TODO check that AddOnceSet is the right data structure
    }


    // whatever that has not been picked up by the notNull and the size escapes
    // + preconditions by calling other methods with preconditions!

    private AnalysisStatus checkPrecondition(SharedState sharedState) {
        if (isEscapeAlwaysExecuted()) {
            EvaluationResult er = statementAnalysis.stateData.conditionManager.get().escapeCondition(sharedState.evaluationContext);
            Value precondition = er.value;
            if (precondition != UnknownValue.EMPTY) {
                boolean atLeastFieldOrParameterInvolved = precondition.variables().stream()
                        .anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
                if (atLeastFieldOrParameterInvolved) {
                    log(VARIABLE_PROPERTIES, "Escape with precondition {}", precondition);

                    statementAnalysis.stateData.precondition.set(precondition);

                    if (!ignoreErrorsOnCondition) {
                        log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                        ignoreErrorsOnCondition = true;
                    }
                }
            }
        }
        return DONE;
    }

    private AnalysisStatus checkNotNullEscapes(SharedState sharedState) {
        if (isEscapeAlwaysExecuted()) {
            Set<Variable> nullVariables = statementAnalysis.stateData.conditionManager.get().findIndividualNullConditions();
            for (Variable nullVariable : nullVariables) {
                log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.fullyQualifiedName());
                ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser((ParameterInfo) nullVariable).parameterAnalysis;
                sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                // as a context property
                statementAnalysis.addProperty(analyserContext, nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                if (!ignoreErrorsOnCondition) {
                    log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                    ignoreErrorsOnCondition = true;
                }
            }
        }
        return DONE;
    }

    private AnalysisStatus checkSizeEscapes(SharedState sharedState) {
        if (isEscapeAlwaysExecuted()) {
            Map<Variable, Value> individualSizeRestrictions = statementAnalysis.stateData.conditionManager.get().findIndividualSizeRestrictionsInCondition();
            for (Map.Entry<Variable, Value> entry : individualSizeRestrictions.entrySet()) {
                ParameterInfo parameterInfo = (ParameterInfo) entry.getKey();
                Value negated = NegatedValue.negate(entry.getValue());
                log(VARIABLE_PROPERTIES, "Escape with check on size on {}: {}", parameterInfo.fullyQualifiedName(), negated);
                int sizeRestriction = negated.encodedSizeRestriction();
                if (sizeRestriction > 0) { // if the complement is a meaningful restriction
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.SIZE, sizeRestriction));

                    statementAnalysis.addProperty(analyserContext, parameterInfo, VariableProperty.SIZE, sizeRestriction);
                    if (!ignoreErrorsOnCondition) {
                        log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                        ignoreErrorsOnCondition = true;
                    }
                }
            }
        }
        return DONE;
    }

    private LocalVariableReference step1_localVariableInForOrCatch(Structure structure,
                                                                   boolean inCatch,
                                                                   boolean assignedInLoop) {
        LocalVariableReference lvr;
        if (structure.localVariableCreation != null) {
            lvr = new LocalVariableReference(structure.localVariableCreation,
                    List.of());
            if (inCatch) {
                statementAnalysis.addProperty(analyserContext, lvr, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                statementAnalysis.addProperty(analyserContext, lvr, VariableProperty.READ, Level.READ_ASSIGN_ONCE);
            } else if (assignedInLoop) {
                statementAnalysis.addProperty(analyserContext, lvr, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
            } else {
                statementAnalysis.find(analyserContext, lvr); // "touch" it
            }
        } else {
            lvr = null;
        }
        return lvr;
    }


    private void step2_localVariableCreation(SharedState sharedState, Structure structure, boolean assignedInLoop) {
        for (Expression initialiser : structure.initialisers) {
            if (initialiser instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr = new LocalVariableReference((lvc).localVariable, List.of());
                // the NO_VALUE here becomes the initial (and reset) value, which should not be a problem because variables
                // introduced here should not become "reset" to an initial value; they'll always be assigned one
                if (assignedInLoop) {
                    statementAnalysis.addProperty(analyserContext, lvr, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                } else {
                    statementAnalysis.find(analyserContext, lvr); // "touch" it
                }
            }
            try {
                EvaluationResult result = initialiser.evaluate(sharedState.evaluationContext, ForwardEvaluationInfo.DEFAULT);
                sharedState.builder.combineAnalysisStatus(apply(result, null));

                Value value = result.value;
                // initialisers size 1 means expression as statement, local variable creation
                if (structure.initialisers.size() == 1 && value != null && value != NO_VALUE &&
                        !statementAnalysis.stateData.valueOfExpression.isSet()) {
                    statementAnalysis.stateData.valueOfExpression.set(value);
                }

                // TODO move to a place where *every* assignment is verified (assignment-basics)
                // are we in a loop (somewhere) assigning to a variable that already exists outside that loop?
                if (initialiser instanceof Assignment && initialiser.assignmentTarget().isPresent()) {
                    int levelLoop = statementAnalysis.stepsUpToLoop();
                    if (levelLoop >= 0) {
                        Variable assignmentTarget = initialiser.assignmentTarget().get();
                        int levelAssignmentTarget = statementAnalysis.levelAtWhichVariableIsDefined(analyserContext, assignmentTarget);
                        if (levelAssignmentTarget >= levelLoop) {
                            statementAnalysis.addProperty(analyserContext, assignmentTarget, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                        }
                    }
                }
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {}", statementAnalysis.index);
                throw rte;
            }
        }
    }

    private void step3_updaters(EvaluationContext evaluationContext, Structure structure) {
        for (Expression updater : structure.updaters) {
            EvaluationResult result = updater.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            apply(result, variable -> statementAnalysis.find(analyserContext, variable).initialValue);
        }
    }

    private Value step4_evaluationOfMainExpression(EvaluationContext evaluationContext, Structure structure) {
        if (structure.expression == EmptyExpression.EMPTY_EXPRESSION) {
            return null;
        }
        try {
            EvaluationResult result = structure.expression.evaluate(evaluationContext, structure.forwardEvaluationInfo);
            apply(result, variable -> statementAnalysis.find(analyserContext, variable).expressionValue);
            // the evaluation system should be pretty good at always returning NO_VALUE when a NO_VALUE has been encountered
            Value value = result.value;

            if (value != NO_VALUE && !statementAnalysis.stateData.valueOfExpression.isSet()) {
                statementAnalysis.stateData.valueOfExpression.set(value);
            }


            return value;
        } catch (RuntimeException rte) {
            LOGGER.warn("Failed to evaluate expression in statement {}", statementAnalysis.index);
            throw rte;
        }
    }

    private void step5_forEachNotNullCheck(EvaluationContext evaluationContext, Value value, LocalVariableReference localVariableReference) {
        if (statementAnalysis.statement instanceof ForEachStatement && value != null) {
            ArrayValue arrayValue;
            if (((arrayValue = value.asInstanceOf(ArrayValue.class)) != null) &&
                    arrayValue.values.stream().allMatch(evaluationContext::isNotNull0)) {
                statementAnalysis.addProperty(analyserContext, localVariableReference, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
            }
        }
    }

    private void step6_return(Value value, EvaluationContext evaluationContext) {
        if (!(statementAnalysis.statement instanceof ReturnStatement) || myMethodAnalyser.methodInfo.isVoid()
                || myMethodAnalyser.methodInfo.isConstructor) {
            return;
        }
        String statementId = statementAnalysis.index;
        TransferValue transferValue;
        if (statementAnalysis.methodLevelData.returnStatementSummaries.isSet(statementId)) {
            transferValue = statementAnalysis.methodLevelData.returnStatementSummaries.get(statementId);
        } else {
            transferValue = new TransferValue();
            statementAnalysis.methodLevelData.returnStatementSummaries.put(statementId, transferValue);
            int fluent = (((ReturnStatement) statementAnalysis.statement).fluent());
            transferValue.properties.put(VariableProperty.FLUENT, fluent);
        }
        if (value == NO_VALUE) return;
        if (value == null) {
            if (!transferValue.linkedVariables.isSet()) {
                transferValue.linkedVariables.set(Set.of());
            }
            if (!transferValue.value.isSet()) transferValue.value.set(NO_VALUE);
            return;
        }

        Set<Variable> vars = evaluationContext.linkedVariables(value);
        if (vars == null) {
            log(DELAYED, "Linked variables is delayed on transfer");
            analysisStatus = DELAYS;
        } else if (!transferValue.linkedVariables.isSet()) {
            transferValue.linkedVariables.set(vars);
        }
        if (!transferValue.value.isSet()) {
            // just like the VariableValue, the TransferValue has properties separately!
            transferValue.value.set(value);
        }
        for (VariableProperty variableProperty : VariableProperty.INTO_RETURN_VALUE_SUMMARY) {
            int v = evaluationContext.getProperty(value, variableProperty);
            if (variableProperty == VariableProperty.IDENTITY && v == Level.DELAY) {
                int methodDelay = evaluationContext.getProperty(value, VariableProperty.METHOD_DELAY);
                if (methodDelay != Level.TRUE) {
                    v = Level.FALSE;
                }
            }
            if (variableProperty == VariableProperty.SIZE_COPY && v == Level.DELAY)
                v = Level.FALSE; // TODO REMOVE ME at some point
            int current = transferValue.getProperty(variableProperty);
            if (v > current) {
                transferValue.properties.put(variableProperty, v);
            }
        }
        int immutable = evaluationContext.getProperty(value, VariableProperty.IMMUTABLE);
        if (immutable == Level.DELAY) immutable = MultiLevel.MUTABLE;
        int current = transferValue.getProperty(VariableProperty.IMMUTABLE);
        if (immutable > current) {
            transferValue.properties.put(VariableProperty.IMMUTABLE, immutable);
        }
    }

    private boolean step7_detectErrorsIfElseSwitchFor(Value value, EvaluationContext evaluationContext) {
        if (statementAnalysis.statement instanceof IfElseStatement || statementAnalysis.statement instanceof SwitchStatement) {
            Objects.requireNonNull(value);

            Value previousConditional = localConditionManager.condition;
            Value combinedWithCondition = localConditionManager.evaluateWithCondition(value);
            Value combinedWithState = localConditionManager.evaluateWithState(value);

            // we have no idea which of the 2 remains
            boolean noEffect = combinedWithCondition != NO_VALUE && combinedWithState != NO_VALUE &&
                    (combinedWithCondition.equals(previousConditional) || combinedWithState.isConstant())
                    || combinedWithCondition.isConstant();

            if (noEffect && !ignoreErrorsOnCondition && !statementAnalysis.inErrorState()) {
                messages.add(Message.newMessage(evaluationContext.getLocation(), Message.CONDITION_EVALUATES_TO_CONSTANT));
                statementAnalysis.errorFlags.errorValue.set(true);
            }
            return true;
        }

        if (value != null && statementAnalysis.statement instanceof ForEachStatement) {
            int size = evaluationContext.getProperty(value, VariableProperty.SIZE);
            if (size == Level.SIZE_EMPTY && !statementAnalysis.inErrorState()) {
                messages.add(Message.newMessage(evaluationContext.getLocation(), Message.EMPTY_LOOP));
                statementAnalysis.errorFlags.errorValue.set(true);
            }
        }
        return false;
    }

    private boolean step8_primaryBlock(EvaluationContext evaluationContext,
                                       Value value,
                                       Structure structure,
                                       StatementAnalyser startOfFirstBlock,
                                       StatementAnalyserResult.Builder builder) {
        boolean statementsExecutedAtLeastOnce = structure.statementsExecutedAtLeastOnce.test(value);

        // in a synchronized block, some fields can behave like variables
        boolean inSyncBlock = statementAnalysis.inSyncBlock || statementAnalysis.statement instanceof SynchronizedStatement;

        FlowData.Execution executionBlock = statementsExecutedAtLeastOnce ? FlowData.Execution.ALWAYS : FlowData.Execution.CONDITIONALLY;
        FlowData.Execution execution = statementAnalysis.flowData.guaranteedToBeReachedInMethod.get();

        StatementAnalyserResult recursiveResult = startOfFirstBlock.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                new ForwardAnalysisInfo(execution.worst(executionBlock), localConditionManager.addCondition(value), false));
        builder.add(recursiveResult);
        return inSyncBlock;
    }

    private Value step9_evaluateSubExpression(EvaluationContext evaluationContext,
                                              Value value,
                                              int start,
                                              Expression expression,
                                              ForwardEvaluationInfo forwardEvaluationInfo,
                                              List<Value> conditions) {

        Value valueForSubStatement;
        if (EmptyExpression.DEFAULT_EXPRESSION == expression) {
            if (start == 1) valueForSubStatement = NegatedValue.negate(value);
            else {
                if (conditions.isEmpty()) {
                    valueForSubStatement = BoolValue.TRUE;
                } else {
                    Value[] negated = conditions.stream().map(NegatedValue::negate).toArray(Value[]::new);
                    valueForSubStatement = new AndValue(ObjectFlow.NO_FLOW).append(negated);
                }
            }
        } else if (EmptyExpression.FINALLY_EXPRESSION == expression || EmptyExpression.EMPTY_EXPRESSION == expression) {
            valueForSubStatement = null;
        } else {
            // real expression
            EvaluationResult result = expression.evaluate(evaluationContext, forwardEvaluationInfo);
            valueForSubStatement = result.value;
            apply(result, null);
            conditions.add(valueForSubStatement);
        }
        return valueForSubStatement;
    }

    private void step10_evaluateSubBlock(EvaluationContext evaluationContext,
                                         StatementAnalyserResult.Builder builder,
                                         Structure structure,
                                         int count,
                                         Value value,
                                         Value valueForSubStatement) {
        boolean statementsExecutedAtLeastOnce = structure.statementsExecutedAtLeastOnce.test(value);
        FlowData.Execution execution = statementsExecutedAtLeastOnce ? FlowData.Execution.ALWAYS : FlowData.Execution.CONDITIONALLY;


        StatementAnalyser subStatementStart = navigationData.blocks.get().get(count);
        boolean inCatch = structure.localVariableCreation != null;
        StatementAnalyserResult result = subStatementStart.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                new ForwardAnalysisInfo(execution, localConditionManager.addCondition(valueForSubStatement), inCatch));
        builder.add(result);
    }

    private AnalysisStatus singleStatementAnalysisSteps(SharedState sharedState, ForwardAnalysisInfo forwardAnalysisInfo) {
        Structure structure = statementAnalysis.statement.getStructure();
        EvaluationContext evaluationContext = sharedState.evaluationContext;
        StatementAnalyserResult.Builder builder = sharedState.builder;

        // STEP 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e)
        boolean assignedInLoop = statementAnalysis.statement instanceof LoopStatement;
        LocalVariableReference theLocalVariableReference = step1_localVariableInForOrCatch(structure,
                forwardAnalysisInfo.inCatch, assignedInLoop);

        // STEP 2: More local variables in try-resources, for-loop, expression as statement (normal local variables)
        step2_localVariableCreation(sharedState, structure, assignedInLoop);


        // STEP 3: updaters (only in the classic for statement, and the parameters of an explicit constructor invocation this(...)
        // for now, we put these BEFORE the main evaluation + the main block. One of the two should read the value that is being updated
        step3_updaters(evaluationContext, structure);


        // STEP 4: evaluation of the main expression of the statement (if the statement has such a thing)
        final Value value = step4_evaluationOfMainExpression(evaluationContext, structure);

        // STEP 5: not-null check on forEach (see step 1)
        step5_forEachNotNullCheck(evaluationContext, value, theLocalVariableReference);


        // STEP 6: ReturnStatement, prepare parts of method level data
        step6_return(value, evaluationContext);

        // STEP 7: detect some errors for some statements
        boolean isSwitchOrIfElse = step7_detectErrorsIfElseSwitchFor(value, evaluationContext);

        // STEP 8: the primary block, if it's there
        // the primary block has an expression in case of "if", "while", and "synchronized"
        // in the first two cases, we'll treat the expression as a condition

        List<StatementAnalyser> startOfBlocks = navigationData.blocks.get();
        Value defaultCondition = NO_VALUE;
        List<Value> conditions = new ArrayList<>();
        int start;

        if (structure.haveNonEmptyBlock()) {
            StatementAnalyser startOfFirstBlock = startOfBlocks.get(0);
            boolean inSyncBlock = step8_primaryBlock(evaluationContext, value, structure, startOfFirstBlock, builder);

            if (!inSyncBlock && value != NO_VALUE && value != null) {
                defaultCondition = NegatedValue.negate(value);
            }
            start = 1;
        } else {
            start = 0;
        }

        // PART 8: other conditions, including the else, switch entries, catch clauses

        for (int count = start; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements.get(count - start);

            // PART 9: evaluate the sub-expression; this can be done in the current evaluation context
            // (only real evaluation for Java 14 conditionals in switch)
            // valueForSubStatement will become the new condition

            Value valueForSubStatement = step9_evaluateSubExpression(evaluationContext, value, start, subStatements.expression,
                    subStatements.forwardEvaluationInfo, conditions);
            if (EmptyExpression.DEFAULT_EXPRESSION == subStatements.expression) {
                defaultCondition = valueForSubStatement;
            }

            // PART 10: create subContext, add parameters of sub statements, execute

            step10_evaluateSubBlock(evaluationContext, builder, subStatements, count, value, valueForSubStatement);
        }

        if (structure.haveNonEmptyBlock()) {

            List<StatementAnalyser> lastStatements = startOfBlocks.stream().map(StatementAnalyser::lastStatement).collect(Collectors.toList());
            statementAnalysis.copyBackLocalCopies(lastStatements, structure.noBlockMayBeExecuted);

            if (isSwitchOrIfElse && !defaultCondition.isConstant()) {
                // compute the escape situation of the sub-blocks
                List<Boolean> escapes = lastStatements.stream()
                        .map(sa -> sa.statementAnalysis.flowData.interruptStatus() == FlowData.Execution.ALWAYS)
                        .collect(Collectors.toList());
                boolean allButLastSubStatementsEscape = escapes.stream().limit(escapes.size() - 1).allMatch(b -> b)
                        && !escapes.get(escapes.size() - 1);
                if (allButLastSubStatementsEscape) {
                    localConditionManager = localConditionManager.addToState(defaultCondition);
                    log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", defaultCondition);
                }
            }
        }

        // FINALLY, set the state

        if (!localConditionManager.delayedState() && !statementAnalysis.stateData.conditionManager.isSet()) {
            statementAnalysis.stateData.conditionManager.set(localConditionManager);
        }

        return value == NO_VALUE ? DELAYS : DONE;
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
                    // TODO at some point we will do better than "notAssignedInLoop"
                    boolean useless = bestAlwaysInterrupt == InterruptsFlow.ESCAPE || notAssignedInLoop && (
                            alwaysInterrupts && statementAnalysis.isLocalVariable(analyserContext, variableInfo) ||
                                    variableInfo.isNotLocalCopy() && variableInfo.variable instanceof LocalVariableReference);
                    if (useless) {
                        if (!statementAnalysis.errorFlags.uselessAssignments.isSet(variableInfo.variable)) {
                            statementAnalysis.errorFlags.uselessAssignments.put(variableInfo.variable, true);
                            messages.add(Message.newMessage(getLocation(), Message.USELESS_ASSIGNMENT, variableInfo.name));
                        }
                        if (variableInfo.isLocalCopy()) toRemove.add(variableInfo.name);
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
                if (variableInfo.isNotLocalCopy() && variableInfo.variable instanceof LocalVariableReference
                        && variableInfo.getProperty(VariableProperty.READ) < Level.READ_ASSIGN_ONCE) {
                    LocalVariable localVariable = ((LocalVariableReference) variableInfo.variable).variable;
                    if (!statementAnalysis.errorFlags.unusedLocalVariables.isSet(localVariable)) {
                        statementAnalysis.errorFlags.unusedLocalVariables.put(localVariable, true);
                        messages.add(Message.newMessage(getLocation(), Message.UNUSED_LOCAL_VARIABLE, localVariable.name));
                    }
                }
            });
        }
        return DONE;
    }

    /*
     * Can be delayed
     */
    private AnalysisStatus checkUnusedReturnValue() {
        if (statementAnalysis.statement instanceof ExpressionAsStatement eas && eas.expression instanceof MethodCall &&
                !statementAnalysis.inErrorState()) {
            MethodCall methodCall = (MethodCall) (((ExpressionAsStatement) statementAnalysis.statement).expression);
            if (methodCall.methodInfo.returnType().isVoid()) return DONE;
            MethodAnalysis methodAnalysis = getMethodAnalysis(methodCall.methodInfo);
            int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
            if (identity == Level.DELAY) return DELAYS;
            if (identity == Level.TRUE) return DONE;
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.FALSE) {
                messages.add(Message.newMessage(getLocation(), Message.IGNORING_RESULT_OF_METHOD_CALL));
                statementAnalysis.errorFlags.errorValue.set(true);
            }
        }
        return DONE;
    }

    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
    }

    public List<StatementAnalyser> lastStatementsOfSubBlocks() {
        return navigationData.blocks.get().stream().map(StatementAnalyser::lastStatement).collect(Collectors.toList());
    }


    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
        }

        @Override
        public Set<String> allUnqualifiedVariableNames() {
            return statementAnalysis.allUnqualifiedVariableNames(getCurrentType().typeInfo);
        }

        @Override
        public int getIteration() {
            return iteration;
        }

        @Override
        public TypeAnalyser getCurrentType() {
            return myMethodAnalyser.myTypeAnalyser;
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
        public FieldAnalyser getCurrentField() {
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
        public EvaluationContext child(Value condition) {
            return new EvaluationContextImpl(iteration, conditionManager.addCondition(condition));
        }

        @Override
        public int getProperty(Value value, VariableProperty variableProperty) {

            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof VariableValue) {
                Variable variable = ((VariableValue) value).variable;
                if (VariableProperty.NOT_NULL == variableProperty && MultiLevel.isEffectivelyNotNull(notNullAccordingToConditionManager(value))) {
                    return Level.best(MultiLevel.EFFECTIVELY_NOT_NULL, statementAnalysis.getProperty(analyserContext, variable, variableProperty));
                }
                if (VariableProperty.SIZE == variableProperty) {
                    Value sizeRestriction = conditionManager.individualSizeRestrictions().get(variable);
                    if (sizeRestriction != null) {
                        return sizeRestriction.encodedSizeRestriction();
                    }
                }
                return statementAnalysis.getProperty(analyserContext, variable, variableProperty);
            }
            // the following situation occurs when the state contains, e.g., not (null == map.get(a)),
            // and we need to evaluate the NOT_NULL property in the return transfer value in StatementAnalyser
            // See TestSMapList; also, same situation, we may have a conditional value, and the condition will decide...
            // TODO smells like dedicated code
            if (!(value.isInstanceOf(ConstantValue.class)) && conditionManager.haveNonEmptyState() && !conditionManager.inErrorState()) {
                if (VariableProperty.NOT_NULL == variableProperty) {
                    int notNull = notNullAccordingToConditionManager(value);
                    if (notNull != Level.DELAY) return notNull;
                }
                // TODO add SIZE support?
            }
            // redirect to Value.getProperty()
            // this is the only usage of this method; all other evaluation of a Value in an evaluation context
            // must go via the current method
            return value.getProperty(this, variableProperty);

        }


        /**
         * Conversion from state to notNull numeric property value, combined with the existing state
         *
         * @param value any non-constant, non-variable value, to be combined with the current state
         * @return the numeric VariableProperty.NOT_NULL value
         */
        private int notNullAccordingToConditionManager(Value value) {
            if (conditionManager.state == UnknownValue.EMPTY || ConditionManager.isDelayed(conditionManager.state))
                return Level.DELAY;

            // action: if we add value == null, and nothing changes, we know it is true, we rely on value.getProperty
            // if the whole thing becomes false, we know it is false, which means we can return Level.TRUE
            Value equalsNull = EqualsValue.equals(NullValue.NULL_VALUE, value, ObjectFlow.NO_FLOW, this);
            if (equalsNull == BoolValue.FALSE) return MultiLevel.EFFECTIVELY_NOT_NULL;
            Value withCondition = conditionManager.combineWithState(equalsNull);
            if (withCondition == BoolValue.FALSE) return MultiLevel.EFFECTIVELY_NOT_NULL; // we know != null
            if (withCondition.equals(equalsNull)) return MultiLevel.NULLABLE; // we know == null was already there
            return Level.DELAY;
        }

        @Override
        public boolean isNotNull0(Value value) {
            return MultiLevel.isEffectivelyNotNull(getProperty(value, VariableProperty.NOT_NULL));
        }

        @Override
        public Value currentValue(Variable variable) {
            VariableInfo vi = statementAnalysis.find(analyserContext, variable);
            return vi.valueForNextStatement();
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            Value currentValue = currentValue(variable);
            if (currentValue instanceof VariableValue) {
                return statementAnalysis.find(analyserContext, variable).getProperty(variableProperty);
            }
            return currentValue.getPropertyOutsideContext(variableProperty);
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
        public Set<Variable> linkedVariables(Value value) {
            if (value instanceof VariableValue variableValue) {
                TypeInfo typeInfo = variableValue.variable.parameterizedType().bestTypeInfo();
                boolean notSelf = typeInfo != getCurrentType().typeInfo;
                if (notSelf) {
                    VariableInfo variableInfo = statementAnalysis.find(analyserContext, variableValue.variable);
                    int immutable = variableInfo.getProperty(VariableProperty.IMMUTABLE);
                    if (immutable == MultiLevel.DELAY) return null;
                    if (MultiLevel.isE2Immutable(immutable)) return Set.of();
                }
                return Set.of(variableValue.variable);
            }
            return value.linkedVariables(this);
        }
    }

    public class SetProperty implements StatementAnalysis.StatementAnalysisModification {
        public final Variable variable;
        public final VariableProperty property;
        public final int value;

        public SetProperty(Variable variable, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = variable;
        }

        @Override
        public void run() {
            VariableInfo variableInfo = statementAnalysis.find(analyserContext, variable);
            int current = variableInfo.getProperty(property);
            if (current < value) {
                variableInfo.setProperty(property, value);
            }

            Value currentValue = variableInfo.valueForNextStatement();
            VariableValue valueWithVariable;
            if ((valueWithVariable = currentValue.asInstanceOf(VariableValue.class)) == null) return;
            Variable other = valueWithVariable.variable;
            if (!variable.equals(other)) {
                statementAnalysis.addProperty(analyserContext, other, property, value);
            }
        }

        @Override
        public String toString() {
            return "SetProperty{" +
                    "variable=" + variable +
                    ", property=" + property +
                    ", value=" + value +
                    '}';
        }
    }


    public class RaiseErrorMessage implements StatementAnalysis.StatementAnalysisModification {
        private final Message message;

        public RaiseErrorMessage(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            messages.add(message);
        }

        @Override
        public String toString() {
            return "RaiseErrorMessage{" +
                    "message=" + message +
                    '}';
        }
    }


    public class ErrorAssigningToFieldOutsideType implements StatementAnalysis.StatementAnalysisModification {
        private final FieldInfo fieldInfo;
        private final Location location;

        public ErrorAssigningToFieldOutsideType(FieldInfo fieldInfo, Location location) {
            this.fieldInfo = fieldInfo;
            this.location = location;
        }

        @Override
        public void run() {
            if (!statementAnalysis.errorFlags.errorAssigningToFieldOutsideType.isSet(fieldInfo)) {
                statementAnalysis.errorFlags.errorAssigningToFieldOutsideType.put(fieldInfo, true);
                messages.add(Message.newMessage(location, Message.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE));
            }
        }

        @Override
        public String toString() {
            return "ErrorAssigningToFieldOutsideType{" +
                    "fieldInfo=" + fieldInfo +
                    ", location=" + location +
                    '}';
        }
    }

    public class ParameterShouldNotBeAssignedTo implements StatementAnalysis.StatementAnalysisModification {
        private final ParameterInfo parameterInfo;
        private final Location location;

        public ParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo, Location location) {
            this.parameterInfo = parameterInfo;
            this.location = location;
        }

        @Override
        public void run() {
            if (!statementAnalysis.errorFlags.parameterAssignments.isSet(parameterInfo)) {
                statementAnalysis.errorFlags.parameterAssignments.put(parameterInfo, true);
                messages.add(Message.newMessage(location, Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO));
            }
        }

        @Override
        public String toString() {
            return "ParameterShouldNotBeAssignedTo{" +
                    "parameterInfo=" + parameterInfo +
                    ", location=" + location +
                    '}';
        }
    }


    public class AddVariable implements StatementAnalysis.StatementAnalysisModification {
        private final Variable variable;

        public AddVariable(Variable variable) {
            this.variable = variable;
        }

        @Override
        public void run() {
            statementAnalysis.find(analyserContext, variable);
        }

        @Override
        public String toString() {
            return "AddVariable{" +
                    "variable=" + variable +
                    '}';
        }
    }

    public class LinkVariable implements StatementAnalysis.StatementAnalysisModification {
        public final Variable variable;
        public final List<Variable> to;

        public LinkVariable(Variable variable, Set<Variable> to) {
            this.variable = variable;
            this.to = ImmutableList.copyOf(to);
        }

        @Override
        public void run() {
            statementAnalysis.assertVariableExists(variable);
            to.forEach(statementAnalysis::assertVariableExists);
            if (!statementAnalysis.dependencyGraph.isFrozen()) {
                statementAnalysis.dependencyGraph.addNode(variable, to);
            }
        }

        @Override
        public String toString() {
            return "LinkVariable{" +
                    "variable=" + variable +
                    ", to=" + to +
                    '}';
        }
    }

}
