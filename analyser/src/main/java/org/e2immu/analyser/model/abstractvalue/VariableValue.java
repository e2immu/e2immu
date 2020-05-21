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
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.Set;

public class VariableValue implements Value {
    @NotNull
    public final Variable variable; // the variable of the inspection, as correct/large as possible

    @NotNull
    public final String name; // the name in the variable properties; this will speed up grabbing the variable properties

    // provided so that we can compute a proper equals(), which is executed...
    @NotNull
    public final EvaluationContext evaluationContext;


    public VariableValue(@NotNull EvaluationContext evaluationContext,
                         @NotNull Variable variable,
                         @NotNull String name) {
        this.evaluationContext = evaluationContext;
        this.variable = variable;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableValue that = (VariableValue) o;
        return evaluationContext.equals(variable, that.variable);
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
    public String asString() {
        return name;
    }

    @Override
    public int compareTo(Value o) {
        if (o.isUnknown()) return -1;
        if (o instanceof VariableValue) return name.compareTo(((VariableValue) o).name);
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            if (equals(((NegatedValue) o).value)) return -1; // I'm always BEFORE my negation
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) {
            if (variable.parameterizedType().isPrimitive()) return Level.TRUE;
            if (variable instanceof This) return Level.TRUE;
        }
        if (variableProperty == VariableProperty.NOT_MODIFIED) {
            ParameterizedType type = variable.parameterizedType();
            if (type.getProperty(VariableProperty.NOT_MODIFIED) == Level.TRUE) return Level.TRUE;
        }
        if (variable instanceof ParameterInfo) {
            return ((ParameterInfo) variable).parameterAnalysis.get().getProperty(variableProperty);
        }
        if (variable instanceof FieldInfo) {
            return ((FieldInfo) variable).fieldAnalysis.get().getProperty(variableProperty);
        }
        return Level.DELAY;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return evaluationContext.getProperty(variable, variableProperty);
    }
    /*
    The difference between worst and best case here is that the worst case is guaranteed to be stable wrt. this evaluation.
    (Independent of whether the value's type has been analysed or not; the critical point is that it is not being evaluated.)
     */

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        boolean differentType = evaluationContext.getCurrentType() != variable.parameterizedType().typeInfo;
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        boolean e2ImmuType;
        if (typeInfo != null) {
            e2ImmuType = Level.haveTrueAt(typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE);
        } else {
            e2ImmuType = false;
        }
        boolean e2Immu = (bestCase || differentType) && e2ImmuType;
        return e2Immu ? Set.of() : Set.of(variable);
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
