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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Negation extends UnaryOperator implements ExpressionWrapper {
    public final ObjectFlow objectFlow;

    public Expression getExpression() {
        return expression;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_NEGATED;
    }

    private Negation(MethodInfo operator, Expression value) {
        super(operator, value, value.isNumeric() ? Precedence.PLUSPLUS : Precedence.UNARY);
        this.objectFlow = value.getObjectFlow();
        if (value.isInstanceOf(Negation.class)) throw new UnsupportedOperationException();
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reValue = expression.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reValue);
        return builder.setExpression(Negation.negate(evaluationContext, reValue.value)).build();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return null;
    }

    public static Expression negate(EvaluationContext evaluationContext, @NotNull Expression v) {
        Objects.requireNonNull(v);
        if (v instanceof BooleanConstant boolValue) {
            return boolValue.negate();
        }
        if (v instanceof Negatable negatable) {
            return negatable.negate();
        }
        if (v.isUnknown()) return v;

        if (v instanceof Negation negation) return negation.expression;
        if (v instanceof Or or) {
            Expression[] negated = or.expressions().stream()
                    .map(ov -> Negation.negate(evaluationContext, ov))
                    .toArray(Expression[]::new);
            return new And(evaluationContext.getPrimitives(), v.getObjectFlow())
                    .append(evaluationContext, negated);
        }
        if (v instanceof And and) {
            List<Expression> negated = and.expressions().stream()
                    .map(av -> Negation.negate(evaluationContext, av)).collect(Collectors.toList());
            return new Or(evaluationContext.getPrimitives(), v.getObjectFlow())
                    .append(evaluationContext, negated);
        }
        if (v instanceof Sum sum) {
            return sum.negate(evaluationContext);
        }
        if (v instanceof GreaterThanZero greaterThanZero) {
            return greaterThanZero.negate(evaluationContext);
        }
        MethodInfo operator = v.isNumeric() ?
                evaluationContext.getPrimitives().unaryMinusOperatorInt :
                evaluationContext.getPrimitives().logicalNotOperatorBool;
        return new Negation(operator, v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Negation that = (Negation) o;
        return expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(expression.isNumeric() ? Symbol.UNARY_MINUS : Symbol.UNARY_BOOLEAN_NOT)
                .add(outputInParenthesis(precedence(), expression));
    }

    @Override
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public int internalCompareTo(Expression v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Variable> variables() {
        return expression.variables();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return evaluationContext.getProperty(expression, variableProperty);
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }
}
