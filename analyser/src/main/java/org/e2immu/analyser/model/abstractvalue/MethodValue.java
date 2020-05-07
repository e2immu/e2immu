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
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodValue implements Value {
    public final MethodInfo methodInfo;
    public final List<Value> parameters;
    public final Value object;

    public MethodValue(@NotNull MethodInfo methodInfo, @NotNull Value object, @NotNull List<Value> parameters) {
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.parameters = Objects.requireNonNull(parameters);
        this.object = Objects.requireNonNull(object);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodValue that = (MethodValue) o;
        return methodInfo.equals(that.methodInfo) &&
                parameters.equals(that.parameters) &&
                object.equals(that.object) &&
                methodInfo.methodAnalysis.sideEffect.isSet() &&
                methodInfo.methodAnalysis.sideEffect.get().atMost(SideEffect.NONE_CONTEXT);
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, methodInfo, parameters);
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
            return object.compareTo(mv.object);
        }
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public Integer getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        boolean recursiveCall = methodInfo == evaluationContext.getCurrentMethod();
        if (recursiveCall) {
            // recursive call, we'll just reply true, but exclude the result later for return analysis
            return 1;
        }
        switch (variableProperty) {
            case NOT_NULL:
                return localNotNull(evaluationContext);
            case IMMUTABLE:
                return localImmutable(evaluationContext.getTypeContext());
            case CONTAINER:
            default:
        }
        throw new UnsupportedOperationException();
    }

    private Integer localNotNull(EvaluationContext evaluationContext) {
        TypeContext typeContext = evaluationContext.getTypeContext();
        Integer notNull = methodInfo.getNotNull(typeContext);
        if (notNull == null) return null;
        Boolean isIdentity = methodInfo.isIdentity(typeContext);
        if (isIdentity == null) return null;
        Integer identityNotNull;
        if (isIdentity == Boolean.TRUE) {
            Value valueFirst = parameters.get(0);
            identityNotNull = valueFirst.getProperty(evaluationContext, VariableProperty.NOT_NULL);
            if (identityNotNull == null) return null;
        } else {
            identityNotNull = 0;
        }
        return Math.max(notNull, identityNotNull);
    }

    private Integer localImmutable(TypeContext typeContext) {
        Integer methodImmutable = methodInfo.getImmutable(typeContext);
        if (methodImmutable == null) return null;
        Boolean isIdentity = methodInfo.isIdentity(typeContext);
        if (isIdentity == null) return null;
        Integer identityImmutable;
        if (isIdentity) {
            identityImmutable = methodInfo.methodInspection.get().parameters.get(0).getImmutable(typeContext);
            if (identityImmutable == null) return null;
        } else {
            identityImmutable = 0;
        }
        Boolean isFluent = methodInfo.isFluent(typeContext);
        if (isFluent == null) return null;
        Integer typeImmutable;
        if (isFluent) {
            typeImmutable = methodInfo.typeInfo.getImmutable(typeContext);
            if (typeImmutable == null) return null;
        } else {
            typeImmutable = 0;
        }
        return Math.max(Math.max(methodImmutable, identityImmutable), typeImmutable);
    }

    /* We're in the situation of a = b.method(c, d), and we are computing the variables that `a` will be linked
     * to. There is no need to consider linking between `b`, `c` and `d` here because that linking takes place in the method's
     * definition itself. We consider 4 cases:
     *
     * 1. a is primitive or e2immutable: independent
     * 2. method is @Independent: independent (the very definition)
     * 3. b is @E2Immutable: only dependent on c, d
     *
     * Note that a dependence on a parameter is only possible when it is not primitive or @E2Immutable (see VariableValue).
     * On top of that comes the situation where the analyser has more detailed information than is in the annotations.
     * For now, we decide to ignore such information.
     *
     */

    private static final Set<Variable> INDEPENDENT = Set.of();

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        TypeContext typeContext = evaluationContext.getTypeContext();

        // RULE 1
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType == Primitives.PRIMITIVES.voidParameterizedType) return INDEPENDENT; // no assignment
        if (returnType.isPrimitiveOrStringNotVoid()) return INDEPENDENT;

        boolean returnTypeDifferent = returnType.typeInfo != evaluationContext.getCurrentType();
        if ((bestCase || returnTypeDifferent) && returnType.isE2Immutable(typeContext) == Boolean.TRUE) {
            return INDEPENDENT;
        }

        // RULE 2
        boolean methodInfoDifferentType = methodInfo.typeInfo != evaluationContext.getCurrentType();
        if ((bestCase || methodInfoDifferentType) && methodInfo.isIndependent(typeContext) == Boolean.TRUE) {
            return INDEPENDENT;
        }

        // some prep.

        Set<Variable> result = new HashSet<>();
        parameters.forEach(p -> result.addAll(p.linkedVariables(bestCase, evaluationContext)));

        // RULE 3
        if ((bestCase || methodInfoDifferentType) &&
                methodInfo.typeInfo.isE2Immutable(typeContext) == Boolean.TRUE) // RULE 3
            return result;

        // default case, add b
        result.addAll(object.linkedVariables(bestCase, evaluationContext));

        return result;
    }

    @Override
    public ParameterizedType type() {
        return methodInfo.returnType();
    }
}
