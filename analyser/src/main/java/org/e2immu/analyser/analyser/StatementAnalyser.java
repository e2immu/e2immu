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
                    List<Variable> nullVariables = variableProperties.getNullConditionals();
                    for (Variable variable : nullVariables) {
                        log(VARIABLE_PROPERTIES, "Escape with check not null on {}", variable.detailedString());
                        variableProperties.addProperty(variable, VariableProperty.PERMANENTLY_NOT_NULL);
                        if (variableProperties.uponUsingConditional != null) {
                            log(VARIABLE_PROPERTIES, "Disabled errors on if-statement");
                            variableProperties.uponUsingConditional.run();
                        }
                    }
                }
            }
            if (unusedLocalVariablesCheck(variableProperties)) changes = true;
            if (uselessAssignments(variableProperties, escapesViaException)) changes = true;

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
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : variableProperties.variableProperties.entrySet()) {
            Variable variable = entry.getKey();
            VariableProperties.AboutVariable aboutVariable = entry.getValue();
            Set<VariableProperty> properties = aboutVariable.properties;
            if (aboutVariable.isNotLocalCopy() && properties.contains(VariableProperty.CREATED) && !properties.contains(VariableProperty.READ)) {
                if (!(variable instanceof LocalVariableReference)) throw new UnsupportedOperationException("??");
                LocalVariable localVariable = ((LocalVariableReference) variable).variable;
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

    private boolean uselessAssignments(VariableProperties variableProperties, boolean escapesViaException) {
        boolean changes = false;
        // we run at the local level
        List<Variable> toRemove = new ArrayList<>();
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : variableProperties.variableProperties.entrySet()) {
            Variable variable = entry.getKey();
            if (variable instanceof LocalVariableReference) {
                VariableProperties.AboutVariable aboutVariable = entry.getValue();
                Set<VariableProperty> properties = aboutVariable.properties;
                LocalVariable localVariable = ((LocalVariableReference) variable).variable;

                if ((properties.contains(VariableProperty.CREATED) && aboutVariable.isNotLocalCopy() || escapesViaException) &&
                        properties.contains(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT)) {
                    if (!methodAnalysis.unusedLocalVariables.isSet(localVariable)) {
                        methodAnalysis.unusedLocalVariables.put(localVariable, true);
                        typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                                ", assignment to local variable " + localVariable.name + " is not used");
                        changes = true;
                    }
                    toRemove.add(variable);
                }
            }// ignoring This etc.
        }
        if (!toRemove.isEmpty()) {
            log(VARIABLE_PROPERTIES, "Removing local info for variables {}", toRemove);
            variableProperties.variableProperties.keySet().removeAll(toRemove);
        }
        return changes;
    }


    private boolean computeVariablePropertiesOfStatement(NumberedStatement statement,
                                                         VariableProperties variableProperties) {
        boolean changes = false;
        CodeOrganization codeOrganization = statement.statement.codeOrganization();

        // PART 1: filling of of the variable properties: parameters of statement "forEach" (duplicated further in PART 10
        // but then for variables in catch clauses)

        LocalVariableReference theLocalVariableReference;
        if (codeOrganization.localVariableCreation != null) {
            theLocalVariableReference = new LocalVariableReference(codeOrganization.localVariableCreation,
                    List.of());
            variableProperties.create(theLocalVariableReference, new VariableValue(theLocalVariableReference), VariableProperty.CREATED);
        } else {
            theLocalVariableReference = null;
        }

        // PART 2: more filling up of the variable properties: local variables in try-resources, for-loop, expression as statement

        for (Expression initialiser : codeOrganization.initialisers) {
            if (initialiser instanceof LocalVariableCreation) {
                LocalVariableReference lvr = new LocalVariableReference(((LocalVariableCreation) initialiser).localVariable, List.of());
                variableProperties.create(lvr, NO_VALUE, VariableProperty.CREATED);
            }
            try {
                EvaluationResult result = computeVariablePropertiesOfExpression(initialiser, variableProperties, statement);
                if (result.changes) changes = true;
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {}", statement);
                throw rte;
            }
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
                ((ArrayValue) value).values.stream().allMatch(element -> element.isNotNull(variableProperties))) {
            variableProperties.addProperty(theLocalVariableReference, VariableProperty.CHECK_NOT_NULL);
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
                Boolean notNull = value.isNotNull(variableProperties);
                if (notNull != null && !statement.returnsNotNull.isSet()) {
                    statement.returnsNotNull.set(notNull);
                    log(NOT_NULL, "Setting returnsNotNull on {} to {} based on {}", methodInfo.fullyQualifiedName(), notNull, value);
                }
                if (!statement.returnValue.isSet()) {
                    statement.returnValue.set(value);
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
        boolean allButLastSubStatementsEscape = true;
        Value defaultCondition = NO_VALUE;
        List<Value> conditions = new ArrayList<>();
        List<BreakOrContinueStatement> breakOrContinueStatementsInChildren = new ArrayList<>();

        int start;

        if (codeOrganization.statements != Block.EMPTY_BLOCK) {
            NumberedStatement startOfFirstBlock = startOfBlocks.get(0);
            boolean statementsExecutedAtLeastOnce = codeOrganization.statementsExecutedAtLeastOnce.test(valueAfterCheckingForConstant);

            VariableProperties variablePropertiesWithValue = (VariableProperties) variableProperties.child(valueAfterCheckingForConstant, uponUsingConditional, statementsExecutedAtLeastOnce);
            computeVariablePropertiesOfBlock(startOfFirstBlock, variablePropertiesWithValue);
            variablePropertiesWithValue.copyBackLocalCopies(statementsExecutedAtLeastOnce);
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
                subContext.create(lvr, new VariableValue(lvr), VariableProperty.CHECK_NOT_NULL);
            }

            NumberedStatement subStatementStart = statement.blocks.get().get(count);
            computeVariablePropertiesOfBlock(subStatementStart, subContext);
            subContext.copyBackLocalCopies(statementsExecutedAtLeastOnce);
            breakOrContinueStatementsInChildren.addAll(subStatementStart.breakAndContinueStatements.isSet() ?
                    subStatementStart.breakAndContinueStatements.get() : List.of());

            // PART 11 post process

            if (count < startOfBlocks.size() - 1 && !subStatementStart.neverContinues.get()) {
                allButLastSubStatementsEscape = false;
            }
        }

        // we don't want to set the value for break statements themselves; that happens higher up
        if (codeOrganization.haveSubBlocks() && !statement.breakAndContinueStatements.isSet()) {
            statement.breakAndContinueStatements.set(breakOrContinueStatementsInChildren);
        }

        if (allButLastSubStatementsEscape && defaultCondition != NO_VALUE) {
            variableProperties.addToConditional(defaultCondition);
            log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", defaultCondition);
        }

        // PART 12: finally there are the updaters
        // used in for-statement, and the parameters of an explicit constructor invocation this(...)
        for (Expression updater : codeOrganization.updaters) {
            EvaluationResult result = computeVariablePropertiesOfExpression(updater, variableProperties, statement);
            if (result.changes) changes = true;
        }

        return changes;
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
                    if (continueAfterError instanceof VariableValue && ((VariableValue) continueAfterError).effectivelyFinalUnevaluated) {
                        encounterUnevaluated.set(true);
                    }
                    VariableProperties lvp = (VariableProperties) localVariableProperties;
                    if (doAssignmentTargetsAndInputVariables(localExpression, lvp, continueAfterError))
                        changes.set(true);
                    doImplicitNullCheck(localExpression, lvp);
                    analyseCallsWithParameters(localExpression, lvp);
                });
        return new EvaluationResult(changes.get(), encounterUnevaluated.get(), value);
    }

    private boolean doAssignmentTargetsAndInputVariables(Expression expression, VariableProperties variableProperties, Value value) {
        boolean changes = false;
        if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;
            Variable at = assignment.target.assignmentTarget().orElseThrow();
            // PART 4: more filling up of the variable properties, this time for assignments

            // only change fields of "our" class
            if (!(at instanceof FieldReference) ||
                    ((FieldReference) at).fieldInfo.owner.primaryType() == variableProperties.currentMethod.typeInfo.primaryType()) {
                log(VARIABLE_PROPERTIES, "Assign value of {} to {}", at.detailedString(), value);

                if (at instanceof FieldReference) {
                    if (checkForIllegalAssignmentIntoNestedOrEnclosingType(((FieldReference) at).fieldInfo, variableProperties.currentMethod)) {
                        changes = true;
                    }
                }

                // assignment to local variable: could be that we're in the block where it was created, then nothing happens
                // but when we're down in some descendant block, a local AboutVariable block is created (we MAY have to undo...)
                if (at instanceof LocalVariableReference) {
                    variableProperties.ensureLocalCopy(at);
                }
                variableProperties.setValue(at, value);

                // the following block is there in case the value, instead of the expected complicated one, turns
                // out to be a constant.
                Boolean isNotNull = value.isNotNull(variableProperties);
                if (isNotNull == Boolean.TRUE) {
                    variableProperties.addProperty(at, VariableProperty.CHECK_NOT_NULL);
                    log(VARIABLE_PROPERTIES, "Added check-null property of {}", at.detailedString());
                } else if (variableProperties.removeProperty(at, VariableProperty.CHECK_NOT_NULL)) {
                    log(VARIABLE_PROPERTIES, "Cleared check-null property of {}", at.detailedString());
                }
                variableProperties.addProperty(at, VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT);
                if (!variableProperties.addProperty(at, VariableProperty.ASSIGNED)) {
                    variableProperties.addProperty(at, VariableProperty.ASSIGNED_MULTIPLE_TIMES);
                }
                if (variableProperties.guaranteedToBeReached(at)) {
                    variableProperties.addProperty(at, VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED);
                } else {
                    variableProperties.removeProperty(at, VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED);
                }
                Value valueForLinkAnalysis = value.valueForLinkAnalysis();
                if (valueForLinkAnalysis != NO_VALUE) {
                    Set<Variable> linkToBestCase = valueForLinkAnalysis.linkedVariables(true, variableProperties);
                    Set<Variable> linkToWorstCase = valueForLinkAnalysis.linkedVariables(false, variableProperties);
                    log(LINKED_VARIABLES, "In assignment, link {} to [{}] best case, [{}] worst case", at.detailedString(),
                            Variable.detailedString(linkToBestCase), Variable.detailedString(linkToWorstCase));
                    variableProperties.linkVariables(at, linkToBestCase, linkToWorstCase);
                }
            } else {
                LOGGER.warn("Ignoring assignment to field in outside primary type: {}, while in {}",
                        at.detailedString(), variableProperties.currentMethod.distinguishingName());
            }
        } else {
            for (Variable variable : expression.variables()) {
                variableProperties.removeProperty(variable, VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT);
                if (!variableProperties.addProperty(variable, VariableProperty.READ)) {
                    variableProperties.addProperty(variable, VariableProperty.READ_MULTIPLE_TIMES);
                }
            }
        }
        if (expression instanceof MethodCall &&
                checkForIllegalMethodUsageIntoNestedOrEnclosingType(((MethodCall) expression).methodInfo,
                        variableProperties.currentMethod)) changes = true;
        else if (expression instanceof MethodReference &&
                checkForIllegalMethodUsageIntoNestedOrEnclosingType(((MethodReference) expression).methodInfo,
                        variableProperties.currentMethod)) changes = true;
        return changes;
    }

    private boolean checkForIllegalMethodUsageIntoNestedOrEnclosingType(MethodInfo methodCalled, MethodInfo currentMethod) {
        if (methodCalled.typeInfo == currentMethod.typeInfo) return false;
        if (methodCalled.typeInfo.primaryType() != currentMethod.typeInfo.primaryType()) return false; // outside
        if (methodCalled.typeInfo.isNestedType() && methodCalled.typeInfo.isPrivate() &&
                currentMethod.typeInfo.isAnEnclosingTypeOf(methodCalled.typeInfo)) {
            return false;
        }
        if (currentMethod.methodAnalysis.errorCallingModifyingMethodOutsideType.isSet(methodCalled)) return false;
        Boolean isNotModified = methodCalled.isNotModified(typeContext);
        boolean allowDelays = !methodCalled.typeInfo.typeAnalysis.doNotAllowDelaysOnNotModified.isSet();
        if (allowDelays && isNotModified == null) return false; // delaying
        boolean error = isNotModified == null || isNotModified == Boolean.FALSE;
        currentMethod.methodAnalysis.errorCallingModifyingMethodOutsideType.put(methodCalled, error);
        if (error) {
            typeContext.addMessage(Message.Severity.ERROR, "Method " + currentMethod.distinguishingName() +
                    " is not allowed to call non-@NotModified method " + methodCalled.distinguishingName());
        }
        return true;
    }

    private boolean checkForIllegalAssignmentIntoNestedOrEnclosingType(FieldInfo assignmentTarget, MethodInfo currentMethod) {
        if (assignmentTarget.owner == currentMethod.typeInfo) return false;
        if (assignmentTarget.owner.isNestedType() && assignmentTarget.owner.isPrivate() &&
                currentMethod.typeInfo.isAnEnclosingTypeOf(assignmentTarget.owner)) {
            return false;
        }
        if (currentMethod.methodAnalysis.errorAssigningToFieldOutsideType.isSet(assignmentTarget)) return false;
        typeContext.addMessage(Message.Severity.ERROR, "Method " + currentMethod.distinguishingName() +
                " is not allowed to access field " + assignmentTarget.fullyQualifiedName());
        currentMethod.methodAnalysis.errorAssigningToFieldOutsideType.put(assignmentTarget, true);
        return true;
    }

    private void doImplicitNullCheck(Expression expression, VariableProperties variableProperties) {
        for (Variable variable : expression.variablesInScopeSide()) {
            if (!(variable instanceof This)) {
                if (variableProperties.isNotNull(variable)) {
                    log(VARIABLE_PROPERTIES, "Null has already been excluded for {}", variable.detailedString());
                } else {
                    variableProperties.addProperty(variable, VariableProperty.PERMANENTLY_NOT_NULL);
                    log(VARIABLE_PROPERTIES, "Set {} to PERMANENTLY NOT NULL", variable.detailedString());
                }
            }
        }
    }

    // the rest of the code deals with the effect of a method call on the variable properties
    // no annotations are set here, only variable properties

    private void analyseCallsWithParameters(Expression expression, VariableProperties variableProperties) {
        if (expression instanceof HasParameterExpressions) {
            HasParameterExpressions call = (HasParameterExpressions) expression;
            if (call.getMethodInfo() != null) {
                analyseCallWithParameters(call, variableProperties);
            }
        }
    }

    private void analyseCallWithParameters(HasParameterExpressions call, VariableProperties variableProperties) {
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
            analyseCallParameter(parameterInDefinition, e, variableProperties);
            parameterIndex++;
        }
    }

    private void analyseCallParameter(ParameterInfo parameterInDefinition,
                                      Expression parameterExpression,
                                      VariableProperties variableProperties) {
        // not modified
        Boolean safeParameter = parameterInDefinition.isNotModified(typeContext);
        if (safeParameter == Boolean.FALSE) {
            recursivelyMarkVariables(parameterExpression, variableProperties, VariableProperty.CONTENT_MODIFIED);
        } else if (safeParameter == null) {
            recursivelyMarkVariables(parameterExpression, variableProperties, VariableProperty.CONTENT_MODIFIED_DELAYED);
        }
        // null not allowed
        if (parameterExpression instanceof VariableExpression) {
            Variable v = ((VariableExpression) parameterExpression).variable;
            if (parameterInDefinition.isNullNotAllowed(typeContext) == Boolean.TRUE) {
                variableProperties.addProperty(v, VariableProperty.PERMANENTLY_NOT_NULL);
            }
        }
    }

    private void analyseMethodCallObject(MethodCall methodCall, VariableProperties variableProperties) {
        // not modified
        SideEffect sideEffect = methodCall.methodInfo.sideEffect(typeContext);
        if (sideEffect == SideEffect.DELAYED) {
            recursivelyMarkVariables(methodCall.object, variableProperties, VariableProperty.CONTENT_MODIFIED_DELAYED);
            return;
        }
        boolean safeMethod = sideEffect.lessThan(SideEffect.SIDE_EFFECT);
        if (!safeMethod) {
            recursivelyMarkVariables(methodCall.object, variableProperties, VariableProperty.CONTENT_MODIFIED);
        }
    }

    private void recursivelyMarkVariables(Expression expression, VariableProperties variableProperties, VariableProperty propertyToSet) {
        if (expression instanceof VariableExpression) {
            Variable variable = expression.variables().get(0);
            log(DEBUG_MODIFY_CONTENT, "SA: mark method object as content modified: {}", variable.detailedString());
            variableProperties.addProperty(variable, propertyToSet);
        } else if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;
            recursivelyMarkVariables(fieldAccess.expression, variableProperties, propertyToSet);
            log(DEBUG_MODIFY_CONTENT, "SA: mark method object, field access as content modified: {}",
                    fieldAccess.variable.detailedString());
            variableProperties.addProperty(fieldAccess.variable, propertyToSet);
        }
    }

}
