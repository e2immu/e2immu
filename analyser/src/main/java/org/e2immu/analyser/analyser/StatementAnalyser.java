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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.OrValue;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.ErrorValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

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
        boolean escapes = false;
        try {
            while (statement != null) {
                if (computeVariablePropertiesOfStatement(statement, variableProperties))
                    changes = true;

                if (statement.statement instanceof ReturnStatement ||
                        statement.statement instanceof ThrowStatement) neverContinues = true;
                if (statement.statement instanceof ThrowStatement) {
                    escapes = true;
                }
                if (statement.neverContinues.isSet() && statement.neverContinues.get()) neverContinues = true;
                if (statement.escapes.isSet() && statement.escapes.get()) escapes = true;
                statement = statement.next.get().orElse(null);
            }
            if (!startStatement.neverContinues.isSet()) {
                log(VARIABLE_PROPERTIES, "Never continues at end of block of {}? {}", startStatement.streamIndices(), neverContinues);
                startStatement.neverContinues.set(neverContinues);
                changes = true;
            }
            if (!startStatement.escapes.isSet()) {
                log(VARIABLE_PROPERTIES, "Escapes at end of block of {}? {}", startStatement.streamIndices(), escapes);
                startStatement.escapes.set(escapes);
                changes = true;

                if (escapes) {
                    List<Value> conditionals = variableProperties.getNullConditionals();
                    for (Value value : conditionals) {
                        Optional<Variable> isNull = value.variableIsNull();
                        if (isNull.isPresent()) {
                            Variable variable = isNull.get();
                            log(VARIABLE_PROPERTIES, "Escape with check not null on {}", variable.detailedString());
                            if (variable instanceof ParameterInfo) {
                                ParameterInfo parameterInfo = (ParameterInfo) variable;
                                if (!parameterInfo.parameterAnalysis.annotations.isSet(typeContext.nullNotAllowed.get())) {
                                    parameterInfo.parameterAnalysis.annotations.put(typeContext.nullNotAllowed.get(), true);
                                    log(NULL_NOT_ALLOWED, "Mark parameter {} as @NullNotAllowed", variable.detailedString());
                                    changes = true;
                                }
                            } else if (variable instanceof FieldReference) {
                                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                                if (!fieldInfo.fieldAnalysis.annotations.isSet(typeContext.nullNotAllowed.get())) {
                                    fieldInfo.fieldAnalysis.annotations.put(typeContext.nullNotAllowed.get(), true);
                                    log(NULL_NOT_ALLOWED, "Mark field {} as @NullNotAllowed", fieldInfo.fullyQualifiedName());
                                    changes = true;
                                }
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : variableProperties.variableProperties.entrySet()) {
                Variable variable = entry.getKey();
                Set<VariableProperty> properties = entry.getValue().properties;
                if (properties.contains(VariableProperty.CREATED) &&
                        !properties.contains(VariableProperty.READ)) {
                    if (!(variable instanceof LocalVariableReference)) throw new UnsupportedOperationException("??");
                    LocalVariable localVariable = ((LocalVariableReference) variable).variable;
                    if (!methodAnalysis.unusedLocalVariables.isSet(localVariable)) {
                        methodAnalysis.unusedLocalVariables.put(localVariable, true);
                        log(ANALYSER, "Mark local variable {} as unused", localVariable.name);
                        changes = true;
                    }
                }
            }
            if (isLogEnabled(LINKED_VARIABLES) && !variableProperties.dependencyGraph.isEmpty()) {
                log(LINKED_VARIABLES, "Dependency graph of linked variables:");
                variableProperties.dependencyGraph.visit((n, list) -> log(LINKED_VARIABLES, " -- {} --> {}", n.detailedString(),
                        list == null ? "[]" : StringUtil.join(list, Variable::detailedString)));
            }

            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in statement analyser: {}", statement);
            throw rte;
        }
    }

    private boolean computeVariablePropertiesOfStatement(NumberedStatement statement,
                                                         VariableProperties variableProperties) {
        boolean changes = false;
        CodeOrganization codeOrganization = statement.statement.codeOrganization();

        // PART 1: filling of of the variable properties: parameters of statement "forEach" (duplicated further in PART 11

        if (codeOrganization.localVariableCreation != null) {
            variableProperties.create(new LocalVariableReference(codeOrganization.localVariableCreation,
                    List.of()), VariableProperty.CREATED);
        }

        // PART 2: more filling up of the variable properties: local variables in try-resources, for-loop, expression as statement

        List<LocalVariableCreation> localVariableCreations = codeOrganization.initialisers.stream()
                .map(expr -> (LocalVariableCreation) expr)
                .collect(Collectors.toList());
        for (LocalVariableCreation localVariableCreation : localVariableCreations) {
            LocalVariableReference lvr = new LocalVariableReference(localVariableCreation.localVariable, List.of());
            variableProperties.create(lvr, VariableProperty.CREATED);
            if (localVariableCreation.expression != EmptyExpression.EMPTY_EXPRESSION) {
                Pair<Value, Boolean> pair = computeVariablePropertiesOfExpression(localVariableCreation.expression,
                        variableProperties, statement);
                if (pair.v) changes = true;
                variableProperties.setValue(lvr, pair.k);
            }
        }

        // PART 5: computing linking between local variables and fields, parameters

        for (LocalVariableCreation localVariableCreation : localVariableCreations) {
            Pair<Value, Boolean> pair = computeVariablePropertiesOfExpression(localVariableCreation.expression,
                    variableProperties, statement);
            Value value = pair.k;
            if (pair.v) changes = true;
            Set<Variable> linkTo = value.linkedVariables(variableProperties);
            log(LINKED_VARIABLES, "In creation with assignment, link {} to [{}]", localVariableCreation.localVariable.name,
                    Variable.detailedString(linkTo));
            LocalVariableReference lvr = new LocalVariableReference(localVariableCreation.localVariable, List.of());
            variableProperties.linkVariables(lvr, linkTo);
        }

        // PART 6: evaluation of the core expression of the statement (if the statement has such a thing)

        Value value;
        if (codeOrganization.expression == EmptyExpression.EMPTY_EXPRESSION) {
            value = null;
        } else {
            try {
                Pair<Value, Boolean> pair = computeVariablePropertiesOfExpression(codeOrganization.expression,
                        variableProperties, statement);
                if (pair.v) changes = true;
                value = pair.k;
            } catch (RuntimeException rte) {
                log(ANALYSER, "Failed to evaluate expression in statement {}", statement);
                throw rte;
            }
        }
        log(VARIABLE_PROPERTIES, "After eval expression: statement {}: {}", statement.streamIndices(), variableProperties);

        // PART 7: checks for ReturnStatement

        if (statement.statement instanceof ReturnStatement) {
            if (value != null) {
                Set<Variable> vars = value.linkedVariables(variableProperties);
                if (!statement.linkedVariables.isSet()) {
                    statement.linkedVariables.set(vars);
                }
                Boolean notNull = value.isNotNull(variableProperties);
                if (notNull != null && !statement.returnsNotNull.isSet()) {
                    statement.returnsNotNull.set(notNull);
                    changes = true;
                }
            }
            if (!statement.returnValue.isSet()) {
                statement.returnValue.set(value == null ? UnknownValue.NO_VALUE : value);
            }
        }

        // PART 8: checks for IfElse

        if (statement.statement instanceof IfElseStatement && (value == BoolValue.FALSE || value == BoolValue.TRUE)) {
            if(!statement.errorValue.isSet()) {
                log(ANALYSER, "condition in if statement is constant {}", value);
                typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                        ", if statement evaluates to constant");
                statement.errorValue.set(true);
            }
        }

        // PART 9: the primary block, if it's there
        // we'll treat it as a conditional on 'value', even if there is no if() statement

        List<NumberedStatement> startOfBlocks = statement.blocks.get();
        if (!startOfBlocks.isEmpty()) {

            NumberedStatement startOfFirstBlock = startOfBlocks.get(0);
            VariableProperties variablePropertiesWithValue = variableProperties.copyWithConditional(value);
            computeVariablePropertiesOfBlock(startOfFirstBlock, variablePropertiesWithValue);

            // PART 10: other conditions, including the else, switch entries, catch clauses
            List<Value> conditions = new ArrayList<>();
            for (int count = 1; count < startOfBlocks.size(); count++) {
                CodeOrganization subStatements = codeOrganization.subStatements.get(count - 1);

                // PART 11: add parameters of sub statements

                if (subStatements.localVariableCreation != null) {
                    variableProperties.create(new LocalVariableReference(subStatements.localVariableCreation, List.of()));
                }

                // PART 12: evaluate the sub-expression

                Value valueForSubStatement;
                if (EmptyExpression.DEFAULT_EXPRESSION == subStatements.expression) {
                    Value or = value;
                    for (Value condition : conditions) {
                        or = new OrValue(or, condition);
                    }
                    valueForSubStatement = NegatedValue.negate(or);
                } else if (EmptyExpression.FINALLY_EXPRESSION == subStatements.expression ||
                        EmptyExpression.EMPTY_EXPRESSION == subStatements.expression) {
                    valueForSubStatement = null;
                } else {
                    // real expression
                    Pair<Value, Boolean> pair = computeVariablePropertiesOfExpression(subStatements.expression,
                            variableProperties, statement);
                    valueForSubStatement = pair.k;
                    if (pair.v) changes = true;
                    conditions.add(valueForSubStatement);
                }
                VariableProperties copyForElse = valueForSubStatement == null ?
                        variableProperties :
                        variableProperties.copyWithConditional(valueForSubStatement);
                computeVariablePropertiesOfBlock(statement.blocks.get().get(count), copyForElse);

                // if the "then" block ends in return or throw (we recursively need to know)
                // then we need to copy the check of the "else" continue (regardless if we
                // have an else or not, or if that else returns or not.
                if (startOfFirstBlock.neverContinues.get() && startOfBlocks.size() == 2) {
                    variableProperties.addToConditional(valueForSubStatement);
                    log(VARIABLE_PROPERTIES, "Then-part of If-Then-Else never continues, " +
                            "added Else-part to variable properties, now {}", variableProperties);
                }

            }
        }

        // finally there are the updaters
        for (Expression updater : codeOrganization.updaters) {
            Pair<Value, Boolean> pair = computeVariablePropertiesOfExpression(updater, variableProperties, statement);
            if (pair.v) changes = true;
        }

        return changes;
    }

    // recursive evaluation of an Expression

    private Pair<Value, Boolean> computeVariablePropertiesOfExpression(Expression expression,
                                                                       VariableProperties variableProperties,
                                                                       NumberedStatement statementForErrorReporting) {
        AtomicBoolean changes = new AtomicBoolean();
        Value value = expression.evaluate(variableProperties,
                (localExpression, localVariableProperties, intermediateValue, localChanges) -> {
                    if (localChanges) changes.set(true);
                    if (intermediateValue instanceof ErrorValue) {
                        log(ANALYSER, "Encountered error in expression {} in {}",
                                localExpression.getClass(), methodInfo.fullyQualifiedName());
                        if (!statementForErrorReporting.errorValue.isSet()) {
                            typeContext.addMessage(Message.Severity.ERROR,
                                    "Error " + intermediateValue + " in method " + methodInfo.fullyQualifiedName());
                            statementForErrorReporting.errorValue.set(true);
                        }
                    } else if (intermediateValue == UnknownValue.DELAYED_VALUE) {
                        log(ANALYSER, "Delaying analysis of expression {} in {}",
                                localExpression.getClass(),
                                methodInfo.fullyQualifiedName());
                    }
                    VariableProperties lvp = (VariableProperties) localVariableProperties;
                    doAssignmentTargetsAndInputVariables(localExpression, lvp, intermediateValue);
                    if (doImplicitNullCheck(localExpression, lvp)) changes.set(true);
                    if (analyseCallsWithParameters(localExpression, lvp)) changes.set(true);
                });
        return new Pair<>(value, changes.get());
    }

    private static void doAssignmentTargetsAndInputVariables(Expression expression, VariableProperties variableProperties, Value value) {
        if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;
            Variable at = assignment.target.assignmentTarget().orElseThrow();
            // PART 4: more filling up of the variable properties, this time for assignments
            if (!(at instanceof FieldReference) || ((FieldReference) at).scope instanceof This) {
                if (variableProperties.removeProperty(at, VariableProperty.CHECK_NOT_NULL)) {
                    log(ANALYSER, "Cleared check-null property of {}", at.detailedString());
                }
                if (!variableProperties.addProperty(at, VariableProperty.MODIFIED)) {
                    variableProperties.addProperty(at, VariableProperty.MODIFIED_MULTIPLE_TIMES);
                }
                if (value != null) {
                    variableProperties.setValue(at, value);
                    Set<Variable> linkTo = value.linkedVariables(variableProperties);
                    log(LINKED_VARIABLES, "In assignment, link {} to [{}]", at.detailedString(),
                            Variable.detailedString(linkTo));
                    variableProperties.linkVariables(at, linkTo);
                }
                log(ANALYSER, "Set value of {} to {}", at.detailedString(), value);
            }
        }
        for (Variable variable : expression.variables()) {
            if (!variableProperties.addProperty(variable, VariableProperty.READ)) {
                variableProperties.addProperty(variable, VariableProperty.READ_MULTIPLE_TIMES);
            }
        }
    }

    private boolean doImplicitNullCheck(Expression expression, VariableProperties variableProperties) {
        boolean changes = false;
        for (Variable variable : expression.variablesInScopeSide()) {
            if (!(variable instanceof This)) {
                if (variableProperties.isNotNull(variable)) {
                    log(VARIABLE_PROPERTIES, "Null has been excluded for {}", variable.detailedString());
                } else {
                    if (variable instanceof ParameterInfo) {
                        ParameterInfo parameterInfo = (ParameterInfo) variable;
                        if (!parameterInfo.parameterAnalysis.annotations.isSet(typeContext.nullNotAllowed.get())) {
                            parameterInfo.parameterAnalysis.annotations.put(typeContext.nullNotAllowed.get(), true);
                            log(NULL_NOT_ALLOWED, "Variable {} can be null, we add @NullNotAllowed", variable.detailedString());
                            changes = true;
                        }
                    }
                    variableProperties.addProperty(variable, VariableProperty.CHECK_NOT_NULL);
                }
            }
        }
        return changes;
    }


    private boolean analyseCallsWithParameters(Expression expression, VariableProperties variableProperties) {
        boolean changes = false;
        if (expression instanceof HasParameterExpressions) {
            HasParameterExpressions call = (HasParameterExpressions) expression;
            if (call.getMethodInfo() != null && // otherwise, nothing to show for; anonymous constructor
                    analyseCallWithParameters(call, variableProperties)) changes = true;
        }
        return changes;
    }

    private boolean analyseCallWithParameters(HasParameterExpressions call, VariableProperties variableProperties) {
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
            if (analyseCallParameter(parameterInDefinition, e, variableProperties)) changes = true;
            parameterIndex++;
        }
        return changes;
    }

    private boolean analyseCallParameter(ParameterInfo parameterInDefinition,
                                         Expression parameterExpression,
                                         VariableProperties variableProperties) {
        boolean changes = false;

        // not modified
        boolean safeParameter = parameterInDefinition.isNotModified(typeContext) == Boolean.TRUE;
        if (!safeParameter) {
            recursivelyMarkVariables(parameterExpression, variableProperties);
        }

        // null not allowed
        if (parameterExpression instanceof VariableExpression) {
            Variable v = ((VariableExpression) parameterExpression).variable;
            if (parameterInDefinition.isNullNotAllowed(typeContext) == Boolean.TRUE) {
                if (v instanceof ParameterInfo && !((ParameterInfo) v).parameterAnalysis.annotations.isSet(typeContext.nullNotAllowed.get())) {
                    log(NULL_NOT_ALLOWED, "Adding implicit null not allowed on {}", v.detailedString());
                    ((ParameterInfo) v).parameterAnalysis.annotations.put(typeContext.nullNotAllowed.get(), true);
                    changes = true;
                }
                variableProperties.addProperty(v, VariableProperty.CHECK_NOT_NULL);
            }
        }
        return changes;
    }

    private void analyseMethodCallObject(MethodCall methodCall, VariableProperties variableProperties) {
        // not modified

        boolean safeMethod = methodCall.methodInfo.sideEffect(typeContext).lessThan(SideEffect.SIDE_EFFECT);
        //boolean haveParameters = !methodCall.methodInfo().methodInspection.get().parameters.isEmpty();
        if (!safeMethod) {
            recursivelyMarkVariables(methodCall.object, variableProperties);
        }
    }

    private void recursivelyMarkVariables(Expression expression, VariableProperties variableProperties) {
        if (expression instanceof VariableExpression) {
            Variable variable = expression.variables().get(0);
            log(MODIFY_CONTENT, "SA: mark method object as content modified: {}", variable.detailedString());
            variableProperties.addProperty(variable, VariableProperty.CONTENT_MODIFIED);
        } else if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;
            recursivelyMarkVariables(fieldAccess.expression, variableProperties);
            log(MODIFY_CONTENT, "SA: mark method object, field access as content modified: {}",
                    fieldAccess.variable.detailedString());
            variableProperties.addProperty(fieldAccess.variable, VariableProperty.CONTENT_MODIFIED);
        }
    }

}
