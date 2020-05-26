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

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;
import java.util.Set;

public class InstanceOfValue extends PrimitiveValue {
    public final ParameterizedType parameterizedType;
    public final Variable variable;

    public InstanceOfValue(Variable variable, ParameterizedType parameterizedType) {
        this.parameterizedType = parameterizedType;
        this.variable = variable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceOfValue that = (InstanceOfValue) o;
        return parameterizedType.equals(that.parameterizedType) &&
                variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, variable);
    }

    @Override
    public int order() {
        return ORDER_INSTANCE_OF;
    }

    @Override
    public int internalCompareTo(Value v) {
        int c = variable.name().compareTo(((InstanceOfValue) v).variable.name());
        if (c == 0) c = parameterizedType.detailedString()
                .compareTo(((InstanceOfValue) v).parameterizedType.detailedString());
        return c;
    }

    @Override
    public String toString() {
        return variable.name() + " instanceof " + parameterizedType.detailedString();
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return Set.of(variable);
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return Set.of(variable);
    }
}
