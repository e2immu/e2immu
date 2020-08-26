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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PropertyWrapper implements Value, ValueWrapper {

    /*

     We are essentially interested in value, but add extra properties.

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
    public Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        Value reValue = value.reEvaluate(evaluationContext, translation);
        return PropertyWrapper.propertyWrapper(reValue, properties, getObjectFlow());
    }

    public static Value propertyWrapper(Value value, Map<VariableProperty, Integer> properties, ObjectFlow objectFlow) {
        // TODO this for-loop is a really good candidate to rewrite using streaming
        Map<VariableProperty, Integer> newMap = new HashMap<>();
        for (Map.Entry<VariableProperty, Integer> entry : properties.entrySet()) {
            int newPropertyValue = value.getPropertyOutsideContext(entry.getKey());
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
        if (value instanceof ConstrainedNumericValue) {
            int size = newMap.getOrDefault(VariableProperty.SIZE, Level.DELAY);
            if (Level.haveEquals(size) || size < Level.TRUE) throw new UnsupportedOperationException();
            return ConstrainedNumericValue.lowerBound(value, Level.decodeSizeMin(size));
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
        return value.toString() + "," + properties.entrySet().stream()
                .filter(e -> e.getValue() > e.getKey().falseValue)
                .map(e -> e.getKey().toString()).sorted().collect(Collectors.joining(","));
    }


    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        int inMap = properties.getOrDefault(variableProperty, Level.DELAY);
        if (inMap != Level.DELAY) return inMap;
        return evaluationContext.getProperty(value, variableProperty);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        int inMap = properties.getOrDefault(variableProperty, Level.DELAY);
        if (inMap != Level.DELAY) return inMap;
        return value.getPropertyOutsideContext(variableProperty);
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return value.linkedVariables(evaluationContext);
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }

    // meaning, can we re-evaluate this? can it be part of an inline operation?
    @Override
    public boolean isExpressionOfParameters() {
        return value.isExpressionOfParameters();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return overwriteObjectFlow != null ? overwriteObjectFlow : value.getObjectFlow();
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        value.visit(consumer);
        consumer.accept(this);
    }
}
