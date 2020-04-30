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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        String operator = primitiveOperator != null && primitiveOperator != Primitives.PRIMITIVES.assignOperatorInt ? "=" + primitiveOperator.name : "=";
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
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        if (!(target instanceof VariableExpression)) {
            // we evaluate if there is a more complex expression, like a.b.c (FieldAccess) or so
            // otherwise, we want to avoid a visit on the variable
            target.evaluate(evaluationContext, visitor);
        }
        Value resultOfExpression = value.evaluate(evaluationContext, visitor);
        Value result;

        if (resultOfExpression instanceof MethodValue) {
            Boolean isNotNull = resultOfExpression.isNotNull(evaluationContext);
            if (isNotNull == null) {
                result = UnknownValue.NO_VALUE; // delay
            } else {
                Optional<Variable> assignmentTarget = target.assignmentTarget();
                if (assignmentTarget.isEmpty()) {
                    throw new UnsupportedOperationException("Have " + target.getClass());// ?
                }
                // here we set the valueForLinkAnalysis to be the method value rather than the variable value
                // which we will return
                result = new VariableValue(assignmentTarget.get(), Set.of(), false, resultOfExpression, isNotNull);
            }
        } else {
            result = resultOfExpression;
        }

        visitor.visit(this, evaluationContext, result);
        return result;
    }
}
