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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.EqualsValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotModified;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared properties: @NotNull(n), dynamic type properties (@Immutable(n), @Container)
 * Properties of variables are ALWAYS computed inside an evaluation context; properties of methods come from outside the scope only.
 */
public interface Value extends Comparable<Value> {

    default Integer getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) return 1; // default = @NotNull
        return null; // no information about @NotModified, @Container/@Immutable, @Final
    }

    // null = no idea, 0 = false, 1 = true
    default Boolean isNotNull(EvaluationContext evaluationContext) {
        Integer isNotNull = getProperty(evaluationContext, VariableProperty.NOT_NULL);
        return isNotNull == null ? null : isNotNull == 1;
    }

    // null = no idea, -1 delaying; 1 = true, 0 = false
    default Boolean isFinal(EvaluationContext evaluationContext) {
        Integer isFinal = getProperty(evaluationContext, VariableProperty.FINAL);
        return isFinal == null ? null : isFinal == 1;
    }

    default Set<AnnotationExpression> dynamicTypeAnnotations(EvaluationContext evaluationContext) {
        Integer container = getProperty(evaluationContext, VariableProperty.CONTAINER);
        Integer immutable = getProperty(evaluationContext, VariableProperty.IMMUTABLE);
        boolean noContainer = container == null;
        boolean noImmutable = immutable == null;

        if (noContainer && noImmutable) return Set.of();
        if (noContainer) return Set.of(AnnotationExpression.immutable(evaluationContext.getTypeContext(), immutable));
        return Set.of(AnnotationExpression.container(evaluationContext.getTypeContext(), noImmutable ? 0 : immutable));
    }

    default IntValue toInt() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    default String asString() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    @NotModified
    default Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return Set.of();
    }

    /**
     * @return a map with all clauses, true for V == null, false for V != null
     */
    default Map<Variable, Boolean> individualNullClauses() {
        return Map.of();
    }

    /**
     * @return the type, if we are certain; used in WidestType for operators
     */
    default ParameterizedType type() {
        return null;
    }

    /**
     * will be overridden by VariableValue
     *
     * @return the object that we need to compute the links in an assignment,
     * as compared to the object with which we continue
     */
    default Value valueForLinkAnalysis() {
        return this;
    }

}
