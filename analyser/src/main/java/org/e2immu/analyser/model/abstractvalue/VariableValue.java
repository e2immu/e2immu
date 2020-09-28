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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VariableValue implements Value {
    public final String name; // the name in the variable properties; this will speed up grabbing the variable properties
    public final Variable variable;
    public final ObjectFlow objectFlow;
    private final Map<VariableProperty, Integer> properties;
    private final Set<Variable> linkedVariables;
    private final boolean variableField;

    public VariableValue(Variable variable,
                         String name,
                         Map<VariableProperty, Integer> properties,
                         Set<Variable> linkedVariables,
                         ObjectFlow objectFlow,
                         boolean variableField) {
        this.variable = variable;
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.name = name;
        this.properties = ImmutableMap.copyOf(properties);
        this.linkedVariables = ImmutableSet.copyOf(linkedVariables);
        this.variableField = variableField;
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
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }
    /*
          if (variableProperty == VariableProperty.NOT_NULL) {
            if (variable.parameterizedType().isPrimitive() || variable instanceof This)
                return MultiLevel.EFFECTIVELY_NOT_NULL;
        }
        if (variableProperty == VariableProperty.MODIFIED) {
            ParameterizedType type = variable.parameterizedType();
            if (type.getProperty(VariableProperty.MODIFIED) == Level.FALSE) return Level.FALSE;
        }
        if (variable instanceof ParameterInfo) {
            return ((ParameterInfo) variable).parameterAnalysis.get().getProperty(variableProperty);
        }
        if (variable instanceof FieldInfo) {
            return ((FieldInfo) variable).fieldAnalysis.get().getProperty(variableProperty);
        }
     */

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
        VariableValue variableValue = (VariableValue) v;
        return name.compareTo(variableValue.name);
    }

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return typeInfo.isNumericPrimitive() || typeInfo.isNumericPrimitiveBoxed();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableValue)) return false;
        VariableValue that = (VariableValue) o;
        if (!variable.equals(that.variable)) return false;
        return !variableField;
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public String toString() {
        return name;
    }
}
