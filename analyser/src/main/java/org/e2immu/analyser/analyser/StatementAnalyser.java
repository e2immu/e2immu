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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.pattern.MatchResult;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.pattern.Replacement;
import org.e2immu.analyser.pattern.Replacer;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.AnalysisStatus.PROGRESS;
import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);

    public final StatementAnalysis statementAnalysis;
    private VariableDataImpl.Builder variableDataBuilder = new VariableDataImpl.Builder();

    private final MethodAnalyser myMethodAnalyser;

    public NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // if true, we should ignore errors on the condition in the next iterations
    // if(x == null) throw ... causes x to become @NotNull; in the next iteration, x==null cannot happen,
    // which would cause an error; it is this error that is eliminated
    private boolean ignoreErrorsOnCondition;

    private AnalysisStatus analysisStatus;
    private final AnalyserContext analyserContext;
    private final Messages messages = new Messages();

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index) {
        this.analyserContext = analyserContext;
        this.myMethodAnalyser = methodAnalyser;
        this.statementAnalysis = new StatementAnalysis(statement, parent, index);
    }

    public static StatementAnalyser recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd) {
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
            String iPlusSt = indices + "." + statementIndex;
            StatementAnalyser statementAnalyser = new StatementAnalyser(analyserContext, myMethodAnalyser, statement, parent, iPlusSt);
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
            if (structure.haveStatements()) {
                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser, parent, statements, iPlusSt + "." + blockIndex, true);
                blocks.add(subStatementAnalyser);
                analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser, parent, statements, iPlusSt + "." + blockIndex, true);
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
     * Main method, which follows the navigation chain
     *
     * @param iteration           the current iteration
     * @param forwardAnalysisInfo information from the level above
     * @return all modifications to be done to parameters, methods
     */
    public StatementAnalyserResult analyse(int iteration, ForwardAnalysisInfo forwardAnalysisInfo) {
        try {
            // skip all the statements that are already in the DONE state...
            StatementAnalyser statementAnalyser = goToFirstStatementToAnalyse();
            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
            if (statementAnalyser == null) return builder.setAnalysisStatus(DONE).build();
            StatementAnalyser previousStatement = null;
            do {
                EvaluationContext evaluationContext = new EvaluationContextImpl(iteration);
                // first attempt at detecting a transformation
                boolean wasReplacement = checkForPatterns(evaluationContext);
                statementAnalyser = statementAnalyser.followReplacements();
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.statementAnalysis;
                StatementAnalyserResult result = statementAnalyser.analyseReplacedStatement(iteration,
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
                parent(), statements, startIndex, false);
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
            Optional<MatchResult<StatementAnalyser>> matchResult = patternMatcher.match(methodInfo, this);
            if (matchResult.isPresent()) {
                MatchResult<StatementAnalyser> mr = matchResult.get();
                Optional<Replacement> replacement = patternMatcher.registeredReplacement(mr.pattern);
                if (replacement.isPresent()) {
                    Replacement r = replacement.get();
                    log(TRANSFORM, "Replacing {} with {} in {} at {}", mr.pattern.name, r.name,
                            methodInfo.distinguishingName(), statementAnalysis.index);
                    Replacer.replace(evaluationContext, mr, r);
                    patternMatcher.reset(methodInfo);
                    return true;
                }
            }
        }
        return false;
    }

    private Location getLocation() {
        return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
    }

    /**
     * @param iteration                 the iteration
     * @param wasReplacement            boolean, to ensure that the effect of a replacement warrants continued analysis
     * @param previousStatementAnalysis null if there was no previous statement in this block
     * @return the effects that the analysis of this statement has on parameters, methods
     */
    private StatementAnalyserResult analyseReplacedStatement(int iteration,
                                                             boolean wasReplacement,
                                                             StatementAnalysis previousStatementAnalysis,
                                                             ForwardAnalysisInfo forwardAnalysisInfo) {
        String statementId = statementAnalysis.index;
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration);

        if (statementAnalysis.flowData.computeGuaranteedToBeReached(previousStatementAnalysis, forwardAnalysisInfo.execution) &&
                !statementAnalysis.inErrorState()) {
            messages.add(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
            statementAnalysis.errorFlags.errorValue.set(true);
        }

        analysisStatus = analyseSingleStatement3(evaluationContext).combine(wasReplacement ? PROGRESS : DONE);

        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVariableVisitors) {
            variableDataBuilder.variableInfos().forEach(variableInfo -> statementAnalyserVariableVisitor.visit(
                    new StatementAnalyserVariableVisitor.Data(
                            evaluationContext.getIteration(),
                            myMethodAnalyser.methodInfo,
                            statementId,
                            variableInfo.getName(),
                            variableInfo.getVariable(),
                            variableInfo.getCurrentValue(),
                            variableInfo.getStateOnAssignment(),
                            variableInfo.getObjectFlow(), variableInfo.properties())));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVisitors) {
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            evaluationContext.getIteration(),
                            myMethodAnalyser.methodInfo,
                            statementAnalysis,
                            statementAnalysis.index,
                            statementAnalysis.stateData.conditionManager.get().condition,
                            statementAnalysis.stateData.conditionManager.get().state));
        }

        // analyse data components

        // flow
        statementAnalysis.flowData.analyse(this, previousStatementAnalysis);
        StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            notNullEscapes(builder);
        }

        // variable data
        VariableDataImpl variableData = variableDataBuilder.build();
        statementAnalysis.variableData.set(variableData);
        variableDataBuilder = null; // drop some data

        // method level data
        StatementAnalyserResult result = statementAnalysis.methodLevelData.analyse(evaluationContext, variableData,
                previousStatementAnalysis == null ? null : previousStatementAnalysis.methodLevelData,
                statementAnalysis.stateData);

        // error flags
        statementAnalysis.errorFlags.analyse(statementAnalysis, previousStatementAnalysis);

        // state data
        statementAnalysis.stateData.analyse();

        // check for some errors
        detectErrors();
        return result;
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
     */
    private void apply(EvaluationResult evaluationResult, StatementAnalysis previous) {
        statementAnalysis.stateData.apply(evaluationResult, previous == null ? null : previous.stateData);

        // all modifications get applied
        evaluationResult.getModificationStream().forEach(statementAnalysis::apply);
    }


    // whatever that has not been picked up by the notNull and the size escapes
    // + preconditions by calling other methods with preconditions!

    private void precondition(EvaluationContext evaluationContext, StatementAnalyser parentStatement) {
        EvaluationResult preconditionResult = conditionManager.escapeCondition(variableProperties);
        if (precondition != UnknownValue.EMPTY) {
            boolean atLeastFieldOrParameterInvolved = precondition.variables().stream().anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
            if (atLeastFieldOrParameterInvolved) {
                log(VARIABLE_PROPERTIES, "Escape with precondition {}", precondition);

                // set the precondition on the top level statement
                if (!parentStatement.precondition.isSet()) {
                    parentStatement.precondition.set(precondition);
                }
                if (!ignoreErrorsOnCondition) {
                    log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                    ignoreErrorsOnCondition = true;
                }
            }
        }
    }

    private void notNullEscapes(StatementAnalyserResult.Builder builder) {
        Set<Variable> nullVariables = statementAnalysis.stateData.conditionManager.get().findIndividualNullConditions();
        for (Variable nullVariable : nullVariables) {
            log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.detailedString());
            ParameterAnalysis parameterAnalysis = myMethodAnalyser.getParameterAnalyser((ParameterInfo) nullVariable).parameterAnalysis;
            builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

            // as a context property
            variableDataBuilder.addProperty(nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
            if (!ignoreErrorsOnCondition) {
                log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                ignoreErrorsOnCondition = true;
            }
        }
    }

    private void sizeEscapes(StatementAnalyserResult.Builder builder) {
        Map<Variable, Value> individualSizeRestrictions = statementAnalysis.stateData.conditionManager.get().findIndividualSizeRestrictionsInCondition();
        for (Map.Entry<Variable, Value> entry : individualSizeRestrictions.entrySet()) {
            ParameterInfo parameterInfo = (ParameterInfo) entry.getKey();
            Value negated = NegatedValue.negate(entry.getValue());
            log(VARIABLE_PROPERTIES, "Escape with check on size on {}: {}", parameterInfo.detailedString(), negated);
            int sizeRestriction = negated.encodedSizeRestriction();
            if (sizeRestriction > 0) { // if the complement is a meaningful restriction
                ParameterAnalysis parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                builder.add(parameterAnalysis.new SetProperty(VariableProperty.SIZE, sizeRestriction));

                variableDataBuilder.addProperty(parameterInfo, VariableProperty.SIZE, sizeRestriction);
                if (!ignoreErrorsOnCondition) {
                    log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                    ignoreErrorsOnCondition = true;
                }
            }
        }
    }


    private AnalysisStatus analyseSingleStatement3(EvaluationContext evaluationContext) {
        boolean changes = false;

        Structure structure = statementAnalysis.statement.getStructure();


        // PART 1: filling of of the variable properties: parameters of statement "forEach" (duplicated further in PART 10
        // but then for variables in catch clauses)

        boolean assignedInLoop = statementAnalysis.statement instanceof LoopStatement;
        LocalVariableReference theLocalVariableReference;
        if (structure.localVariableCreation != null) {
            theLocalVariableReference = new LocalVariableReference(structure.localVariableCreation,
                    List.of());
            statementAnalysis.variableData.createLocalVariableOrParameter(theLocalVariableReference);
            if (assignedInLoop)
                statementAnalysis.variableData.addProperty(theLocalVariableReference, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
        } else {
            theLocalVariableReference = null;
        }

        // PART 2: more filling up of the variable properties: local variables in try-resources, for-loop, expression as statement
        // (normal local variables)

        for (Expression initialiser : structure.initialisers) {
            if (initialiser instanceof LocalVariableCreation) {
                LocalVariableReference lvr = new LocalVariableReference(((LocalVariableCreation) initialiser).localVariable, List.of());
                // the NO_VALUE here becomes the initial (and reset) value, which should not be a problem because variables
                // introduced here should not become "reset" to an initial value; they'll always be assigned one
                statementAnalysis.variableData.createLocalVariableOrParameter(lvr);
                if (assignedInLoop) {
                    statementAnalysis.variableData.addProperty(lvr, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                }
            }
            try {
                EvaluationResult result = initialiser.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                if (result.changes) changes = true;

                Value value = result.value;
                if (structure.initialisers.size() == 1 && value != null && value != NO_VALUE &&
                        !statementAnalysis.stateData.valueOfExpression.isSet()) {
                    statementAnalysis.stateData.valueOfExpression.set(value);
                }
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {}", statement);
                throw rte;
            }
        }

        if (statementAnalysis.statement instanceof LoopStatement) {
            if (!statementAnalysis.variableData.existingVariablesAssignedInLoop.isSet()) {
                Set<Variable> set = computeExistingVariablesAssignedInLoop(statement.statement, variableProperties);
                log(ASSIGNMENT, "Computed which existing variables are being assigned to in the loop {}: {}", statement.index,
                        Variable.detailedString(set));
                statement.existingVariablesAssignedInLoop.set(set);
            }
            statement.existingVariablesAssignedInLoop.get().forEach(variable ->
                    variableProperties.addPropertyAlsoRecords(variable, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE));
        }

        // PART 12: finally there are the updaters
        // used in for-statement, and the parameters of an explicit constructor invocation this(...)

        // for now, we put these BEFORE the main evaluation + the main block. One of the two should read the value that is being updated
        for (Expression updater : structure.updaters) {
            EvaluationResult result = updater.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            if (result.changes) changes = true;
        }

        // PART 4: evaluation of the core expression of the statement (if the statement has such a thing)

        final Value value;
        if (structure.expression == EmptyExpression.EMPTY_EXPRESSION) {
            value = null;
        } else {
            try {
                EvaluationResult result = structure.expression.evaluate(evaluationContext, structure.forwardEvaluationInfo);
                if (result.changes) changes = true;
                value = result.encounteredUnevaluatedVariables ? NO_VALUE : result.value;
                if (result.encounteredUnevaluatedVariables) {
                    variableProperties.setDelayedEvaluation(true);
                }
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate expression in statement {}", statement);
                throw rte;
            }
        }
        log(VARIABLE_PROPERTIES, "After eval expression: statement {}: {}", statementAnalysis.index, variableProperties);

        if (value != null && value != NO_VALUE && !statementAnalysis.stateData.valueOfExpression.isSet()) {
            statementAnalysis.stateData.valueOfExpression.set(value);
        }

        if (statementAnalysis.statement instanceof ForEachStatement && value != null) {
            ArrayValue arrayValue;
            if (((arrayValue = value.asInstanceOf(ArrayValue.class)) != null) &&
                    arrayValue.values.stream().allMatch(variableProperties::isNotNull0)) {
                statementAnalysis.variableData.addProperty(theLocalVariableReference, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
            }
        }

        // PART 5: checks for ReturnStatement

        if (statementAnalysis.statement instanceof ReturnStatement && !myMethodAnalyser.methodInfo.isVoid()
                && !myMethodAnalyser.methodInfo.isConstructor) {
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
            if (value == null) {
                if (!transferValue.linkedVariables.isSet())
                    transferValue.linkedVariables.set(Set.of());
                if (!transferValue.value.isSet()) transferValue.value.set(NO_VALUE);
            } else if (value != NO_VALUE) {
                Set<Variable> vars = value.linkedVariables(variableProperties);
                if (vars == null) {
                    log(DELAYED, "Linked variables is delayed on transfer");
                } else if (!transferValue.linkedVariables.isSet()) {
                    transferValue.linkedVariables.set(vars);
                }
                if (!transferValue.value.isSet()) {
                    VariableValue variableValue;
                    if ((variableValue = value.asInstanceOf(VariableValue.class)) != null) {
                        // we pass on both value and variableValue, as the latter may be wrapped in a
                        // PropertyValue
                        transferValue.value.set(new VariableValue(value, variableValue, variableProperties, value.getObjectFlow()));
                    } else {
                        transferValue.value.set(value);
                    }
                }
                for (VariableProperty variableProperty : VariableProperty.INTO_RETURN_VALUE_SUMMARY) {
                    int v = variableProperties.getProperty(value, variableProperty);
                    if (variableProperty == VariableProperty.IDENTITY && v == Level.DELAY) {
                        int methodDelay = variableProperties.getProperty(value, VariableProperty.METHOD_DELAY);
                        if (methodDelay != Level.TRUE) {
                            v = Level.FALSE;
                        }
                    }
                    int current = transferValue.getProperty(variableProperty);
                    if (v > current) {
                        transferValue.properties.put(variableProperty, v);
                    }
                }
                int immutable = variableProperties.getProperty(value, VariableProperty.IMMUTABLE);
                if (immutable == Level.DELAY) immutable = MultiLevel.MUTABLE;
                int current = transferValue.getProperty(VariableProperty.IMMUTABLE);
                if (immutable > current) {
                    transferValue.properties.put(VariableProperty.IMMUTABLE, immutable);
                }

            } else {
                log(VARIABLE_PROPERTIES, "NO_VALUE for return statement in {} {} -- delaying",
                        evaluationContext.logLocation());
            }
        }

        // PART 6: checks for IfElse

        boolean haveADefaultCondition = false;

        if (statementAnalysis.statement instanceof IfElseStatement || statementAnalysis.statement instanceof SwitchStatement) {
            Objects.requireNonNull(value);

            Value previousConditional = statementAnalysis.parent.stateData.conditionManager.get().getCondition();
            Value combinedWithCondition = variableProperties.conditionManager.evaluateWithCondition(value);
            Value combinedWithState = variableProperties.conditionManager.evaluateWithState(value);

            haveADefaultCondition = true;

            // we have no idea which of the 2 remains
            boolean noEffect = combinedWithCondition != NO_VALUE && combinedWithState != NO_VALUE &&
                    (combinedWithCondition.equals(previousConditional) || combinedWithState.isConstant())
                    || combinedWithCondition.isConstant();

            if (noEffect && !ignoreErrorsOnCondition && !statementAnalysis.inErrorState()) {
                messages.add(Message.newMessage(evaluationContext.getLocation(), Message.CONDITION_EVALUATES_TO_CONSTANT));
                statementAnalysis.errorFlags.errorValue.set(true);
            }

        } else {

            if (value != null && statementAnalysis.statement instanceof ForEachStatement) {
                int size = variableProperties.getProperty(value, VariableProperty.SIZE);
                if (size == Level.SIZE_EMPTY && !statementAnalysis.inErrorState()) {
                    messages.add(Message.newMessage(evaluationContext.getLocation(), Message.EMPTY_LOOP));
                    statementAnalysis.errorValue.set(true);
                }
            }
        }

        // PART 7: the primary block, if it's there
        // the primary block has an expression in case of "if", "while", and "synchronized"
        // in the first two cases, we'll treat the expression as a condition

        List<StatementAnalysis> startOfBlocks = statementAnalysis.navigationData.blocks.get();
        List<VariableProperties> evaluationContextsGathered = new ArrayList<>();
        boolean allButLastSubStatementsEscape = true;
        Value defaultCondition = NO_VALUE;

        List<Value> conditions = new ArrayList<>();
        List<BreakOrContinueStatement> breakOrContinueStatementsInChildren = new ArrayList<>();

        int start;

        if (structure.haveNonEmptyBlock()) {
            StatementAnalysis startOfFirstBlock = startOfBlocks.get(0);
            boolean statementsExecutedAtLeastOnce = structure.statementsExecutedAtLeastOnce.test(value);

            // in a synchronized block, some fields can behave like variables
            boolean inSyncBlock = statementAnalysis.statement instanceof SynchronizedStatement;
            VariableProperties variablePropertiesWithValue = (VariableProperties) variableProperties.childPotentiallyInSyncBlock
                    (value, uponUsingConditional, inSyncBlock, statementsExecutedAtLeastOnce);

            computeVariablePropertiesOfBlock(startOfFirstBlock, variablePropertiesWithValue);
            evaluationContextsGathered.add(variablePropertiesWithValue);
            breakOrContinueStatementsInChildren.addAll(startOfFirstBlock.breakAndContinueStatements.isSet() ?
                    startOfFirstBlock.breakAndContinueStatements.get() : List.of());

            allButLastSubStatementsEscape = startOfFirstBlock.neverContinues.get();
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

            // PART 9: evaluate the sub-expression; this can be done in the CURRENT variable properties
            // (only real evaluation for Java 14 conditionals in switch)

            Value valueForSubStatement;
            if (EmptyExpression.DEFAULT_EXPRESSION == subStatements.expression) {
                if (start == 1) valueForSubStatement = NegatedValue.negate(value);
                else {
                    if (conditions.isEmpty()) {
                        valueForSubStatement = BoolValue.TRUE;
                    } else {
                        Value[] negated = conditions.stream().map(NegatedValue::negate).toArray(Value[]::new);
                        valueForSubStatement = new AndValue(ObjectFlow.NO_FLOW).append(negated);
                    }
                }
                defaultCondition = valueForSubStatement;
            } else if (EmptyExpression.FINALLY_EXPRESSION == subStatements.expression ||
                    EmptyExpression.EMPTY_EXPRESSION == subStatements.expression) {
                valueForSubStatement = null;
            } else {
                // real expression
                EvaluationResult result = computeVariablePropertiesOfExpression(subStatements.expression,
                        variableProperties, statement, subStatements.forwardEvaluationInfo);
                valueForSubStatement = result.value;
                if (result.changes) changes = true;
                conditions.add(valueForSubStatement);
            }

            // PART 10: create subContext, add parameters of sub statements, execute

            boolean statementsExecutedAtLeastOnce = subStatements.statementsExecutedAtLeastOnce.test(value);
            VariableProperties subContext = (VariableProperties) variableProperties
                    .child(valueForSubStatement, uponUsingConditional, statementsExecutedAtLeastOnce);

            if (subStatements.localVariableCreation != null) {
                LocalVariableReference lvr = new LocalVariableReference(subStatements.localVariableCreation, List.of());
                subContext.createLocalVariableOrParameter(lvr);
                subContext.addProperty(lvr, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                subContext.addProperty(lvr, VariableProperty.READ, Level.READ_ASSIGN_ONCE);
            }

            StatementAnalysis subStatementStart = statementAnalysis.navigationData.blocks.get().get(count);
            computeVariablePropertiesOfBlock(subStatementStart, subContext);
            evaluationContextsGathered.add(subContext);
            breakOrContinueStatementsInChildren.addAll(subStatementStart.breakAndContinueStatements.isSet() ?
                    subStatementStart.breakAndContinueStatements.get() : List.of());

            // PART 11 post process

            if (count < startOfBlocks.size() - 1 && !subStatementStart.neverContinues.get()) {
                allButLastSubStatementsEscape = false;
            }
            // the last one escapes as well... then we should not add to the state
            if (count == startOfBlocks.size() - 1 && subStatementStart.neverContinues.get()) {
                allButLastSubStatementsEscape = false;
            }
        }

        if (!evaluationContextsGathered.isEmpty()) {
            variableProperties.copyBackLocalCopies(evaluationContextsGathered, structure.noBlockMayBeExecuted);
        }

        // we don't want to set the value for break statements themselves; that happens higher up
        if (haveSubBlocks(structure) && !statement.breakAndContinueStatements.isSet()) {
            statement.breakAndContinueStatements.set(breakOrContinueStatementsInChildren);
        }

        if (allButLastSubStatementsEscape && haveADefaultCondition && !defaultCondition.isConstant()) {
            variableProperties.conditionManager.addToState(defaultCondition);
            log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", defaultCondition);
        }

        // FINALLY, set the state

        if (!variableProperties.conditionManager.delayedState() && !statement.state.isSet()) {
            statement.state.set(variableProperties.conditionManager.getState());
        }

        return changes;
    }

    private static boolean haveSubBlocks(Structure structure) {
        return structure.haveNonEmptyBlock() ||
                structure.statements != null && !structure.statements.isEmpty() ||
                !structure.subStatements.isEmpty();
    }

    private Set<Variable> computeExistingVariablesAssignedInLoop(Statement statement, VariableProperties variableProperties) {
        return statement.collect(Assignment.class).stream()
                .flatMap(a -> a.assignmentTarget().stream())
                .filter(variableProperties::isKnown)
                .collect(Collectors.toSet());
    }

    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li>NYR + local variable created higher up + EXIT (return stmt, anything beyond the level of the CREATED)</li>
     *     <li>NYR + escape</li>
     * </ul>
     *
     * @return if an error was generated
     */
    private void checkUselessAssignments() {
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            // we run at the local level
            List<String> toRemove = new ArrayList<>();
            for (VariableInfo variableInfo : variableDataBuilder.variableInfos()) {
                if (variableInfo.getProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT) == Level.TRUE) {
                    boolean notAssignedInLoop = variableInfo.getProperty(VariableProperty.ASSIGNED_IN_LOOP) != Level.TRUE;
                    // TODO at some point we will do better than "notAssignedInLoop"
                    boolean useless = bestAlwaysInterrupt == InterruptsFlow.ESCAPE || notAssignedInLoop && (
                            alwaysInterrupts && variableDataBuilder.isLocalVariable(variableInfo) ||
                                    variableInfo.isNotLocalCopy() && variableInfo.isLocalVariableReference());
                    if (useless) {
                        if (!statementAnalysis.errorFlags.uselessAssignments.isSet(variableInfo.getVariable())) {
                            statementAnalysis.errorFlags.uselessAssignments.put(variableInfo.getVariable(), true);
                            messages.add(Message.newMessage(getLocation(), Message.USELESS_ASSIGNMENT, variableInfo.getName()));
                        }
                        if (variableInfo.isLocalCopy()) toRemove.add(variableInfo.getName());
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                log(VARIABLE_PROPERTIES, "Removing local info for variables {}", toRemove);
                variableDataBuilder.removeAllVariables(toRemove);
            }
        }
    }


    // TODO move to variableData

    private void unusedLocalVariablesCheck() {
        // we run at the local level
        for (VariableInfo variableInfo : variableDataBuilder.variableInfos()) {
            if (variableInfo.isNotLocalCopy() && variableInfo.isLocalVariableReference()
                    && variableInfo.getProperty(VariableProperty.READ) < Level.READ_ASSIGN_ONCE) {
                if (!(variableInfo.isLocalVariableReference())) {
                    throw new UnsupportedOperationException("?? CREATED only added to local variables");
                }
                LocalVariable localVariable = ((LocalVariableReference) variableInfo.getVariable()).variable;
                if (!statementAnalysis.errorFlags.unusedLocalVariables.isSet(localVariable)) {
                    statementAnalysis.errorFlags.unusedLocalVariables.put(localVariable, true);
                    messages.add(Message.newMessage(getLocation(), Message.UNUSED_LOCAL_VARIABLE, localVariable.name));
                }
            }
        }
    }


    private void checkUnusedReturnValue() {
        if (statementAnalysis.statement instanceof ExpressionAsStatement &&
                ((ExpressionAsStatement) statementAnalysis.statement).expression instanceof MethodCall &&
                !statementAnalysis.inErrorState()) {
            MethodCall methodCall = (MethodCall) (((ExpressionAsStatement) statementAnalysis.statement).expression);
            if (methodCall.methodInfo.returnType().isVoid()) return;
            int identity = methodCall.methodInfo.methodAnalysis.get().getProperty(VariableProperty.IDENTITY);
            if (identity != Level.FALSE) return;// DELAY: we don't know, wait; true: OK not a problem
            MethodAnalysis methodAnalysis = getMethodAnalysis(methodCall.methodInfo);
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.FALSE) {
                messages.add(Message.newMessage(getLocation(), Message.IGNORING_RESULT_OF_METHOD_CALL));
                statementAnalysis.errorFlags.errorValue.set(true);
            }
        }
    }

    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
    }

    private void detectErrors() {
        checkUnusedReturnValue();
        checkUselessAssignments();
    }

    public void initialiseParametersAsVariables(List<ParameterAnalyser> parameterAnalysers) {
        // TODO
    }

    public List<StatementAnalyser> lastStatementsOfSubBlocks() {
        return navigationData.blocks.get().stream().map(StatementAnalyser::lastStatement).collect(Collectors.toList());
    }


    private class EvaluationContextImpl implements EvaluationContext {

        private final int iteration;

        private EvaluationContextImpl(int iteration) {
            this.iteration = iteration;
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
        public EvaluationContext child(Value condition, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
            return null;
        }

        @Override
        public Value currentValue(Variable variable) {

            return null;
        }

        @Override
        public Value currentValue(String variableName) {
            return currentValue(variableByName(variableName));
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return null;
        }
    }


    public class SetProperty implements StatementAnalysis.StatementAnalysisModification {
        private final Variable variable;
        private final VariableProperty property;
        private final int value;

        public SetProperty(Variable variable, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = variable;
        }

        @Override
        public void run() {
            VariableInfoImpl.Builder aboutVariable = variableDataBuilder.find(variable);
            if (aboutVariable == null) {
                return;
            }
            int current = aboutVariable.getProperty(property);
            if (current < value) {
                aboutVariable.setProperty(property, value);
            }

            Value currentValue = aboutVariable.getCurrentValue();
            VariableValue valueWithVariable;
            if ((valueWithVariable = currentValue.asInstanceOf(VariableValue.class)) == null) return;
            Variable other = valueWithVariable.variable;
            if (!variable.equals(other)) {
                variableDataBuilder.addProperty(other, property, value);
            }
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
    }


    public class AddVariable implements StatementAnalysis.StatementAnalysisModification {
        private final Variable variable;

        public AddVariable(Variable variable) {
            this.variable = variable;
        }

        @Override
        public void run() {

        }
    }

    public class LinkVariable implements StatementAnalysis.StatementAnalysisModification {
        private final Variable variable;
        private final Set<Variable> to;

        public LinkVariable(Variable variable, Set<Variable> to) {
            this.variable = variable;
            this.to = to;
        }

        @Override
        public void run() {

        }
    }
}
