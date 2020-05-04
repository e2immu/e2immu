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
    public final Variable variable;
    public final boolean effectivelyFinalUnevaluated;
    public final Set<AnnotationExpression> dynamicTypeAnnotations;
    public final Value valueForLinkAnalysis;
    public final Boolean isNotNull;

    public VariableValue(Variable variable) {
        this(variable, Set.of(), false, null, null);
    }

    public VariableValue(Variable variable,
                         Set<AnnotationExpression> dynamicTypeAnnotations) {
        this(variable, dynamicTypeAnnotations, false, null, null);
    }

    public VariableValue(Variable variable,
                         Set<AnnotationExpression> dynamicTypeAnnotations,
                         boolean effectivelyFinalUnevaluated,
                         Value valueForLinkAnalysis,
                         Boolean isNotNull) {
        this.variable = variable;
        this.effectivelyFinalUnevaluated = effectivelyFinalUnevaluated;
        this.dynamicTypeAnnotations = dynamicTypeAnnotations;
        this.valueForLinkAnalysis = valueForLinkAnalysis;
        this.isNotNull = isNotNull;
    }

    @Override
    public Value notNullCopy() {
        if (isNotNull == Boolean.TRUE) return this;
        return new VariableValue(variable, dynamicTypeAnnotations, effectivelyFinalUnevaluated, valueForLinkAnalysis, true);
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
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public String toString() {
        return variable.detailedString() + (effectivelyFinalUnevaluated ? " ??@E1Immutable??" : "");
    }

    @Override
    public int compareTo(Value o) {
        if (o == UnknownValue.UNKNOWN_VALUE) return -1;
        if (o instanceof VariableValue) return variable.name().compareTo(((VariableValue) o).variable.name());
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            if (equals(((NegatedValue) o).value)) return -1; // I'm always BEFORE my negation
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        if (isNotNull == Boolean.TRUE) return true;
        if (variable.parameterizedType().isPrimitive()) return true;
        if (variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            // quite possibly the field is final and has been annotated...
            return fieldInfo.isNotNull(evaluationContext.getTypeContext());
        } else {
            if (evaluationContext.isNotNull(variable)) return true;
        }
        if (variable instanceof ParameterInfo) {
            ParameterInfo parameterInfo = ((ParameterInfo) variable);
            return parameterInfo.isNotNull(evaluationContext.getTypeContext());
        }
        return null; // we don't know yet
    }

    /*
    The difference between worst and best case here is that the worst case is guaranteed to be stable wrt. this evaluation.
    (Independent of whether the value's type has been analysed or not; the critical point is that it is not being evaluated.)
     */

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        boolean differentType = evaluationContext.getCurrentMethod().typeInfo != variable.parameterizedType().typeInfo;
        boolean e2Immu = (bestCase || differentType) &&
                variable.parameterizedType().isE2Immutable(evaluationContext.getTypeContext()) == Boolean.TRUE;
        if (e2Immu || variable.parameterizedType().isPrimitiveOrStringNotVoid()) return Set.of();
        return Set.of(variable);
    }

    @Override
    public ParameterizedType type() {
        return variable.concreteReturnType();
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
