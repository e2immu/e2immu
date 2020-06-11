/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

// the statement analyser does not add annotations, but it does raise errors
// output of the only public method is in changes to the properties of the variables in the evaluation context

public class StatementAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);

    private final TypeContext typeContext;
    private final MethodAnalysis methodAnalysis;
    private final MethodInfo methodInfo;

    public StatementAnalyser(TypeContext typeContext, MethodInfo methodInfo) {
        this.typeContext = typeContext;
        this.methodAnalysis = methodInfo.methodAnalysis.get();
        this.methodInfo = methodInfo;
    }

    public boolean computeVariablePropertiesOfBlock(NumberedStatement startStatement, EvaluationContext evaluationContext) {
        VariableProperties variableProperties = (VariableProperties) evaluationContext;

        boolean changes = false;
        NumberedStatement statement = Objects.requireNonNull(startStatement); // for IntelliJ
        boolean neverContinues = false;
        boolean escapesViaException = false;
        List<BreakOrContinueStatement> breakAndContinueStatementsInBlocks = new ArrayList<>();

        try {
            while (statement != null) {
                String statementId = statement.streamIndices();
                variableProperties.setCurrentStatement(statement);

                if (variableProperties.conditionalManager.getConditional() == BoolValue.FALSE) {
                    variableProperties.raiseError(Message.UNREACHABLE_STATEMENT);
                }

                if (computeVariablePropertiesOfStatement(statement, variableProperties)) {
                    changes = true;
                }

                for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                        ((VariableProperties) evaluationContext).debugConfiguration.statementAnalyserVariableVisitors) {
                    variableProperties.variableProperties().forEach(aboutVariable -> {
                        statementAnalyserVariableVisitor.visit(((VariableProperties) evaluationContext).iteration, methodInfo,
                                statementId, aboutVariable.name, aboutVariable.variable,
                                aboutVariable.getCurrentValue(), aboutVariable.properties());
                    });
                }
                for (StatementAnalyserVisitor statementAnalyserVisitor : ((VariableProperties) evaluationContext).debugConfiguration.statementAnalyserVisitors) {
                    statementAnalyserVisitor.visit(((VariableProperties) evaluationContext).iteration, methodInfo, statement, variableProperties.conditionalManager.getConditional());
                }

                if (statement.statement instanceof ReturnStatement || statement.statement instanceof ThrowStatement) {
                    neverContinues = true;
                }
                if (statement.statement instanceof ThrowStatement) {
                    escapesViaException = true;
                }
                if (statement.statement instanceof BreakOrContinueStatement) {
                    breakAndContinueStatementsInBlocks.add((BreakOrContinueStatement) statement.statement);
                }
                // it is here that we'll inherit from blocks inside the statement
                if (statement.neverContinues.isSet() && statement.neverContinues.get()) neverContinues = true;
                if (statement.breakAndContinueStatements.isSet()) {
                    breakAndContinueStatementsInBlocks.addAll(filterBreakAndContinue(statement, statement.breakAndContinueStatements.get()));
                    if (!breakAndContinueStatementsInBlocks.isEmpty()) {
                        variableProperties.setGuaranteedToBeReachedInCurrentBlock(false);
                    }
                }
                if (statement.escapes.isSet() && statement.escapes.get()) escapesViaException = true;
                statement = statement.next.get().orElse(null);
            }
            // at the end, at the top level, there is a return, even if it is implicit
            boolean atTopLevel = variableProperties.depth == 0;
            if (atTopLevel) {
                neverContinues = true;
            }

            if (!startStatement.neverContinues.isSet()) {
                log(VARIABLE_PROPERTIES, "Never continues at end of block of {}? {}", startStatement.streamIndices(), neverContinues);
                startStatement.neverContinues.set(neverContinues);
            }
            if (!startStatement.escapes.isSet()) {
                log(VARIABLE_PROPERTIES, "Escapes at end of block of {}? {}", startStatement.streamIndices(), escapesViaException);
                startStatement.escapes.set(escapesViaException);

                if (escapesViaException) {
                    notNullEscapes(variableProperties, startStatement);
                    sizeEscapes(variableProperties, startStatement);
                    precondition(variableProperties, startStatement);
                }
            }
            // order is important, because unused gets priority
            if (unusedLocalVariablesCheck(variableProperties)) changes = true;
            if (uselessAssignments(variableProperties, escapesViaException, neverContinues)) changes = true;

            if (isLogEnabled(DEBUG_LINKED_VARIABLES) && !variableProperties.dependencyGraphWorstCase.isEmpty()) {
                log(DEBUG_LINKED_VARIABLES, "Dependency graph of linked variables best case:");
                variableProperties.dependencyGraphBestCase.visit((n, list) -> log(DEBUG_LINKED_VARIABLES, " -- {} --> {}", n.detailedString(),
                        list == null ? "[]" : StringUtil.join(list, Variable::detailedString)));
                log(DEBUG_LINKED_VARIABLES, "Dependency graph of linked variables worst case:");
                variableProperties.dependencyGraphWorstCase.visit((n, list) -> log(DEBUG_LINKED_VARIABLES, " -- {} --> {}", n.detailedString(),
                        list == null ? "[]" : StringUtil.join(list, Variable::detailedString)));
            }

            if (!startStatement.breakAndContinueStatements.isSet())
                startStatement.breakAndContinueStatements.set(ImmutableList.copyOf(breakAndContinueStatementsInBlocks));
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in statement analyser: {}", statement);
            throw rte;
        }
    }

    // whatever that has not been picked up by the notNull and the size escapes
    private static void precondition(VariableProperties variableProperties, NumberedStatement startStatement) {
        Value precondition = variableProperties.conditionalManager.escapeCondition();
        if (precondition != null) {
            log(VARIABLE_PROPERTIES, "Escape with precondition {}", precondition);
            MethodAnalysis methodAnalysis = variableProperties.getCurrentMethod().methodAnalysis.get();
            if (!methodAnalysis.precondition.isSet()) {
                methodAnalysis.precondition.set(precondition);
            }
            if (variableProperties.uponUsingConditional != null) {
                log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                variableProperties.uponUsingConditional.run();
            }
            Set<Variable> variables = precondition.variables();
            for (Variable variable : variables) {
                // we're guarding because we can have these escapes for sizes as well
                if (!startStatement.parent.removeVariablesFromConditional.isSet(variable)) {
                    startStatement.parent.removeVariablesFromConditional.put(variable, true);
                }
            }
        }
    }

    private static void notNullEscapes(VariableProperties variableProperties, NumberedStatement startStatement) {
        Set<Variable> nullVariables = variableProperties.conditionalManager.getNullConditionals(true);
        for (Variable nullVariable : nullVariables) {
            log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.detailedString());
            if (nullVariable instanceof ParameterInfo) {
                ((ParameterInfo) nullVariable).parameterAnalysis.get().improveProperty(VariableProperty.NOT_NULL, Level.TRUE);
            }
            // as a context property
            variableProperties.addProperty(nullVariable, VariableProperty.NOT_NULL, Level.TRUE);
            if (variableProperties.uponUsingConditional != null) {
                log(VARIABLE_PROPERTIES, "Disabled errors on if-statement");
                variableProperties.uponUsingConditional.run();
            }
            // now we need to make sure that there will not be an additional condition added after the if statement
            // the not-null is already in the properties. we need to communicate this one level up.

            // we're guarding because we can have these escapes for sizes as well
            if (!startStatement.parent.removeVariablesFromConditional.isSet(nullVariable)) {
                startStatement.parent.removeVariablesFromConditional.put(nullVariable, true);
            }
        }
    }

    private static void sizeEscapes(VariableProperties variableProperties, NumberedStatement startStatement) {
        Map<Variable, Value> individualSizeRestrictions = variableProperties.conditionalManager.getSizeRestrictions();
        for (Map.Entry<Variable, Value> entry : individualSizeRestrictions.entrySet()) {
            Variable variable = entry.getKey();
            Value negated = NegatedValue.negate(entry.getValue());
            log(VARIABLE_PROPERTIES, "Escape with check on size on {}: {}", variable.detailedString(), negated);
            int sizeRestriction = negated.encodedSizeRestriction();
            if (sizeRestriction > 0) { // if the complement is a meaningful restriction
                if (variable instanceof ParameterInfo) {
                    ((ParameterInfo) variable).parameterAnalysis.get().improveProperty(VariableProperty.SIZE, sizeRestriction);
                }
                variableProperties.addProperty(variable, VariableProperty.SIZE, sizeRestriction);
                if (variableProperties.uponUsingConditional != null) {
                    log(VARIABLE_PROPERTIES, "Disabled errors on if-statement");
                    variableProperties.uponUsingConditional.run();
                }

                // now we need to make sure that there will not be an additional condition added after the if statement
                // the not-null is already in the properties. we need to communicate this one level up.

                // we're guarding because we can have these escapes for not-nulls as well
                if (!startStatement.parent.removeVariablesFromConditional.isSet(variable)) {
                    startStatement.parent.removeVariablesFromConditional.put(variable, true);
                }
            }
        }
    }


    private Collection<? extends BreakOrContinueStatement> filterBreakAndContinue(NumberedStatement statement,
                                                                                  List<BreakOrContinueStatement> statements) {
        if (statement.statement instanceof LoopStatement) {
            String label = ((LoopStatement) statement.statement).label;
            // we only retain those break and continue statements for ANOTHER labelled statement
            // (the break statement has a label, and it does not equal the one of this loop)
            return statements.stream().filter(s -> s.label != null && !s.label.equals(label)).collect(Collectors.toList());
        } else if (statement.statement instanceof SwitchStatement) {
            // continue statements cannot be for a switch; must have a labelled statement to break from a loop outside the switch
            return statements.stream().filter(s -> s instanceof ContinueStatement || s.label != null).collect(Collectors.toList());
        } else return statements;
    }

    private boolean unusedLocalVariablesCheck(VariableProperties variableProperties) {
        boolean changes = false;
        // we run at the local level
        for (AboutVariable aboutVariable : variableProperties.variableProperties()) {
            if (aboutVariable.isNotLocalCopy() && aboutVariable.isLocalVariable()
                    && !Level.haveTrueAt(aboutVariable.getProperty(VariableProperty.READ), 0)) {
                if (!(aboutVariable.variable instanceof LocalVariableReference)) {
                    throw new UnsupportedOperationException("?? CREATED only added to local variables");
                }
                LocalVariable localVariable = ((LocalVariableReference) aboutVariable.variable).variable;
                if (!methodAnalysis.unusedLocalVariables.isSet(localVariable)) {
                    methodAnalysis.unusedLocalVariables.put(localVariable, true);
                    typeContext.addMessage(Message.newMessage(new Location(methodInfo), Message.UNUSED_LOCAL_VARIABLE, localVariable.name));
                    changes = true;
                }
            }
        }
        return changes;
    }

    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li>NYR + local variable created higher up + EXIT (return stmt, anything beyond the level of the CREATED)</li>
     *     <li>NYR + escape</li>
     * </ul>
     *
     * @param variableProperties context
     * @return if an error was generated
     */
    private boolean uselessAssignments(VariableProperties variableProperties,
                                       boolean escapesViaException,
                                       boolean neverContinuesBecauseOfReturn) {
        boolean changes = false;
        // we run at the local level
        List<String> toRemove = new ArrayList<>();
        for (AboutVariable aboutVariable : variableProperties.variableProperties()) {
            if (aboutVariable.getProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT) == Level.TRUE) {
                boolean notAssignedInLoop = aboutVariable.getProperty(VariableProperty.ASSIGNED_IN_LOOP) != Level.TRUE;
                // TODO at some point we will do better than "notAssignedInLoop"
                boolean useless = escapesViaException || notAssignedInLoop && (
                        neverContinuesBecauseOfReturn && variableProperties.isLocalVariable(aboutVariable) ||
                                aboutVariable.isNotLocalCopy() && aboutVariable.isLocalVariable());
                if (useless) {
                    if (!methodAnalysis.uselessAssignments.isSet(aboutVariable.variable)) {
                        methodAnalysis.uselessAssignments.put(aboutVariable.variable, true);
                        typeContext.addMessage(Message.newMessage(new Location(methodInfo), Message.USELESS_ASSIGNMENT, aboutVariable.name));
                        changes = true;
                    }
                    if (aboutVariable.isLocalCopy()) toRemove.add(aboutVariable.name);
                }
            }
        }
        if (!toRemove.isEmpty()) {
            log(VARIABLE_PROPERTIES, "Removing local info for variables {}", toRemove);
            variableProperties.removeAll(toRemove);
        }
        return changes;
    }

    private boolean computeVariablePropertiesOfStatement(NumberedStatement statement,
                                                         VariableProperties variableProperties) {
        boolean changes = false;
        CodeOrganization codeOrganization = statement.statement.codeOrganization();

        VariableProperty[] newLocalVariableProperties;
        if (statement.statement instanceof LoopStatement) {
            newLocalVariableProperties = new VariableProperty[]{VariableProperty.ASSIGNED_IN_LOOP};
        } else {
            newLocalVariableProperties = new VariableProperty[0];
        }

        // PART 1: filling of of the variable properties: parameters of statement "forEach" (duplicated further in PART 10
        // but then for variables in catch clauses)

        LocalVariableReference theLocalVariableReference;
        if (codeOrganization.localVariableCreation != null) {
            theLocalVariableReference = new LocalVariableReference(codeOrganization.localVariableCreation,
                    List.of());
            variableProperties.createLocalVariableOrParameter(theLocalVariableReference, newLocalVariableProperties);
        } else {
            theLocalVariableReference = null;
        }

        // PART 2: more filling up of the variable properties: local variables in try-resources, for-loop, expression as statement
        // (normal local variables)

        for (Expression initialiser : codeOrganization.initialisers) {
            if (initialiser instanceof LocalVariableCreation) {
                LocalVariableReference lvr = new LocalVariableReference(((LocalVariableCreation) initialiser).localVariable, List.of());
                // the NO_VALUE here becomes the initial (and reset) value, which should not be a problem because variables
                // introduced here should not become "reset" to an initial value; they'll always be assigned one
                variableProperties.createLocalVariableOrParameter(lvr, newLocalVariableProperties);
            }
            try {
                EvaluationResult result = computeVariablePropertiesOfExpression(initialiser, variableProperties, statement, ForwardEvaluationInfo.DEFAULT);
                if (result.changes) changes = true;
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {}", statement);
                throw rte;
            }
        }

        if (statement.statement instanceof LoopStatement) {
            if (!statement.existingVariablesAssignedInLoop.isSet()) {
                Set<Variable> set = computeExistingVariablesAssignedInLoop(codeOrganization, variableProperties);
                log(ASSIGNMENT, "Computed which existing variables are being assigned to in the loop {}: {}", statement.streamIndices(),
                        Variable.detailedString(set));
                statement.existingVariablesAssignedInLoop.set(set);
            }
            statement.existingVariablesAssignedInLoop.get().forEach(variable ->
                    variableProperties.addPropertyAlsoRecords(variable, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE));
        }

        // PART 12: finally there are the updaters
        // used in for-statement, and the parameters of an explicit constructor invocation this(...)

        // for now, we put these BEFORE the main evaluation + the main block. One of the two should read the value that is being updated
        for (Expression updater : codeOrganization.updaters) {
            EvaluationResult result = computeVariablePropertiesOfExpression(updater, variableProperties, statement, ForwardEvaluationInfo.DEFAULT);
            if (result.changes) changes = true;
        }

        // PART 4: evaluation of the core expression of the statement (if the statement has such a thing)

        Value value;
        if (codeOrganization.expression == EmptyExpression.EMPTY_EXPRESSION) {
            value = null;
        } else {
            try {
                EvaluationResult result = computeVariablePropertiesOfExpression(codeOrganization.expression,
                        variableProperties, statement, codeOrganization.forwardEvaluationInfo);
                if (result.changes) changes = true;
                value = result.encounteredUnevaluatedVariables ? NO_VALUE : result.value;
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate expression in statement {}", statement);
                throw rte;
            }
        }
        log(VARIABLE_PROPERTIES, "After eval expression: statement {}: {}", statement.streamIndices(), variableProperties);

        if (value != null && value != NO_VALUE && !statement.valueOfExpression.isSet()) {
            statement.valueOfExpression.set(value);
        }

        if (statement.statement instanceof ForEachStatement && value instanceof ArrayValue &&
                ((ArrayValue) value).values.stream().allMatch(variableProperties::isNotNull0)) {
            variableProperties.addProperty(theLocalVariableReference, VariableProperty.NOT_NULL, Level.TRUE);
        }

        // PART 5: checks for ReturnStatement

        if (statement.statement instanceof ReturnStatement) {
            String statementId = statement.streamIndices();
            TransferValue transferValue;
            if (methodAnalysis.returnStatementSummaries.isSet(statementId)) {
                transferValue = methodAnalysis.returnStatementSummaries.get(statementId);
            } else {
                transferValue = new TransferValue();
                methodAnalysis.returnStatementSummaries.put(statementId, transferValue);
                int fluent = (((ReturnStatement) statement.statement).fluent());
                transferValue.properties.put(VariableProperty.FLUENT, fluent);
            }
            if (value == null) {
                if (!transferValue.linkedVariables.isSet())
                    transferValue.linkedVariables.set(Set.of());
                if (!transferValue.value.isSet()) transferValue.value.set(NO_VALUE);
            } else if (value != NO_VALUE) {
                Set<Variable> vars = value.linkedVariables(true, variableProperties);
                if (!transferValue.linkedVariables.isSet()) {
                    transferValue.linkedVariables.set(vars);
                }
                if (!transferValue.value.isSet()) {
                    if (value instanceof VariableValue) {
                        transferValue.value.set(new VariableValuePlaceholder((VariableValue) value, variableProperties));
                    } else {
                        transferValue.value.set(value);
                    }
                }
                if (transferValue.properties.getOtherwise(VariableProperty.IDENTITY, Level.DELAY) == Level.DELAY) {
                    int identity = ReturnStatement.identity(((ReturnStatement) statement.statement).expression);
                    transferValue.properties.put(VariableProperty.IDENTITY, identity);
                }
                for (VariableProperty variableProperty : VariableProperty.INTO_RETURN_VALUE_SUMMARY) {
                    int v = variableProperties.getProperty(value, variableProperty);
                    int current = transferValue.properties.getOtherwise(variableProperty, Level.DELAY);
                    if (v > current) {
                        transferValue.properties.put(variableProperty, v);
                    }
                }
                for (VariableProperty variableProperty : VariableProperty.INTO_RETURN_VALUE_SUMMARY_DEFAULT_FALSE) {
                    int v = variableProperties.getProperty(value, variableProperty);
                    if (v == Level.DELAY) v = Level.FALSE;
                    int current = transferValue.properties.getOtherwise(variableProperty, Level.DELAY);
                    if (v > current) {
                        transferValue.properties.put(variableProperty, v);
                    }
                }
            } else {
                log(VARIABLE_PROPERTIES, "NO_VALUE for return statement in {} {} -- delaying",
                        methodInfo.fullyQualifiedName(), statement.streamIndices());
            }
        }

        // PART 6: checks for IfElse

        Runnable uponUsingConditional;

        if (statement.statement instanceof IfElseStatement || statement.statement instanceof SwitchStatement) {
            Value previousConditional = variableProperties.conditionalManager.getConditional();
            Value combinedWithConditional = variableProperties.conditionalManager.evaluateWithConditional(value);

            // we have no idea which of the 2 remains
            boolean noEffect = combinedWithConditional.equals(previousConditional);

            if (combinedWithConditional.isConstant() || noEffect) {
                if (!statement.inErrorState()) {
                    typeContext.addMessage(Message.newMessage(new Location(methodInfo, statement.streamIndices()), Message.CONDITION_EVALUATES_TO_CONSTANT));
                    statement.errorValue.set(true);
                }
            }
            uponUsingConditional = () -> {
                log(VARIABLE_PROPERTIES, "Triggering errorValue true on if-else-statement");
                if (!statement.errorValue.isSet()) statement.errorValue.set(true);
            };
        } else {
            uponUsingConditional = null;

            if (value != null && statement.statement instanceof ForEachStatement) {
                int size = variableProperties.getProperty(value, VariableProperty.SIZE);
                if (size == Analysis.SIZE_EMPTY && !statement.inErrorState()) {
                    typeContext.addMessage(Message.newMessage(new Location(methodInfo, statement.streamIndices()), Message.EMPTY_LOOP));
                    statement.errorValue.set(true);
                }
            }
        }

        // PART 7: the primary block, if it's there
        // the primary block has an expression in case of "if", "while", and "synchronized"
        // in the first two cases, we'll treat the expression as a condition

        List<NumberedStatement> startOfBlocks = statement.blocks.get();
        List<VariableProperties> evaluationContextsGathered = new ArrayList<>();
        boolean allButLastSubStatementsEscape = true;
        Value defaultCondition = NO_VALUE;
        List<Value> conditions = new ArrayList<>();
        List<BreakOrContinueStatement> breakOrContinueStatementsInChildren = new ArrayList<>();

        int start;

        if (codeOrganization.statements != Block.EMPTY_BLOCK) {
            NumberedStatement startOfFirstBlock = startOfBlocks.get(0);
            boolean statementsExecutedAtLeastOnce = codeOrganization.statementsExecutedAtLeastOnce.test(value);

            // in a synchronized block, some fields can behave like variables
            boolean inSyncBlock = statement.statement instanceof SynchronizedStatement;
            VariableProperties variablePropertiesWithValue = (VariableProperties) variableProperties.childInSyncBlock(value, uponUsingConditional,
                    inSyncBlock, statementsExecutedAtLeastOnce);

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
            CodeOrganization subStatements = codeOrganization.subStatements.get(count - start);

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
                        valueForSubStatement = new AndValue().append(negated);
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
            VariableProperties subContext = (VariableProperties) variableProperties.child(valueForSubStatement, uponUsingConditional, statementsExecutedAtLeastOnce);

            if (subStatements.localVariableCreation != null) {
                LocalVariableReference lvr = new LocalVariableReference(subStatements.localVariableCreation, List.of());
                subContext.createLocalVariableOrParameter(lvr, VariableProperty.NOT_NULL, VariableProperty.READ);
            }

            NumberedStatement subStatementStart = statement.blocks.get().get(count);
            computeVariablePropertiesOfBlock(subStatementStart, subContext);
            evaluationContextsGathered.add(subContext);
            breakOrContinueStatementsInChildren.addAll(subStatementStart.breakAndContinueStatements.isSet() ?
                    subStatementStart.breakAndContinueStatements.get() : List.of());

            // PART 11 post process

            if (count < startOfBlocks.size() - 1 && !subStatementStart.neverContinues.get()) {
                allButLastSubStatementsEscape = false;
            }
        }

        if (!evaluationContextsGathered.isEmpty()) {
            variableProperties.copyBackLocalCopies(evaluationContextsGathered, codeOrganization.noBlockMayBeExecuted);
        }

        // we don't want to set the value for break statements themselves; that happens higher up
        if (codeOrganization.haveSubBlocks() && !statement.breakAndContinueStatements.isSet()) {
            statement.breakAndContinueStatements.set(breakOrContinueStatementsInChildren);
        }

        if (allButLastSubStatementsEscape && defaultCondition != NO_VALUE) {
            variableProperties.conditionalManager.addToConditional(defaultCondition);
            statement.removeVariablesFromConditional.visit((toRemove, b) ->
                    variableProperties.conditionalManager.variableReassigned(toRemove));
            log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", defaultCondition);
        }

        return changes;
    }

    private Set<Variable> computeExistingVariablesAssignedInLoop(CodeOrganization codeOrganization, VariableProperties variableProperties) {
        return codeOrganization.findExpressionRecursivelyInStatements(Assignment.class)
                .flatMap(a -> a.assignmentTarget().stream())
                .filter(variableProperties::isKnown)
                .collect(Collectors.toSet());
    }

    // recursive evaluation of an Expression

    private static class EvaluationResult {
        final boolean changes;
        final boolean encounteredUnevaluatedVariables;
        final Value value;

        EvaluationResult(boolean changes, boolean encounteredUnevaluatedVariables, Value value) {
            this.value = value;
            this.encounteredUnevaluatedVariables = encounteredUnevaluatedVariables;
            this.changes = changes;
        }
    }

    private EvaluationResult computeVariablePropertiesOfExpression(Expression expression,
                                                                   VariableProperties variableProperties,
                                                                   NumberedStatement statement,
                                                                   ForwardEvaluationInfo forwardEvaluationInfo) {
        AtomicBoolean changes = new AtomicBoolean();
        AtomicBoolean encounterUnevaluated = new AtomicBoolean();

        Value value = expression.evaluate(variableProperties,
                (localExpression, localVariableProperties, intermediateValue, localChanges) -> {
                    // local changes come from analysing lambda blocks as methods
                    if (localChanges) changes.set(true);
                    if (intermediateValue == NO_VALUE) {
                        encounterUnevaluated.set(true);
                    }
                }, forwardEvaluationInfo);

        if (statement.statement instanceof ExpressionAsStatement && expression instanceof MethodCall) {
            checkUnusedReturnValue((MethodCall) expression, statement);
        }
        return new EvaluationResult(changes.get(), encounterUnevaluated.get(), value);
    }

    private void checkUnusedReturnValue(MethodCall methodCall, NumberedStatement statement) {
        if (statement.inErrorState()) return;
        if (methodCall.methodInfo.returnType().isVoid()) return;
        int identity = methodCall.methodInfo.methodAnalysis.get().getProperty(VariableProperty.IDENTITY);
        if (identity != Level.FALSE) return;// DELAY: we don't know, wait; true: OK not a problem

        SideEffect sideEffect = methodCall.methodInfo.sideEffect();
        switch (sideEffect) {
            case DELAYED:
            case STATIC_ONLY:
            case SIDE_EFFECT:
                return; // nothing to be done about these
            default:
                typeContext.addMessage(Message.newMessage(new Location(methodInfo, statement.streamIndices()),
                        Message.IGNORING_RESULT_OF_METHOD_CALL));
                statement.errorValue.set(true);
        }
    }


    /**
     * @param methodCalled       the method that is being called
     * @param variableProperties context
     */
    public static void checkForIllegalMethodUsageIntoNestedOrEnclosingType(MethodInfo methodCalled, EvaluationContext variableProperties) {
        if (methodCalled.isConstructor) return;
        TypeInfo currentType = variableProperties.getCurrentType();
        if (methodCalled.typeInfo == currentType) return;
        if (methodCalled.typeInfo.primaryType() != currentType.primaryType()) return; // outside
        if (methodCalled.typeInfo.isRecord() && currentType.isAnEnclosingTypeOf(methodCalled.typeInfo)) {
            return;
        }
        MethodInfo currentMethod = variableProperties.getCurrentMethod();
        if (currentMethod.methodAnalysis.get().errorCallingModifyingMethodOutsideType.isSet(methodCalled)) {
            return;
        }
        int modified = methodCalled.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        boolean allowDelays = !methodCalled.typeInfo.typeAnalysis.get().doNotAllowDelaysOnNotModified.isSet();
        if (allowDelays && modified == Level.DELAY) return; // delaying
        boolean error = modified == Level.TRUE;
        currentMethod.methodAnalysis.get().errorCallingModifyingMethodOutsideType.put(methodCalled, error);
        if (error) {
            variableProperties.raiseError(Message.METHOD_NOT_ALLOWED_TO_CALL_MODIFYING_METHOD, methodCalled.distinguishingName());
        }
    }

    /**
     * @param assignmentTarget   the field being assigned to
     * @param variableProperties context
     * @return true if the assignment is illegal
     */
    public static boolean checkForIllegalAssignmentIntoNestedOrEnclosingType(FieldReference assignmentTarget,
                                                                             EvaluationContext variableProperties) {
        MethodInfo currentMethod = variableProperties.getCurrentMethod();
        MethodAnalysis currentMethodAnalysis = currentMethod.methodAnalysis.get();
        FieldInfo fieldInfo = assignmentTarget.fieldInfo;
        if (currentMethodAnalysis.errorAssigningToFieldOutsideType.isSet(fieldInfo)) {
            return currentMethodAnalysis.errorAssigningToFieldOutsideType.get(fieldInfo);
        }
        boolean error;
        TypeInfo owner = fieldInfo.owner;
        TypeInfo currentType = variableProperties.getCurrentType();
        if (owner == currentType) {
            // so if x is a local variable of the current type, we can do this.field =, but not x.field = !
            error = !(assignmentTarget.scope instanceof This);
        } else {
            // outside current type, only records
            error = !(owner.isRecord() && currentType.isAnEnclosingTypeOf(owner));
        }
        if (error) {
            variableProperties.raiseError(Message.METHOD_NOT_ALLOWED_TO_ASSIGN_TO_FIELD, fieldInfo.name);
        }
        currentMethodAnalysis.errorAssigningToFieldOutsideType.put(fieldInfo, error);
        return error;
    }


    // the rest of the code deals with the effect of a method call on the variable properties
    // no annotations are set here, only variable properties

    public static void variableOccursInNotNullContext(Variable variable,
                                                      Value currentValue,
                                                      EvaluationContext evaluationContext,
                                                      int notNullContext) { // at least TRUE, but could be more?
        VariableProperties variableProperties = (VariableProperties) evaluationContext;
        if (currentValue == NO_VALUE) return; // not yet
        if (variable instanceof This) return; // nothing to be done here

        // the variable has already been created, if relevant
        if (!variableProperties.isKnown(variable)) {
            if (!(variable instanceof FieldReference)) throw new UnsupportedOperationException("?? should be known");
            variableProperties.ensureThisVariable((FieldReference) variable);
        }

        // if we already know that the variable is NOT @NotNull, then we'll raise an error
        int notNull = Level.value(evaluationContext.getProperty(currentValue, VariableProperty.NOT_NULL), Level.NOT_NULL);
        if (notNull == Level.FALSE) {
            evaluationContext.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION, variable.name());
        } else if (notNull == Level.DELAY) {
            // we only need to mark this in case of doubt (if we already know, we should not mark)
            variableProperties.addPropertyRestriction(variable, VariableProperty.NOT_NULL, notNullContext);
        }
    }

    public static void markSizeRestriction(EvaluationContext evaluationContext, Variable variable, int value) {
        VariableProperties variableProperties = (VariableProperties) evaluationContext;
        if (variable instanceof FieldReference) variableProperties.ensureThisVariable((FieldReference) variable);
        variableProperties.addPropertyRestriction(variable, VariableProperty.SIZE, value);
    }

    public static void markContentModified(EvaluationContext evaluationContext, Variable variable, int value) {
        VariableProperties variableProperties = (VariableProperties) evaluationContext;
        if (variable instanceof FieldReference) variableProperties.ensureThisVariable((FieldReference) variable);
        int ignoreContentModifications = variableProperties.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
        if (ignoreContentModifications != Level.TRUE) {
            log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", value, variable.detailedString());
            variableProperties.addPropertyRestriction(variable, VariableProperty.MODIFIED, value);
        } else {
            log(DEBUG_MODIFY_CONTENT, "Skip marking method object as content modified: {}", variable.detailedString());
        }
    }

    public static void markMethodCalled(EvaluationContext evaluationContext, Variable variable, int methodCalled) {
        if (methodCalled == Level.TRUE) {
            VariableProperties variableProperties = (VariableProperties) evaluationContext;
            if (variable instanceof This) {
                variableProperties.addProperty(variable, VariableProperty.METHOD_CALLED, methodCalled);
            } else if (variable.concreteReturnType().typeInfo == variableProperties.currentType) {
                This thisVariable = new This(variableProperties.currentType);
                variableProperties.addProperty(thisVariable, VariableProperty.METHOD_CALLED, methodCalled);
            }
        }
    }

    public static void markMethodDelay(EvaluationContext evaluationContext, Variable variable, int methodDelay) {
        VariableProperties variableProperties = (VariableProperties) evaluationContext;
        variableProperties.addProperty(variable, VariableProperty.METHOD_DELAY, methodDelay);
    }

}
