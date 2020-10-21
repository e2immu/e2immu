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

import java.util.Objects;
import java.util.Set;

/**
 * The evaluation context has to reroute requests for property values, linked variables, and object flow either
 * - to the variable data in the statement analyser, esp. for local variables
 * - to the relevant parameterAnalysis, fieldAnalysis
 */
public class VariableValue implements Value {
    public final String name; // the name in the variable properties; this will speed up grabbing the variable properties
    public final Variable variable;
    private final boolean variableField;
    private final ObjectFlow objectFlow;

    public VariableValue(Variable variable) {
        this(variable, ObjectFlow.NO_FLOW);
    }

    public VariableValue(Variable variable, ObjectFlow objectFlow) {
        this(variable, objectFlow, false);
    }

    public VariableValue(Variable variable, ObjectFlow objectFlow, boolean variableField) {
        this(variable, variable.fullyQualifiedName(), objectFlow, variableField);
    }

    // dependent variables have a different name
    public VariableValue(Variable variable,
                         String name,
                         ObjectFlow objectFlow,
                         boolean variableField) {
        this.variable = variable;
        this.name = name;
        this.objectFlow = objectFlow;
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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        throw new UnsupportedOperationException();
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
        if (!(o instanceof VariableValue that)) return false;
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
