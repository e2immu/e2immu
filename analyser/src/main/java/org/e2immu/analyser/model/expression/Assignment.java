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

package org.e2immu.analyser.model.expression;

import com.github.javaparser.ast.expr.AssignExpr;
import com.google.common.collect.Sets;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

public class Assignment implements Expression {

    public final Expression target;
    public final Expression value;
    public final MethodInfo primitiveOperator;

    // if null, and primitive operator not null, then the primitive operator counts (i += value)
    // if true, we have ++i
    // if false, we have i++ if primitive operator is +=, i-- if primitive is -=
    public final Boolean prefixPrimitiveOperator;

    public Assignment(@NotNull Expression target, @NotNull Expression value) {
        this(target, value, null, null);
    }

    public Assignment(@NotNull Expression target, @NotNull Expression value,
                      MethodInfo primitiveOperator,
                      Boolean prefixPrimitiveOperator) {
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
        this.primitiveOperator = primitiveOperator; // as in i+=1;
        this.prefixPrimitiveOperator = prefixPrimitiveOperator;
    }

    @NotNull
    public static MethodInfo operator(@NotNull AssignExpr.Operator operator,
                                      @NotNull TypeInfo widestType) {
        switch (operator) {
            case PLUS:
                return Primitives.PRIMITIVES.assignPlusOperatorInt;
            case MINUS:
                return Primitives.PRIMITIVES.assignMinusOperatorInt;
            case MULTIPLY:
                return Primitives.PRIMITIVES.assignMultiplyOperatorInt;
            case DIVIDE:
                return Primitives.PRIMITIVES.assignDivideOperatorInt;
            case BINARY_OR:
                return Primitives.PRIMITIVES.assignOrOperatorBoolean;
            case ASSIGN:
                return Primitives.PRIMITIVES.assignOperatorInt;
        }
        throw new UnsupportedOperationException("Need to add primitive operator " +
                operator + " on type " + widestType.fullyQualifiedName);
    }

    @Override
    public ParameterizedType returnType() {
        return target.returnType();
    }

    @Override
    public String expressionString(int indent) {
        if (prefixPrimitiveOperator != null) {
            String operator = primitiveOperator == Primitives.PRIMITIVES.assignPlusOperatorInt ? "++" : "--";
            if (prefixPrimitiveOperator) {
                StringBuilder sb = new StringBuilder();
                StringUtil.indent(sb, indent);
                sb.append(operator);
                sb.append(target.expressionString(0));
                return sb.toString();
            }
            return target.expressionString(indent) + operator;
        }
        //  != null && primitiveOperator != Primitives.PRIMITIVES.assignOperatorInt ? "=" + primitiveOperator.name : "=";
        String operator = primitiveOperator == null ? "=" : primitiveOperator.name;
        return target.expressionString(indent) + " " + operator + " " + value.expressionString(indent);
    }

    @Override
    public int precedence() {
        return 1; // lowest precedence
    }

    @Override
    public Set<String> imports() {
        return Sets.union(target.imports(), value.imports());
    }

    @Override
    public List<Expression> subExpressions() {
        return List.of(target, value);
    }

    @Override
    public Optional<Variable> assignmentTarget() {
        return target.assignmentTarget();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        if (target instanceof FieldAccess) {
            return SideEffect.SIDE_EFFECT;
        }
        return SideEffect.LOCAL;
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return target.variablesInScopeSide();
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Variable at;
        if (target instanceof ArrayAccess) {
            ArrayAccess arrayAccess = (ArrayAccess) target;
            Value array = arrayAccess.expression.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);
            Value indexValue = arrayAccess.index.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);
            String name = ArrayAccess.dependentVariableName(array, indexValue);
            Variable arrayVariable = arrayAccess.expression instanceof VariableExpression ? ((VariableExpression) arrayAccess.expression).variable : null;
            at = evaluationContext.ensureArrayVariable(arrayAccess, name, arrayVariable);
            log(VARIABLE_PROPERTIES, "Assignment to array: {} = {}", at.detailedString(), value);
        } else {
            target.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.ASSIGNMENT_TARGET);
            at = target.assignmentTarget().orElseThrow();
            log(VARIABLE_PROPERTIES, "Assignment: {} = {}", at.detailedString(), value);
        }

        // we pass on forwardEvaluation (could be that we require not null)
        Value resultOfExpression = value.evaluate(evaluationContext, visitor, forwardEvaluationInfo);
        doAssignmentWork(evaluationContext, at, resultOfExpression);
        visitor.visit(this, evaluationContext, resultOfExpression);
        // we let the assignment code decide what to do; we'll read the value of the variable afterwards
        Variable assignmentTarget = target.assignmentTarget().orElseThrow();
        return evaluationContext.currentValue(assignmentTarget);
    }

    private void doAssignmentWork(EvaluationContext evaluationContext, Variable at, Value resultOfExpression) {
        MethodInfo methodInfo = evaluationContext.getCurrentMethod();
        Messages messages = evaluationContext.getMessages();
        NumberedStatement statement = evaluationContext.getCurrentStatement();

        // see if we need to raise an error (writing to fields outside our class, etc.)
        if (at instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) at).fieldInfo;
            FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
            // only change fields of "our" class, otherwise, raise error
            if (fieldInfo.owner.primaryType() != methodInfo.typeInfo.primaryType()) {
                if (!fieldAnalysis.errorsForAssignmentsOutsidePrimaryType.isSet(methodInfo)) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE));
                    fieldAnalysis.errorsForAssignmentsOutsidePrimaryType.put(methodInfo, true);
                }
                return;
            }

            // even inside our class, there are limitations; potentially raise error
            if (StatementAnalyser.checkForIllegalAssignmentIntoNestedOrEnclosingType((FieldReference) at, evaluationContext)) {
                return;
            }

            if (resultOfExpression.getObjectFlow() != ObjectFlow.NO_FLOW) {
                resultOfExpression.getObjectFlow().assignTo(fieldInfo);
            }
        } else if (at instanceof ParameterInfo) {
            if (!statement.inErrorState()) {
                messages.add(Message.newMessage(new Location((ParameterInfo) at), Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO));
                statement.errorValue.set(true);
                return;
            }
        }
        evaluationContext.assignmentBasics(at, resultOfExpression, this.value != EmptyExpression.EMPTY_EXPRESSION);

        // connect the value to the assignment target
        if (resultOfExpression != NO_VALUE) {
            Set<Variable> linkToBestCase = resultOfExpression.linkedVariables(true, evaluationContext);
            Set<Variable> linkToWorstCase = resultOfExpression.linkedVariables(false, evaluationContext);
            log(LINKED_VARIABLES, "In assignment, link {} to [{}] best case, [{}] worst case", at.detailedString(),
                    Variable.detailedString(linkToBestCase), Variable.detailedString(linkToWorstCase));
            evaluationContext.linkVariables(at, linkToBestCase, linkToWorstCase);
        }
    }
}
