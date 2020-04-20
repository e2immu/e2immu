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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeContext;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.log;

// an object, instance of a certain class, potentially after casting
public class Instance implements Value {
    public final ParameterizedType parameterizedType;
    public final List<Value> constructorParameterValues;
    public final MethodInfo constructor;

    public Instance(ParameterizedType parameterizedType) {
        this(parameterizedType, null, null);
    }

    public Instance(ParameterizedType parameterizedType, MethodInfo constructor, List<Value> parameterValues) {
        this.parameterizedType = parameterizedType;
        this.constructor = constructor;
        this.constructorParameterValues = parameterValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance that = (Instance) o;
        return parameterizedType.equals(that.parameterizedType);
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
        return "instanceof " + parameterizedType.detailedString()
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
        boolean differentType = constructor.typeInfo != evaluationContext.getCurrentMethod().typeInfo;
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
        return true;
    }
}
