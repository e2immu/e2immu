/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record PropertyWrapper(Expression expression,
                              Map<VariableProperty, Integer> properties,
                              ParameterizedType castType) implements Expression, ExpressionWrapper {

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new PropertyWrapper(expression.translate(translationMap), properties,
                castType == null ? null : translationMap.translateType(castType));
    }

    @Override
    public Expression getExpression() {
        return expression;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_PROPERTY;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reValue = expression.reEvaluate(evaluationContext, translation);
        return reEvaluated(evaluationContext, reValue);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult reValue = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        return reEvaluated(evaluationContext, reValue);
    }

    private EvaluationResult reEvaluated(EvaluationContext evaluationContext, EvaluationResult reValue) {
        Expression newValue = reValue.value();
        Map<VariableProperty, Integer> reduced = reduce(evaluationContext, newValue, properties);
        Expression result = reduced.isEmpty() ? newValue : PropertyWrapper.propertyWrapper(newValue, reduced);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setExpression(result).build();
    }

    private static Map<VariableProperty, Integer> reduce(EvaluationContext evaluationContext,
                                                         Expression expression,
                                                         Map<VariableProperty, Integer> map) {
        return map.entrySet().stream()
                .filter(e -> {
                    int v = evaluationContext.getProperty(expression, e.getKey(), true, false);
                    return v != e.getValue();
                })
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Expression propertyWrapper(Expression value, Map<VariableProperty, Integer> properties) {
        assert !(value instanceof Negation) : "we always want the negation to be on the outside";
        return new PropertyWrapper(value, properties, null);
    }

    public static Expression propertyWrapper(Expression value, Map<VariableProperty, Integer> properties, ParameterizedType castType) {
        assert !(value instanceof Negation) : "we always want the negation to be on the outside";
        return new PropertyWrapper(value, properties, castType);
    }

    @Override
    public ParameterizedType returnType() {
        return castType != null ? castType : expression.returnType();
    }

    @Override
    public Precedence precedence() {
        return expression.precedence();
    }

    @Override
    public int order() {
        return expression.order();
    }

    @Override
    public int internalCompareTo(Expression v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String propertyString = properties.entrySet().stream().filter(e -> e.getValue() > e.getKey().falseValue)
                .map(PropertyWrapper::stringValue).sorted().collect(Collectors.joining(","));
        OutputBuilder outputBuilder = new OutputBuilder().add(expression.output(qualification));
        if (!propertyString.isBlank()) {
            outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                    .add(new Text(propertyString));
            if (castType != null) {
                outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                        .add(castType.output(qualification))
                        .add(Symbol.RIGHT_PARENTHESIS);
            }
            outputBuilder.add(Symbol.RIGHT_BLOCK_COMMENT);
        }
        return outputBuilder;
    }

    private static String stringValue(Map.Entry<VariableProperty, Integer> e) {
        switch (e.getKey()) {
            case INDEPENDENT, INDEPENDENT_PARAMETER, CONTEXT_DEPENDENT -> {
                if (e.getValue() == MultiLevel.DEPENDENT_1) return "@Dependent1";
                if (e.getValue() == MultiLevel.DEPENDENT_2) return "@Dependent2";
            }
        }
        return e.getKey().toString();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        int inMap = properties.getOrDefault(variableProperty, Level.DELAY);
        if (inMap != Level.DELAY) return inMap;
        return evaluationContext.getProperty(expression, variableProperty, duringEvaluation, false);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return evaluationContext.linkedVariables(expression);
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
    public <T extends Expression> T asInstanceOf(Class<T> clazz) {
        return expression.asInstanceOf(clazz);
    }

    @Override
    public boolean isInstanceOf(Class<? extends Expression> clazz) {
        return expression.isInstanceOf(clazz);
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        return expression.getInstance(evaluationResult);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Expression oUnboxed)) return false;
        Expression unboxed = this;
        while (unboxed instanceof PropertyWrapper propertyWrapper) {
            unboxed = propertyWrapper.expression;
        }
        while (oUnboxed instanceof PropertyWrapper propertyWrapper) {
            oUnboxed = propertyWrapper.expression;
        }
        return unboxed.equals(oUnboxed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }
}
