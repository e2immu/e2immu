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
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NullNotAllowed;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodValue implements Value {
    public final MethodInfo methodInfo;
    public final List<Value> parameters;
    public final Value object;

    public MethodValue(@NullNotAllowed MethodInfo methodInfo, Value object, @NullNotAllowed List<Value> parameters) {
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.parameters = Objects.requireNonNull(parameters);
        this.object = object;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodValue that = (MethodValue) o;
        return methodInfo.equals(that.methodInfo) &&
                parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, parameters);
    }

    @Override
    public String toString() {
        return methodInfo.fullyQualifiedName()
                + parameters.stream().map(Object::toString).collect(Collectors.joining(", ", "(", ")"));
    }

    @Override
    public int compareTo(Value o) {
        if (o == UnknownValue.UNKNOWN_VALUE) return -1;
        if (o instanceof MethodValue) {
            MethodValue mv = (MethodValue) o;
            int c = methodInfo.fullyQualifiedName().compareTo(mv.methodInfo.fullyQualifiedName());
            if (c != 0) return c;
            int i = 0;
            while (i < parameters.size()) {
                if (i >= mv.parameters.size()) return 1;
                c = parameters.get(i).compareTo(mv.parameters.get(i));
                if (c != 0) return c;
                i++;
            }
            return -1;
        }
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        TypeContext typeContext = evaluationContext.getTypeContext();
        Boolean isNotNull = methodInfo.isNotNull(typeContext);
        if (isNotNull == Boolean.TRUE) {
            return true;
        }
        Boolean isIdentity = methodInfo.isIdentity(typeContext);
        if (isIdentity == Boolean.TRUE) {
            Value valueFirst = parameters.get(0);
            return valueFirst.isNotNull(evaluationContext);
        }
        Boolean isFluent = methodInfo.isFluent(typeContext);
        if (isFluent == Boolean.TRUE) return true;
        boolean recursiveCall = methodInfo == evaluationContext.getCurrentMethod();
        if (recursiveCall) {
            // recursive call, we'll just reply true, but exclude the result later for return analysis
            return true;
        }
        if (isFluent == null || isNotNull == null || isIdentity == null) return null;
        return false;
    }

    /* We're in the situation of a = b.method(c, d), and we are computing the variables that `a` will be linked
     * to. There is NO need to consider linking between `b`, `c` and `d` because that linking takes place in the method's
     * definition itself.
     *
     * Primitives and context classes break the chain: they cannot be modified. So if `b` is a context class, then
     * `a` is independent of `b`, `c`, `d` because no method in a context class can return a modifiable object.
     * More generally, any primitive or context class return type breaks the chain.
     * If the method is marked independent, then by definition there is no dependence.
     * If `c` is a primitive or context class instance, `c` does not need to be added.
     *
     * But unless we know better, we need to make a dependent on b, c, and d.
     */

    private static final Set<Variable> INDEPENDENT = Set.of();

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        TypeContext typeContext = evaluationContext.getTypeContext();
        // if the method is one of a context class, then we are guaranteed that we cannot modify the result...
        // so no need to dig...
        if (methodInfo.typeInfo.isE2Immutable(evaluationContext.getTypeContext()) == Boolean.TRUE) return INDEPENDENT;
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType == Primitives.PRIMITIVES.voidParameterizedType) return INDEPENDENT;
        if (returnType.isPrimitiveOrStringNotVoid()) return INDEPENDENT;
        if (returnType.isEffectivelyImmutable(typeContext) == Boolean.TRUE) return INDEPENDENT;
        if (methodInfo.isIndependent(typeContext) == Boolean.TRUE) return INDEPENDENT;

        Set<Variable> result = new HashSet<>();
        // unless we know better, we need to link to the parameters of the method
        // the VariableValue class will deal with parameters that are context classes
        parameters.forEach(p -> result.addAll(p.linkedVariables(evaluationContext)));

        if (object != null) {
            result.addAll(object.linkedVariables(evaluationContext));
        }
        return result;
    }

}
