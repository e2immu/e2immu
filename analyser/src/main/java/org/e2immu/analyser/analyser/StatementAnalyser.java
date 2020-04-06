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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.StringUtil;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class StatementAnalyser {
    private final TypeContext typeContext;
    private final MethodAnalysis methodAnalysis;
    private final MethodInfo methodInfo;

    public StatementAnalyser(TypeContext typeContext, MethodInfo methodInfo) {
        this.typeContext = typeContext;
        this.methodAnalysis = methodInfo.methodAnalysis;
        this.methodInfo = methodInfo;
    }

    boolean computeVariablePropertiesOfBlock(NumberedStatement startStatement, VariableProperties variableProperties) {
        boolean changes = false;
        NumberedStatement statement = startStatement;
        boolean neverContinues = false;
        boolean escapes = false;
        while (statement != null) {
            if (computeVariablePropertiesOfStatement(statement, variableProperties)) changes = true;

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
    }

    private boolean computeVariablePropertiesOfStatement(NumberedStatement statement,
                                                         VariableProperties variableProperties) {
        boolean changes = false;

        List<LocalVariableCreation> localVariablesCreated = statement.statement.findInExpression(LocalVariableCreation.class);
        localVariablesCreated.stream()
                .map(lvc -> new LocalVariableReference(lvc.localVariable, List.of()))
                .forEach(lvr -> variableProperties.create(lvr, VariableProperty.CREATED));
        if (statement.statement instanceof ForEachStatement) {
            ForEachStatement forEachStatement = (ForEachStatement) statement.statement;
            variableProperties.create(new LocalVariableReference(forEachStatement.localVariable, List.of()));
        }
        statement.inputVariables
                .forEach(variable -> {
                    if (!variableProperties.addProperty(variable, VariableProperty.READ)) {
                        variableProperties.addProperty(variable, VariableProperty.READ_MULTIPLE_TIMES);
                    }
                });
        statement.assignmentTargets
                .stream()
                .filter(v -> !(v instanceof FieldReference) || ((FieldReference) v).scope instanceof This)
                .forEach(at -> {
                    if (variableProperties.removeProperty(at, VariableProperty.CHECK_NOT_NULL)) {
                        log(ANALYSER, "Cleared check-null property of {}", at.detailedString());
                    }
                    if (!variableProperties.addProperty(at, VariableProperty.MODIFIED)) {
                        variableProperties.addProperty(at, VariableProperty.MODIFIED_MULTIPLE_TIMES);
                    }
                    Value value = (statement.statement instanceof StatementWithExpression) ?
                            ((StatementWithExpression) statement.statement).expression.evaluate(variableProperties) : null;
                    if (value != null) {
                        variableProperties.setValue(at, value);
                        Set<Variable> linkTo = value.linkedVariables(variableProperties);
                        log(LINKED_VARIABLES, "In assignment, link {} to [{}]", at.detailedString(),
                                Variable.detailedString(linkTo));
                        variableProperties.linkVariables(at, linkTo);
                    }
                    log(ANALYSER, "Set value of {} to {}", at.detailedString(), value);
                });

        for (LocalVariableCreation localVariableCreation : localVariablesCreated) {
            Value value = localVariableCreation.expression.evaluate(variableProperties);
            Set<Variable> linkTo = value.linkedVariables(variableProperties);
            log(LINKED_VARIABLES, "In creation with assignment, link {} to [{}]", localVariableCreation.localVariable.name,
                    Variable.detailedString(linkTo));
            LocalVariableReference lvr = new LocalVariableReference(localVariableCreation.localVariable, List.of());
            variableProperties.linkVariables(lvr, linkTo);
        }

        // TODO there may be duplicate evaluation

        Value value = (statement.statement instanceof StatementWithExpression) ?
                ((StatementWithExpression) statement.statement).expression.evaluate(variableProperties) : null;

        if (value != null) {
            Set<Variable> vars = value.linkedVariables(variableProperties);
            if (!statement.linkedVariables.isSet() && statement.statement instanceof ReturnStatement) {
                statement.linkedVariables.set(vars);
            }
        }

        if (statement.statement instanceof IfElseStatement) {
            if (value == BoolValue.FALSE || value == BoolValue.TRUE) {
                log(ANALYSER, "condition in if statement is constant {}", value);
                typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() + ", if statement evaluates to constant");
            }
            VariableProperties copyForThen = new VariableProperties(variableProperties, value);

            log(VARIABLE_PROPERTIES, "IfThenElse statement {}: {}", statement.streamIndices(), variableProperties);
            if (computeVariablePropertiesOfExpression(((IfElseStatement) statement.statement).expression, variableProperties, value)) {
                changes = true;
            }
            NumberedStatement startOfThenBlock = statement.blocks.get().get(0);
            computeVariablePropertiesOfBlock(startOfThenBlock, copyForThen);
            if (statement.blocks.get().size() > 1) {
                VariableProperties copyForElse = new VariableProperties(variableProperties, NegatedValue.negate(value));
                computeVariablePropertiesOfBlock(statement.blocks.get().get(1), copyForElse);
            }
            // if the "then" block ends in return or throw (we recursively need to know)
            // then we need to copy the check of the "else" continue (regardless if we
            // have an else or not, or if that else returns or not.
            if (startOfThenBlock.neverContinues.get()) {
                variableProperties.addToConditional(value);
                log(VARIABLE_PROPERTIES, "Then-part of If-Then-Else never continues, added Else-part to variable properties, now {}",
                        variableProperties);
            }
        } else {
            log(VARIABLE_PROPERTIES, "Statement {}: {}", statement.streamIndices(), variableProperties);
            if (statement.statement instanceof StatementWithExpression) {
                Expression expression = ((StatementWithExpression) statement.statement).expression;

                if (computeVariablePropertiesOfExpression(expression, variableProperties, value)) changes = true;

                if (statement.statement instanceof ReturnStatement) {
                    Boolean notNull = value.isNotNull(variableProperties);
                    if (notNull != null && !statement.returnsNotNull.isSet()) {
                        statement.returnsNotNull.set(notNull);
                        changes = true;
                    }
                }
            }

            for (NumberedStatement block : statement.blocks.get()) {
                computeVariablePropertiesOfBlock(block, new VariableProperties(variableProperties));
            }
        }
        return changes;
    }

    private boolean computeVariablePropertiesOfExpression(Expression expression, VariableProperties variableProperties, Value value) {
        boolean changes = false;
        List<InlineConditionalOperator> ternaries = expression.find(InlineConditionalOperator.class);
        for (InlineConditionalOperator operator : ternaries) {
            Value conditional = operator.conditional.evaluate(variableProperties);
            if (value == BoolValue.FALSE || value == BoolValue.TRUE) {
                typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() + ", ternary expression evaluates to constant");
            }
            log(VARIABLE_PROPERTIES, "Entering ternary, condition {}", operator.conditional.expressionString(0));
            VariableProperties copyForThen = new VariableProperties(variableProperties, conditional);
            VariableProperties copyForElse = new VariableProperties(variableProperties, NegatedValue.negate(conditional));

            computeVariablePropertiesOfExpression(operator.ifTrue, copyForThen, operator.ifTrue.evaluate(copyForThen));
            computeVariablePropertiesOfExpression(operator.ifFalse, copyForElse, operator.ifFalse.evaluate(copyForElse));
            log(VARIABLE_PROPERTIES, "Exited ternary, condition {}", operator.conditional.expressionString(0));
        }
        List<LambdaExpression> lambdaExpressions = expression.find(LambdaExpression.class);
        for (LambdaExpression lambdaExpression : lambdaExpressions) {
            VariableProperties withLambdaParameters = new VariableProperties(variableProperties);
            lambdaExpression.parameters.forEach(pi -> withLambdaParameters.create(pi));
            computeVariablePropertiesOfExpression(lambdaExpression.expression, withLambdaParameters, lambdaExpression.evaluate(withLambdaParameters));
        }
        List<LambdaBlock> lambdaBlocks = expression.find(LambdaBlock.class);
        for (LambdaBlock lambdaBlock : lambdaBlocks) {
            if (handleLambdaBlock(lambdaBlock, variableProperties)) changes = true;
        }

        if (doImplicitNullCheck(expression, variableProperties)) changes = true;
        if (analyseCallsWithParameters(expression, variableProperties)) changes = true;

        return changes;
    }

    // moved the code in a separate method, so it's easier to spot this type of recursion in stack traces
    private boolean handleLambdaBlock(LambdaBlock lambdaBlock, VariableProperties variableProperties) {
        boolean changes = false;
        VariableProperties withLambdaParameters = new VariableProperties(variableProperties);
        lambdaBlock.parameters.forEach(pi -> withLambdaParameters.create(pi));

        if (!lambdaBlock.numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            MethodAnalyser.recursivelyCreateNumberedStatements(lambdaBlock.block.statements, indices, numberedStatements, new SideEffectContext(typeContext, methodInfo));
            lambdaBlock.numberedStatements.set(numberedStatements);
            changes = true;
        }
        StatementAnalyser statementAnalyser = new StatementAnalyser(typeContext, methodInfo);
        if (!lambdaBlock.numberedStatements.get().isEmpty() &&
                statementAnalyser.computeVariablePropertiesOfBlock(lambdaBlock.numberedStatements.get().get(0), withLambdaParameters))
            changes = true;
        return changes;
    }

    private boolean doImplicitNullCheck(Expression expression, VariableProperties variableProperties) {
        boolean changes = false;
        for (Variable variable : expression.variablesInScopeSide(true)) {
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
        for (HasParameterExpressions call : expression.find(HasParameterExpressions.class)) {
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
            Variable variable = expression.variableFromExpression();
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
