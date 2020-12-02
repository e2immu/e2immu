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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertyWrapper implements Value, ValueWrapper {

    /*

     We are essentially interested in value, but add extra properties.
     Alternatively, we wrap a dedicated object flow

     */
    public final Value value;
    public final Map<VariableProperty, Integer> properties;
    public final ObjectFlow overwriteObjectFlow;

    private PropertyWrapper(Value value, Map<VariableProperty, Integer> properties, ObjectFlow objectFlow) {
        this.value = value;
        this.properties = properties;
        overwriteObjectFlow = objectFlow;
    }

    @Override
    public Value getValue() {
        return value;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_PROPERTY;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reValue = value.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setValue(PropertyWrapper.propertyWrapper(evaluationContext, reValue.value, properties, getObjectFlow())).build();
    }

    public static Value propertyWrapper(EvaluationContext evaluationContext, Expression value,
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
        if (value instanceof NegatedValue) {
            throw new UnsupportedOperationException(); // this makes no sense!!
        }
        return new PropertyWrapper(value, properties, objectFlow);
    }

    @Override
    public int order() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int internalCompareTo(Value v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        if (printMode.forDebug()) {
            return value.print(printMode) + "," + properties.entrySet().stream()
                    .filter(e -> e.getValue() > e.getKey().falseValue)
                    .map(e -> e.getKey().toString()).sorted().collect(Collectors.joining(","));
        }
        // transparent
        return value.print(printMode);
    }

    @Override
    public boolean isNumeric() {
        return value.isNumeric();
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        int inMap = properties.getOrDefault(variableProperty, Level.DELAY);
        if (inMap != Level.DELAY) return inMap;
        return evaluationContext.getProperty(value, variableProperty);
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return evaluationContext.linkedVariables(value);
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return overwriteObjectFlow != null ? overwriteObjectFlow : value.getObjectFlow();
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if(predicate.test(this)) {
            value.visit(predicate);
        }
    }

    @Override
    public <T extends Value> T asInstanceOf(Class<T> clazz) {
        return value.asInstanceOf(clazz);
    }

    @Override
    public boolean isInstanceOf(Class<? extends Value> clazz) {
        return value.isInstanceOf(clazz);
    }

    @Override
    public ParameterizedType type() {
        return value.type();
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return value.getInstance(evaluationContext);
    }
}
