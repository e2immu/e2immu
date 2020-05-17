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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * the result of a new object creation (new XX(...))
 */

public class Instance implements Value {
    @NotNull
    public final ParameterizedType parameterizedType;
    @NotNull
    public final List<Value> constructorParameterValues;
    public final MethodInfo constructor;

    public Instance(@NotNull ParameterizedType parameterizedType, MethodInfo constructor, List<Value> parameterValues) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.constructor = constructor; // con be null, in anonymous classes
        this.constructorParameterValues = ImmutableList.copyOf(parameterValues);
    }

    public static Instance newStringInstance() {
        MethodInfo constructor = Primitives.PRIMITIVES.stringTypeInfo.typeInspection.get().constructors.get(0);
        return new Instance(Primitives.PRIMITIVES.stringParameterizedType, constructor, List.of());
    }

    // every new instance is different.
    // we may come back to this later, but for now...
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof Instance) {
            return parameterizedType.detailedString()
                    .compareTo(((Instance) o).parameterizedType.detailedString());
        }
        if (o instanceof VariableValue) return 1;
        if (o instanceof MethodValue) return -1;
        return -1;
    }

    @Override
    public String toString() {
        return "instance type " + parameterizedType.detailedString()
                + "(" + constructorParameterValues.stream()
                .map(Value::toString)
                .collect(Collectors.joining(", ")) + ")";
    }

    private static final Set<Variable> INDEPENDENT = Set.of();

    /*
     * Rules, assuming the notation b = new B(c, d)
     *
     * 1. no explicit constructor, no parameters on a static type: independent
     * 2. constructor is @Independent: independent
     * 3. B is @E2Immutable: independent
     *
     * the default case is a dependence on c and d
     */
    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        // RULE 1
        if (constructorParameterValues == null || constructor == null) return INDEPENDENT;
        if (constructorParameterValues.isEmpty() && constructor.typeInfo.isStatic()) {
            return INDEPENDENT;
        }

        // RULE 2, 3
        boolean differentType = constructor.typeInfo != evaluationContext.getCurrentType();
        if ((bestCase || differentType) &&
                (constructor.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT) == Level.TRUE // RULE 2
                        || Level.value(constructor.typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
                        Level.E2IMMUTABLE) == Level.TRUE)) { // RULE 3
            return INDEPENDENT;
        }

        // default case
        return constructorParameterValues.stream()
                .flatMap(v -> v.linkedVariables(bestCase, evaluationContext).stream())
                .collect(Collectors.toSet());
    }

    @Override
    public ParameterizedType type() {
        return parameterizedType;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();

        if (VariableProperty.NOT_NULL == variableProperty) {
            return bestType == null ? Level.TRUE :
                    Math.max(Level.TRUE, bestType.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
        if (VariableProperty.CONTAINER == variableProperty || VariableProperty.IMMUTABLE == variableProperty) {
            return bestType == null ? Level.FALSE :
                    Math.max(Level.FALSE, bestType.typeAnalysis.get().getProperty(variableProperty));
        }

        // @NotModified should not be asked here
        throw new UnsupportedOperationException();
    }
}
