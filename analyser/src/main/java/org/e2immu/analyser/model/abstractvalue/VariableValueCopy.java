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
import org.e2immu.annotation.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

// used in a return statement, to freeze the properties

public class VariableValueCopy implements Value {
    @NotNull
    public final Variable variable; // the variable of the inspection, as correct/large as possible

    @NotNull
    public final String name; // the name in the variable properties; this will speed up grabbing the variable properties

    private final Map<VariableProperty, Integer> copiedProperties;
    private final Set<Variable> linkedVariablesBest;
    private final Set<Variable> linkedVariablesWorst;

    public VariableValueCopy(VariableValue original, EvaluationContext evaluationContext) {
        this.variable = original.variable;
        this.name = original.name;
        ImmutableMap.Builder<VariableProperty, Integer> builder = new ImmutableMap.Builder<>();
        for (VariableProperty property :VariableProperty.RETURN_VALUE_PROPERTIES) {
            builder.put(property, original.getProperty(evaluationContext, property));
        }
        copiedProperties = builder.build();
        linkedVariablesBest = ImmutableSet.copyOf(original.linkedVariables(true, evaluationContext));
        linkedVariablesWorst = ImmutableSet.copyOf(original.linkedVariables(false, evaluationContext));
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public String toString() {
        return variable.detailedString();
    }

    @Override
    public int compareTo(Value o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return copiedProperties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        throw new UnsupportedOperationException(); // there is no valid context anymore
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return bestCase ? linkedVariablesBest : linkedVariablesWorst;
    }

    @Override
    public ParameterizedType type() {
        return variable.concreteReturnType();
    }

    @Override
    public Set<Variable> variables() {
        return Set.of(variable);
    }
}
