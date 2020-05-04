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

import java.util.Objects;
import java.util.Set;

public class VariableValue implements Value {
    public final Variable value;
    public final boolean effectivelyFinalUnevaluated;
    public final Set<AnnotationExpression> dynamicTypeAnnotations;
    public final Value valueForLinkAnalysis;
    public final Boolean isNotNull;

    public VariableValue(Variable value) {
        this(value, Set.of(), false, null, null);
    }

    public VariableValue(Variable value,
                         Set<AnnotationExpression> dynamicTypeAnnotations) {
        this(value, dynamicTypeAnnotations, false, null, null);
    }

    public VariableValue(Variable value,
                         Set<AnnotationExpression> dynamicTypeAnnotations,
                         boolean effectivelyFinalUnevaluated,
                         Value valueForLinkAnalysis,
                         Boolean isNotNull) {
        this.value = value;
        this.effectivelyFinalUnevaluated = effectivelyFinalUnevaluated;
        this.dynamicTypeAnnotations = dynamicTypeAnnotations;
        this.valueForLinkAnalysis = valueForLinkAnalysis;
        this.isNotNull = isNotNull;
    }

    @Override
    public Value notNullCopy() {
        if (isNotNull == Boolean.TRUE) return this;
        return new VariableValue(value, dynamicTypeAnnotations, effectivelyFinalUnevaluated, valueForLinkAnalysis, true);
    }

    @Override
    public Set<AnnotationExpression> dynamicTypeAnnotations(EvaluationContext evaluationContext) {
        return dynamicTypeAnnotations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableValue that = (VariableValue) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.detailedString() + (effectivelyFinalUnevaluated ? " ??@E1Immutable??" : "");
    }

    @Override
    public int compareTo(Value o) {
        if (o == UnknownValue.UNKNOWN_VALUE) return -1;
        if (o instanceof VariableValue) return value.name().compareTo(((VariableValue) o).value.name());
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            if (equals(((NegatedValue) o).value)) return -1; // I'm always BEFORE my negation
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        if (isNotNull != null) return isNotNull;
        if (value.parameterizedType().isPrimitive()) return true;
        if (evaluationContext.isNotNull(value)) return true;
        if (value instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) value).fieldInfo;
            // quite possibly the field is final and has been annotated...
            return fieldInfo.isNotNull(evaluationContext.getTypeContext());
        }
        if (value instanceof ParameterInfo) {
            ParameterInfo parameterInfo = ((ParameterInfo) value);
            // quite possibly the field is final and has been annotated...
            return parameterInfo.isNullNotAllowed(evaluationContext.getTypeContext());
        }
        return null; // we don't know yet
    }

    /*
    The difference between worst and best case here is that the worst case is guaranteed to be stable wrt. this evaluation.
    (Independent of whether the value's type has been analysed or not; the critical point is that it is not being evaluated.)
     */

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        boolean differentType = evaluationContext.getCurrentMethod().typeInfo != value.parameterizedType().typeInfo;
        boolean e2Immu = (bestCase || differentType) &&
                value.parameterizedType().isE2Immutable(evaluationContext.getTypeContext()) == Boolean.TRUE;
        if (e2Immu || value.parameterizedType().isPrimitiveOrStringNotVoid()) return Set.of();
        return Set.of(value);
    }

    @Override
    public ParameterizedType type() {
        return value.concreteReturnType();
    }

    @Override
    public Value valueForLinkAnalysis() {
        return valueForLinkAnalysis != null ? valueForLinkAnalysis : this;
    }

    @Override
    public boolean isEffectivelyFinalUnevaluated() {
        return effectivelyFinalUnevaluated;
    }
}
