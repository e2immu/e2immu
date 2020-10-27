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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * It is important to note that this value is not a VariableValue, whose properties are kept in the VariableData of the StatementAnalyser.
 * This value keeps its own, unmodifiable, properties.
 * <p>
 * It is used for final fields, and This.
 */
public class FinalFieldValue implements Value {
    public final Variable variable;
    public final ObjectFlow objectFlow;
    private final Map<VariableProperty, Integer> properties;
    private final Set<Variable> linkedVariables;

    public FinalFieldValue(Variable variable,
                           Map<VariableProperty, Integer> properties,
                           Set<Variable> linkedVariables,
                           ObjectFlow objectFlow) {
        assert variable instanceof FieldReference || variable instanceof This;
        this.variable = variable;
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.properties = ImmutableMap.copyOf(properties);
        this.linkedVariables = ImmutableSet.copyOf(linkedVariables);
    }

    @Override
    public ParameterizedType type() {
        return variable.concreteReturnType();
    }

    @Override
    public Set<Variable> variables() {
        return Set.of(variable);
    }


    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return linkedVariables;
    }

    @Override
    public int order() {
        return ORDER_VARIABLE_VALUE;
    }

    @Override
    public int internalCompareTo(Value v) {
        FinalFieldValue finalFieldValue = (FinalFieldValue) v;
        return variable.fullyQualifiedName().compareTo(finalFieldValue.variable.fullyQualifiedName());
    }

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return Primitives.isNumeric(typeInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalFieldValue that)) return false;
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public String toString() {
        return variable.fullyQualifiedName();
    }
}
