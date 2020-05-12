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
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.ErrorValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.value.UnknownValue.NO_VALUE;
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
        this.methodAnalysis = methodInfo.methodAnalysis;
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
                if (computeVariablePropertiesOfStatement(statement, variableProperties))
                    changes = true;

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
            if (!startStatement.neverContinues.isSet()) {
                log(VARIABLE_PROPERTIES, "Never continues at end of block of {}? {}", startStatement.streamIndices(), neverContinues);
                startStatement.neverContinues.set(neverContinues);
            }
            if (!startStatement.escapes.isSet()) {
                log(VARIABLE_PROPERTIES, "Escapes at end of block of {}? {}", startStatement.streamIndices(), escapesViaException);
                startStatement.escapes.set(escapesViaException);

                if (escapesViaException) {
                    Set<Variable> nullVariables = variableProperties.getNullConditionals(true);
                    for (Variable variable : nullVariables) {
                        log(VARIABLE_PROPERTIES, "Escape with check not null on {}", variable.detailedString());
                        if (variable instanceof ParameterInfo) {
                            ParameterInfo parameterInfo = (ParameterInfo) variable;
                            if (parameterInfo.markNotNull(Level.TRUE)) changes = true;
                        }
                        if (variableProperties.uponUsingConditional != null) {
                            log(VARIABLE_PROPERTIES, "Disabled errors on if-statement");
                            variableProperties.uponUsingConditional.run();
                        }
                    }
                }
            }
            if (unusedLocalVariablesCheck(variableProperties)) changes = true;
            if (escapesViaException && uselessAssignments(variableProperties)) changes = true;

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
            if (aboutVariable.isNotLocalCopy() && aboutVariable.getProperty(VariableProperty.CREATED) == Level.TRUE
                    && Level.value(aboutVariable.getProperty(VariableProperty.READ), 0) != Level.TRUE) {
                if (!(aboutVariable.variable instanceof LocalVariableReference)) {
                    throw new UnsupportedOperationException("?? CREATED only added to local variables");
                }
                LocalVariable localVariable = ((LocalVariableReference) aboutVariable.variable).variable;
                if (!methodAnalysis.unusedLocalVariables.isSet(localVariable)) {
                    methodAnalysis.unusedLocalVariables.put(localVariable, true);
                    typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                            ", local variable " + localVariable.name + " is not used");
                    changes = true;
                }
            }
        }
        return changes;
    }

    private boolean uselessAssignments(VariableProperties variableProperties) {
        boolean changes = false;
        // we run at the local level
        List<String> toRemove = new ArrayList<>();
        for (AboutVariable aboutVariable : variableProperties.variableProperties()) {
            if (aboutVariable.getProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT) == Level.TRUE) {
                if (!methodAnalysis.uselessAssignments.isSet(aboutVariable.variable)) {
                    methodAnalysis.uselessAssignments.put(aboutVariable.variable, true);
                    typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                            ", assignment to variable " + aboutVariable.name + " is not used");
                    changes = true;
                }
                if (aboutVariable.isLocalCopy()) toRemove.add(aboutVariable.name);
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
            newLocalVariableProperties = new VariableProperty[]{VariableProperty.CREATED, VariableProperty.ASSIGNED_IN_LOOP};
        } else {
            newLocalVariableProperties = new VariableProperty[]{VariableProperty.CREATED};
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
                EvaluationResult result = computeVariablePropertiesOfExpression(initialiser, variableProperties, statement);
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
            EvaluationResult result = computeVariablePropertiesOfExpression(updater, variableProperties, statement);
            if (result.changes) changes = true;
        }

        // PART 4: evaluation of the core expression of the statement (if the statement has such a thing)

        Value value;
        if (codeOrganization.expression == EmptyExpression.EMPTY_EXPRESSION) {
            value = null;
        } else {
            try {
                EvaluationResult result = computeVariablePropertiesOfExpression(codeOrganization.expression,
                        variableProperties, statement);
                if (result.changes) changes = true;
                value = result.encounteredUnevaluatedVariables ? NO_VALUE : result.value;
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate expression in statement {}", statement);
                throw rte;
            }
        }
        log(VARIABLE_PROPERTIES, "After eval expression: statement {}: {}", statement.streamIndices(), variableProperties);

        if (statement.statement instanceof ForEachStatement && value instanceof ArrayValue &&
                ((ArrayValue) value).values.stream().allMatch(elementValue -> elementValue.isNotNull0(variableProperties))) {
            variableProperties.addProperty(theLocalVariableReference, VariableProperty.NOT_NULL, Level.TRUE);
        }

        // PART 5: checks for ReturnStatement

        if (statement.statement instanceof ReturnStatement) {
            if (value == null) {
                if (!statement.variablesLinkedToReturnValue.isSet())
                    statement.variablesLinkedToReturnValue.set(Set.of());
                if (!statement.returnValue.isSet()) statement.returnValue.set(NO_VALUE);
            } else if (value != NO_VALUE) {
                Set<Variable> vars = value.linkedVariables(true, variableProperties);
                if (!statement.variablesLinkedToReturnValue.isSet()) {
                    statement.variablesLinkedToReturnValue.set(vars);
                }
                if (!statement.returnValue.isSet()) {
                    if (value instanceof VariableValue) {
                        statement.returnValue.set(new VariableValueCopy((VariableValue) value, variableProperties));
                    } else {
                        statement.returnValue.set(value);
                    }
                }
            } else {
                log(VARIABLE_PROPERTIES, "NO_VALUE for return statement in {} {} -- delaying",
                        methodInfo.fullyQualifiedName(), statement.streamIndices());
            }
        }

        // PART 6: checks for IfElse

        Runnable uponUsingConditional;

        Value valueAfterCheckingForConstant;
        if (statement.statement instanceof IfElseStatement || statement.statement instanceof SwitchStatement) {
            Value combinedWithConditional = variableProperties.evaluateWithConditional(value);
            if (combinedWithConditional instanceof Constant) {
                if (!statement.errorValue.isSet()) {
                    typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                            ", if/switch conditional in " + statement.streamIndices() + " evaluates to constant");
                    statement.errorValue.set(true);
                }
                valueAfterCheckingForConstant = UnknownValue.UNKNOWN_VALUE; // this should mess up most conditions
            } else {
                valueAfterCheckingForConstant = value;
            }
            uponUsingConditional = () -> {
                log(VARIABLE_PROPERTIES, "Triggering errorValue true on if-else-statement");
                statement.errorValue.set(true);
            };
        } else {
            uponUsingConditional = null;
            valueAfterCheckingForConstant = value;
        }

        // PART 7: the primary block, if it's there
        // we'll treat it as a conditional on 'value', even if there is no if() statement

        List<NumberedStatement> startOfBlocks = statement.blocks.get();
        List<VariableProperties> evaluationContextsGathered = new ArrayList<>();
        boolean allButLastSubStatementsEscape = true;
        Value defaultCondition = NO_VALUE;
        List<Value> conditions = new ArrayList<>();
        List<BreakOrContinueStatement> breakOrContinueStatementsInChildren = new ArrayList<>();

        int start;

        if (codeOrganization.statements != Block.EMPTY_BLOCK) {
            NumberedStatement startOfFirstBlock = startOfBlocks.get(0);
            boolean statementsExecutedAtLeastOnce = codeOrganization.statementsExecutedAtLeastOnce.test(valueAfterCheckingForConstant);

            boolean inSyncBlock = statement.statement instanceof SynchronizedStatement;
            VariableProperties variablePropertiesWithValue = (VariableProperties) variableProperties.childInSyncBlock(valueAfterCheckingForConstant, uponUsingConditional,
                    inSyncBlock, statementsExecutedAtLeastOnce);

            computeVariablePropertiesOfBlock(startOfFirstBlock, variablePropertiesWithValue);
            evaluationContextsGathered.add(variablePropertiesWithValue);
            breakOrContinueStatementsInChildren.addAll(startOfFirstBlock.breakAndContinueStatements.isSet() ?
                    startOfFirstBlock.breakAndContinueStatements.get() : List.of());

            allButLastSubStatementsEscape = startOfFirstBlock.neverContinues.get();
            if (valueAfterCheckingForConstant != NO_VALUE) {
                defaultCondition = NegatedValue.negate(valueAfterCheckingForConstant);
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
                        variableProperties, statement);
                valueForSubStatement = result.value;
                if (result.changes) changes = true;
                conditions.add(valueForSubStatement);
            }

            // PART 10: create subContext, add parameters of sub statements, execute

            boolean statementsExecutedAtLeastOnce = subStatements.statementsExecutedAtLeastOnce.test(value);
            VariableProperties subContext = (VariableProperties) variableProperties.child(valueForSubStatement, uponUsingConditional, statementsExecutedAtLeastOnce);

            if (subStatements.localVariableCreation != null) {
                LocalVariableReference lvr = new LocalVariableReference(subStatements.localVariableCreation, List.of());
                subContext.createLocalVariableOrParameter(lvr, VariableProperty.NOT_NULL);
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

        if(!evaluationContextsGathered.isEmpty()) {
            variableProperties.copyBackLocalCopies(evaluationContextsGathered, codeOrganization.noBlockMayBeExecuted);
        }

        // we don't want to set the value for break statements themselves; that happens higher up
        if (codeOrganization.haveSubBlocks() && !statement.breakAndContinueStatements.isSet()) {
            statement.breakAndContinueStatements.set(breakOrContinueStatementsInChildren);
        }

        if (allButLastSubStatementsEscape && defaultCondition != NO_VALUE) {
            variableProperties.addToConditional(defaultCondition);
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

    Set<MethodInfo> computeLocalMethodsCalled(List<NumberedStatement> statements) {
        Set<MethodInfo> result = new HashSet<>();
        for (NumberedStatement statement : statements) {
            CodeOrganization codeOrganization = statement.statement.codeOrganization();
            Predicate<MethodInfo> accept = mi -> mi != methodInfo && mi.typeInfo == methodInfo.typeInfo && !mi.isConstructor;
            result.addAll(codeOrganization.findExpressionRecursivelyInStatements(MethodCall.class)
                    .filter(mc -> accept.test(mc.methodInfo))
                    .map(mc -> mc.methodInfo)
                    .collect(Collectors.toSet()));
            result.addAll(codeOrganization.findExpressionRecursivelyInStatements(MethodReference.class)
                    .filter(mc -> accept.test(mc.methodInfo))
                    .map(mc -> mc.methodInfo)
                    .collect(Collectors.toSet()));
        }
        return ImmutableSet.copyOf(result);
    }

    public List<NumberedStatement> extractReturnStatements(NumberedStatement startStatement) {
        List<NumberedStatement> result = new ArrayList<>();
        NumberedStatement statement = startStatement;
        while (true) {
            if (statement.statement instanceof ReturnStatement) {
                result.add(statement);
                return result;
            }
            for (NumberedStatement block : statement.blocks.get()) {
                result.addAll(extractReturnStatements(block));
            }
            if (statement.next.get().isEmpty()) break;
            statement = statement.next.get().get();
        }
        return result;
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
                                                                   NumberedStatement statement) {
        AtomicBoolean changes = new AtomicBoolean();
        AtomicBoolean encounterUnevaluated = new AtomicBoolean();
        Value value = expression.evaluate(variableProperties,
                (localExpression, localVariableProperties, intermediateValue, localChanges) -> {
                    // local changes come from analysing lambda blocks as methods
                    if (localChanges) changes.set(true);
                    Value continueAfterError;
                    if (intermediateValue instanceof ErrorValue) {
                        if (!statement.errorValue.isSet()) {
                            typeContext.addMessage(Message.Severity.ERROR,
                                    "Error " + intermediateValue + " in method " + methodInfo.fullyQualifiedName() + " statement " + statement.streamIndices());
                            statement.errorValue.set(true);
                        }
                        continueAfterError = ((ErrorValue) intermediateValue).alternative;
                    } else {
                        continueAfterError = intermediateValue;
                    }
                    if (continueAfterError == NO_VALUE) {
                        encounterUnevaluated.set(true);
                    }
                    VariableProperties lvp = (VariableProperties) localVariableProperties;
                    doAssignmentTargetsAndInputVariables(localExpression, lvp, continueAfterError);
                    doImplicitNullCheck(statement, localExpression, lvp);
                    if (analyseCallsWithParameters(statement, localExpression, lvp)) changes.set(true);
                });
        if (statement.statement instanceof ForEachStatement) {
            // TODO: should be part of the evaluation, maybe via forward properties? there will be more @NotNull situations
            for (Variable variable : expression.variables()) {
                log(VARIABLE_PROPERTIES, "Set variable {} to permanently not null: in forEach statement", variable.detailedString());
                variableOccursInNotNullContext(statement, variable, variableProperties);
            }
            if (expression instanceof MethodCall) {
                MethodInfo methodCalled = ((MethodCall) expression).methodInfo;
                if (methodCalled != variableProperties.getCurrentMethod()) { // not a recursive call
                    int notNull = methodCalled.methodAnalysis.getProperty(VariableProperty.NOT_NULL);
                    if (notNull == Level.DELAY) encounterUnevaluated.set(true);
                    else if (notNull == Level.FALSE) {
                        if (!statement.errorValue.isSet()) {
                            typeContext.addMessage(Message.Severity.ERROR, "Result of method call can be null in a @NotNull situation: " +
                                    methodCalled.distinguishingName() + " in " +
                                    statement.streamIndices() + ", " +
                                    variableProperties.getCurrentMethod().distinguishingName());
                            statement.errorValue.set(true);
                        }
                    }
                }
            }
        }
        return new EvaluationResult(changes.get(), encounterUnevaluated.get(), value);
    }

    /**
     * We need to directly set "READ" for fields outside the construction phase
     *
     * @param expression         the current expression evaluated. Note that sub-expressions may have been analysed before
     * @param variableProperties the evaluation context
     * @param value              the result of the evaluation
     */
    private void doAssignmentTargetsAndInputVariables(Expression expression, VariableProperties variableProperties, Value value) {
        if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;
            Variable at = assignment.target.assignmentTarget().orElseThrow();
            log(VARIABLE_PROPERTIES, "Assign value of {} to {}", at.detailedString(), value);

            if (at instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) at).fieldInfo;

                // only change fields of "our" class, otherwise, raise error
                if (fieldInfo.owner.primaryType() != methodInfo.typeInfo.primaryType()) {
                    if (!fieldInfo.fieldAnalysis.errorsForAssignmentsOutsidePrimaryType.isSet(methodInfo)) {
                        typeContext.addMessage(Message.Severity.ERROR, "Assigning to field outside the primary type: " + at.detailedString());
                        fieldInfo.fieldAnalysis.errorsForAssignmentsOutsidePrimaryType.put(methodInfo, true);
                    }
                    return;
                }

                // even inside our class, there are limitations; potentially raise error
                if (checkForIllegalAssignmentIntoNestedOrEnclosingType((FieldReference) at, variableProperties)) {
                    return;
                }
            }
            variableProperties.assignmentBasics(at, value);

            if (value != NO_VALUE) {
                Set<Variable> linkToBestCase = value.linkedVariables(true, variableProperties);
                Set<Variable> linkToWorstCase = value.linkedVariables(false, variableProperties);
                log(LINKED_VARIABLES, "In assignment, link {} to [{}] best case, [{}] worst case", at.detailedString(),
                        Variable.detailedString(linkToBestCase), Variable.detailedString(linkToWorstCase));
                variableProperties.linkVariables(at, linkToBestCase, linkToWorstCase);
            }

        } else {
            for (Variable variable : expression.variables()) {
                if (variable instanceof This) {
                    if (!methodAnalysis.thisRead.isSet()) {
                        log(ASSIGNMENT, "Mark that '{}' has been read in {}", variable.detailedString(), methodInfo.distinguishingName());
                        methodAnalysis.thisRead.set(true);
                    }
                } else {
                    variableProperties.markRead(variable);
                }
            }
        }
        if (expression instanceof MethodCall)
            checkForIllegalMethodUsageIntoNestedOrEnclosingType(((MethodCall) expression).methodInfo,
                    variableProperties);
        else if (expression instanceof MethodReference)
            checkForIllegalMethodUsageIntoNestedOrEnclosingType(((MethodReference) expression).methodInfo,
                    variableProperties);
    }

    /**
     * @param methodCalled       the method that is being called
     * @param variableProperties context
     */
    private void checkForIllegalMethodUsageIntoNestedOrEnclosingType(MethodInfo methodCalled, VariableProperties variableProperties) {
        if (methodCalled.isConstructor) return;
        TypeInfo currentType = variableProperties.getCurrentType();
        if (methodCalled.typeInfo == currentType) return;
        if (methodCalled.typeInfo.primaryType() != currentType.primaryType()) return; // outside
        if (methodCalled.typeInfo.isRecord() && currentType.isAnEnclosingTypeOf(methodCalled.typeInfo)) {
            return;
        }
        MethodInfo currentMethod = variableProperties.getCurrentMethod();
        if (currentMethod.methodAnalysis.errorCallingModifyingMethodOutsideType.isSet(methodCalled)) {
            return;
        }
        int notModified = methodCalled.methodAnalysis.getProperty(VariableProperty.NOT_MODIFIED);
        boolean allowDelays = !methodCalled.typeInfo.typeAnalysis.doNotAllowDelaysOnNotModified.isSet();
        if (allowDelays && notModified == Level.DELAY) return; // delaying
        boolean error = notModified != Level.TRUE;
        currentMethod.methodAnalysis.errorCallingModifyingMethodOutsideType.put(methodCalled, error);
        if (error) {
            typeContext.addMessage(Message.Severity.ERROR, "Method " + currentMethod.distinguishingName() +
                    " is not allowed to call non-@NotModified method " + methodCalled.distinguishingName());
        }
    }

    /**
     * @param assignmentTarget   the field being assigned to
     * @param variableProperties context
     * @return true if the assignment is illegal
     */
    private boolean checkForIllegalAssignmentIntoNestedOrEnclosingType(FieldReference assignmentTarget,
                                                                       VariableProperties variableProperties) {
        MethodInfo currentMethod = variableProperties.getCurrentMethod();
        FieldInfo fieldInfo = assignmentTarget.fieldInfo;
        if (currentMethod.methodAnalysis.errorAssigningToFieldOutsideType.isSet(fieldInfo)) {
            return currentMethod.methodAnalysis.errorAssigningToFieldOutsideType.get(fieldInfo);
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
            typeContext.addMessage(Message.Severity.ERROR, "Method " + currentMethod.distinguishingName() +
                    " is not allowed to assign to field " + fieldInfo.fullyQualifiedName());
        }
        currentMethod.methodAnalysis.errorAssigningToFieldOutsideType.put(fieldInfo, error);
        return error;
    }

    private void doImplicitNullCheck(NumberedStatement currentStatement,
                                     Expression expression,
                                     VariableProperties variableProperties) {
        for (Variable variable : expression.variablesInScopeSide()) {
            variableOccursInNotNullContext(currentStatement, variable, variableProperties);
        }
    }

    // the rest of the code deals with the effect of a method call on the variable properties
    // no annotations are set here, only variable properties

    private boolean analyseCallsWithParameters(NumberedStatement currentStatement, Expression expression, VariableProperties variableProperties) {
        if (expression instanceof HasParameterExpressions) {
            HasParameterExpressions call = (HasParameterExpressions) expression;
            // we also need to exclude recursive calls from this analysis
            if (call.getMethodInfo() != null && call.getMethodInfo() != variableProperties.getCurrentMethod()) {
                return analyseCallWithParameters(currentStatement, call, variableProperties);
            }
        }
        return false;
    }

    private boolean analyseCallWithParameters(NumberedStatement currentStatement,
                                              HasParameterExpressions call,
                                              VariableProperties variableProperties) {
        boolean changes = false;
        if (call instanceof MethodCall) analyseMethodCallObject((MethodCall) call, variableProperties);
        int parameterIndex = 0;
        List<ParameterInfo> params = call.getMethodInfo().methodInspection.get().parameters;
        if (call.getParameterExpressions().size() > 0 && params.size() == 0) {
            throw new UnsupportedOperationException("Method " + call.getMethodInfo().fullyQualifiedName() +
                    " has no parameters, but I have " + call.getParameterExpressions().size());
        }
        for (Expression e : call.getParameterExpressions()) {
            ParameterInfo parameterInDefinition;
            if (parameterIndex >= params.size()) {
                ParameterInfo lastParameter = params.get(params.size() - 1);
                if (lastParameter.parameterInspection.get().varArgs) {
                    parameterInDefinition = lastParameter;
                } else {
                    throw new UnsupportedOperationException("?");
                }
            } else {
                parameterInDefinition = params.get(parameterIndex);
            }
            if (analyseCallParameter(currentStatement, parameterInDefinition, e, variableProperties)) changes = true;
            parameterIndex++;
        }
        return changes;
    }

    private boolean analyseCallParameter(NumberedStatement currentStatement,
                                         ParameterInfo parameterInDefinition,
                                         Expression parameterExpression,
                                         VariableProperties variableProperties) {
        // not modified
        int notModified = parameterInDefinition.parameterAnalysis.getProperty(VariableProperty.NOT_MODIFIED);
        int value = Level.compose(Level.TRUE, notModified == Level.FALSE ? 1 : 0);
        recursivelyMarkContentModified(parameterExpression, variableProperties, value);

        // null not allowed; not a recursive call so no knowledge about the parameter as a variable
        if (parameterExpression instanceof VariableExpression) {
            Variable v = ((VariableExpression) parameterExpression).variable;
            int notNull = parameterInDefinition.parameterAnalysis.getProperty(VariableProperty.NOT_NULL);
            if (Level.value(notNull, Level.NOT_NULL) == Level.TRUE) {
                return variableOccursInNotNullContext(currentStatement, v, variableProperties);
            }
        }
        return false;
    }

    private boolean variableOccursInNotNullContext(NumberedStatement currentStatement, Variable variable, VariableProperties variableProperties) {
        if (variable instanceof This) return false; // nothing to be done here
        // the variable has already been created, if relevant
        if (!variableProperties.isKnown(variable)) return false;

        int notNull = variableProperties.getProperty(variable, VariableProperty.NOT_NULL);
        if (Level.value(notNull, Level.NOT_NULL) == Level.TRUE) return false; // OK!

        // if the variable has been assigned to another variable, we want to jump there!
        // this chain potentially ends in fields or parameters
        // if it is a field, it should also be known
        Variable valueVar = variableProperties.switchToValueVariable(variable);
        if (valueVar instanceof ParameterInfo) {
            ParameterInfo parameterInfo = (ParameterInfo) valueVar;
            return parameterInfo.markNotNull(Level.TRUE);
        }
        if (!currentStatement.errorValue.isSet()) {
            typeContext.addMessage(Message.Severity.ERROR, "Potential null-pointer exception involving "
                    + variable.detailedString() + " in statement "
                    + currentStatement.streamIndices() + " of " + methodInfo.name);
            currentStatement.errorValue.set(true);
            return true;
        }
        return false;
    }

    private void analyseMethodCallObject(MethodCall methodCall, VariableProperties variableProperties) {
        // not modified
        SideEffect sideEffect = methodCall.methodInfo.sideEffect();
        boolean safeMethod = sideEffect.lessThan(SideEffect.SIDE_EFFECT);
        int value = Level.compose(Level.TRUE, safeMethod ? 0 : 1);
        recursivelyMarkContentModified(methodCall.object, variableProperties, value);
    }

    private void recursivelyMarkContentModified(Expression expression, VariableProperties variableProperties, int value) {
        Variable variable;
        if (expression instanceof VariableExpression) {
            variable = expression.variables().get(0);
        } else if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;
            recursivelyMarkContentModified(fieldAccess.expression, variableProperties, value);
            variable = fieldAccess.variable;
        } else {
            return;
        }
        if (variable instanceof FieldReference) variableProperties.ensureVariable((FieldReference) variable);
        int ignoreContentModifications = variableProperties.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
        if (ignoreContentModifications != Level.TRUE) {
            log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", value, variable.detailedString());
            variableProperties.addProperty(variable, VariableProperty.CONTENT_MODIFIED, value);
        } else {
            log(DEBUG_MODIFY_CONTENT, "Skip marking method object as content modified {}: {}", value, variable.detailedString());
        }
    }
}
