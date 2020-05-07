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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * An object of a given type. Can even be not @NotNull.
 * It can be explicitly constructed, in which case linking can be investigated.
 * All Instance objects are different.
 * <p>
 * Note that we could make things more complicated by assuming that the creation of two
 * objects from identical parameters will yield an object that equals... but we would
 * have to study the equals method. That's for later.
 */

public class Instance implements Value {
    @NotNull
    public final ParameterizedType parameterizedType;
    public final List<Value> constructorParameterValues;
    public final MethodInfo constructor;
    public final boolean isNotNull;
    public final int uniqueNumber;
    private final static AtomicInteger uniqueNumberGenerator = new AtomicInteger();

    public Instance(@NotNull ParameterizedType parameterizedType) {
        this(parameterizedType, null, null, true, uniqueNumberGenerator.incrementAndGet());
    }

    public Instance(@NotNull ParameterizedType parameterizedType, MethodInfo constructor, List<Value> parameterValues, boolean isNotNull) {
        this(parameterizedType, constructor, parameterValues, isNotNull, uniqueNumberGenerator.incrementAndGet());
    }

    private Instance(@NotNull ParameterizedType parameterizedType, MethodInfo constructor, List<Value> parameterValues, boolean isNotNull, int uniqueNumber) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.constructor = constructor;
        this.constructorParameterValues = parameterValues == null ? null : ImmutableList.copyOf(parameterValues);
        this.isNotNull = isNotNull;
        this.uniqueNumber = uniqueNumber;
    }

    @Override
    public Value finalNotNullCopy() {
        if (isNotNull) return this;
        return new Instance(parameterizedType, constructor, constructorParameterValues, true, uniqueNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance that = (Instance) o;
        return uniqueNumber == that.uniqueNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType);
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
        return "instance " + uniqueNumber + ": " + parameterizedType.detailedString()
                + (constructorParameterValues != null ? "(" + constructorParameterValues.stream()
                .map(Value::toString)
                .collect(Collectors.joining(", ")) + ")" : "");
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
        TypeContext typeContext = evaluationContext.getTypeContext();
        boolean differentType = constructor.typeInfo != evaluationContext.getCurrentType();
        if ((bestCase || differentType) &&
                (constructor.isIndependent(typeContext) == Boolean.TRUE // RULE 2
                        || constructor.typeInfo.isE2Immutable(typeContext) == Boolean.TRUE)) { // RULE 3
            return INDEPENDENT;
        }

        // default case
        return constructorParameterValues.stream()
                .flatMap(v -> v.linkedVariables(bestCase, evaluationContext).stream())
                .collect(Collectors.toSet());
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return isNotNull;
    }

    @Override
    public Boolean isNotNull(TypeContext typeContext) {
        return isNotNull;
    }

    @Override
    public ParameterizedType type() {
        return parameterizedType;
    }

    public boolean explicitlyConstructed() {
        return constructor != null;
    }
}
