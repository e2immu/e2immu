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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertyWrapper implements Expression, ExpressionWrapper {

    /*

     We are essentially interested in value, but add extra properties.
     Alternatively, we wrap a dedicated object flow

     */
    public final Expression expression;
    public final Map<VariableProperty, Integer> properties;
    public final ObjectFlow overwriteObjectFlow;

    private PropertyWrapper(Expression expression, Map<VariableProperty, Integer> properties, ObjectFlow objectFlow) {
        this.expression = expression;
        this.properties = properties;
        overwriteObjectFlow = objectFlow;
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
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setExpression(PropertyWrapper.propertyWrapper(evaluationContext, reValue.value(),
                properties, getObjectFlow())).build();
    }

    public static Expression propertyWrapper(EvaluationContext evaluationContext, Expression value,
                                             Map<VariableProperty, Integer> properties, ObjectFlow objectFlow) {
        Map<VariableProperty, Integer> newMap = new HashMap<>();
        for (Map.Entry<VariableProperty, Integer> entry : properties.entrySet()) {
            int newPropertyValue = evaluationContext.getProperty(value, entry.getKey());
            if (newPropertyValue < entry.getValue()) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        // if I cannot contribute, there's no point being here...
        if (newMap.isEmpty() && objectFlow == null) return value;

        // second, we always want the negation to be on the outside
        if (value instanceof Negation) {
            throw new UnsupportedOperationException(); // this makes no sense!!
        }
        return new PropertyWrapper(value, properties, objectFlow);
    }

    public static Expression propertyWrapperForceProperties(Expression value, Map<VariableProperty, Integer> properties) {
        return new PropertyWrapper(value, properties, value.getObjectFlow());
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public Precedence precedence() {
        return expression.precedence();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
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
    public OutputBuilder output() {
        return new OutputBuilder().add(expression.output())
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text(properties.entrySet().stream().filter(e -> e.getValue() > e.getKey().falseValue)
                        .map(e -> e.getKey().toString()).sorted().collect(Collectors.joining(","))))
                .add(Symbol.RIGHT_BLOCK_COMMENT);
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
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        int inMap = properties.getOrDefault(variableProperty, Level.DELAY);
        if (inMap != Level.DELAY) return inMap;
        return evaluationContext.getProperty(expression, variableProperty);
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
    public ObjectFlow getObjectFlow() {
        return overwriteObjectFlow != null ? overwriteObjectFlow : expression.getObjectFlow();
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
        if (!(o instanceof Expression)) return false;
        Expression unboxed = this;
        Expression oUnboxed = (Expression) o;
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
