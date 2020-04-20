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

import java.util.Objects;
import java.util.Set;

public class VariableValue implements Value {
    public final Variable value;
    public final boolean effectivelyFinalUnevaluated;

    public VariableValue(Variable value) {
        this(value, false);
    }

    public VariableValue(Variable value, boolean effectivelyFinalUnevaluated) {
        this.value = value;
        this.effectivelyFinalUnevaluated = effectivelyFinalUnevaluated;
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
        return value.detailedString() + (effectivelyFinalUnevaluated ? " ??@Final??" : "");
    }

    @Override
    public int compareTo(Value o) {
        if (o == UnknownValue.UNKNOWN_VALUE) return -1;
        if (o instanceof VariableValue) return value.name().compareTo(((VariableValue) o).value.name());
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
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
                value.parameterizedType().isEffectivelyImmutable(evaluationContext.getTypeContext()) == Boolean.TRUE;
        if (e2Immu || value.parameterizedType().isPrimitiveOrStringNotVoid()) return Set.of();
        return Set.of(value);
    }

}
