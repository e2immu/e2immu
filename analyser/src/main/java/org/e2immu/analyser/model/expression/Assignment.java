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
import org.e2immu.analyser.analyser.ErrorFlags;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
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
    public final MethodInfo assignmentOperator;
    public final MethodInfo binaryOperator;

    // if null, and binary operator not null, then the primitive operator counts (i += value)
    // if true, we have ++i
    // if false, we have i++ if primitive operator is +=, i-- if primitive is -=
    public final Boolean prefixPrimitiveOperator;

    public Assignment(@NotNull Expression target, @NotNull Expression value) {
        this(target, value, null, null);
    }

    public Assignment(@NotNull Expression target, @NotNull Expression value,
                      MethodInfo assignmentOperator,
                      Boolean prefixPrimitiveOperator) {
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
        this.assignmentOperator = assignmentOperator; // as in i+=1;
        this.prefixPrimitiveOperator = prefixPrimitiveOperator;
        binaryOperator = assignmentOperator == null ? null : BinaryOperator.fromAssignmentOperatorToNormalOperator(assignmentOperator);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Assignment(translationMap.translateExpression(target),
                translationMap.translateExpression(value), assignmentOperator, prefixPrimitiveOperator);
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
            String operator = assignmentOperator == Primitives.PRIMITIVES.assignPlusOperatorInt ? "++" : "--";
            if (prefixPrimitiveOperator) {
                return operator + target.expressionString(indent);
            }
            return target.expressionString(indent) + operator;
        }
        //  != null && primitiveOperator != Primitives.PRIMITIVES.assignOperatorInt ? "=" + primitiveOperator.name : "=";
        String operator = assignmentOperator == null ? "=" : assignmentOperator.name;
        return target.expressionString(indent) + " " + operator + " " + value.expressionString(indent);
    }

    @Override
    public int precedence() {
        return 1; // lowest precedence
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(target, value);
    }

    @Override
    public Optional<Variable> assignmentTarget() {
        return target.assignmentTarget();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        if (target instanceof FieldAccess) {
            return SideEffect.SIDE_EFFECT;
        }
        return SideEffect.LOCAL;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        Variable at;
        if (target instanceof ArrayAccess) {
            ArrayAccess arrayAccess = (ArrayAccess) target;
            EvaluationResult array = arrayAccess.expression.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL_MODIFIED);
            EvaluationResult indexValue = arrayAccess.index.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
            builder.compose(array, indexValue);

            String name = ArrayAccess.dependentVariableName(array.value, indexValue.value);
            Variable arrayVariable = arrayAccess.expression instanceof VariableExpression ? ((VariableExpression) arrayAccess.expression).variable : null;
            at = builder.ensureArrayVariable(arrayAccess, name, arrayVariable);
            log(VARIABLE_PROPERTIES, "Assignment to array: {} = {}", at.detailedString(), value);
        } else {
            EvaluationResult targetResult = target.evaluate(evaluationContext, ForwardEvaluationInfo.ASSIGNMENT_TARGET);
            builder.compose(targetResult);

            at = target.assignmentTarget().orElseThrow();
            log(VARIABLE_PROPERTIES, "Assignment: {} = {}", at.detailedString(), value);
        }

        EvaluationResult valueResult = value.evaluate(evaluationContext, forwardEvaluationInfo);
        builder.compose(valueResult);

        Value resultOfExpression;
        Value assignedToTarget;
        if (binaryOperator != null) {
            BinaryOperator operation = new BinaryOperator(new VariableExpression(at), binaryOperator, value,
                    BinaryOperator.precedence(binaryOperator));
            EvaluationResult operationResult = operation.evaluate(evaluationContext, forwardEvaluationInfo);
            builder.compose(operationResult);

            if (prefixPrimitiveOperator == null || prefixPrimitiveOperator) {
                // ++i, i += 1
                resultOfExpression = operationResult.value;
            } else {
                // i++
                resultOfExpression = valueResult.value;
            }
            assignedToTarget = operationResult.value;
        } else {
            resultOfExpression = valueResult.value;
            assignedToTarget = valueResult.value;
        }
        doAssignmentWork(builder, evaluationContext, at, assignedToTarget);

        // we let the assignment code decide what to do; we'll read the value of the variable afterwards
        // TODO this does not work well with i++
        Variable assignmentTarget = target.assignmentTarget().orElseThrow();
        Value currentValue = builder.currentValue(assignmentTarget);

        return builder.setValueAndResultOfExpression(currentValue, resultOfExpression).build();
    }

    private void doAssignmentWork(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Variable at, Value resultOfExpression) {

        // see if we need to raise an error (writing to fields outside our class, etc.)
        if (at instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) at).fieldInfo;

            // check illegal assignment into nested type
            if (checkIllegalAssignmentIntoNestedType(at, fieldInfo, evaluationContext.getCurrentType().typeInfo)) {
                builder.add(evaluationContext.getCurrentStatement()
                        .new ErrorAssigningToFieldOutsideType(fieldInfo, evaluationContext.getLocation()));
            }

            if (resultOfExpression.getObjectFlow() != ObjectFlow.NO_FLOW) {
                resultOfExpression.getObjectFlow().assignTo(fieldInfo);
            }
        } else if (at instanceof ParameterInfo) {
            builder.add(evaluationContext.getCurrentStatement()
                    .new ParameterShouldNotBeAssignedTo((ParameterInfo) at, evaluationContext.getLocation()));
        }
        builder.assignmentBasics(at, resultOfExpression, this.value != EmptyExpression.EMPTY_EXPRESSION);

        // connect the value to the assignment target
        if (resultOfExpression != NO_VALUE) {
            Set<Variable> linked = resultOfExpression.linkedVariables(evaluationContext);
            log(LINKED_VARIABLES, "In assignment, link {} to [{}]", at.detailedString(),
                    Variable.detailedString(linked), Variable.detailedString(linked));
            builder.linkVariables(at, linked);
        }
    }

    private static boolean checkIllegalAssignmentIntoNestedType(Variable at, FieldInfo fieldInfo, TypeInfo currentType) {
        TypeInfo owner = fieldInfo.owner;
        if (owner.primaryType() != currentType.primaryType()) return true; // outside primary type
        if (owner == currentType) { // in the same type
            // so if x is a local variable of the current type, we can do this.field =, but not x.field = !
            return !(((FieldReference) at).scope instanceof This);
        }
        // outside current type, but inside primary type, only records
        return !(owner.isRecord() && currentType.isAnEnclosingTypeOf(owner));
    }
}
