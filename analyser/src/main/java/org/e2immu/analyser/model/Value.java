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

import org.e2immu.analyser.model.abstractvalue.EqualsValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.annotation.NotModified;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

public interface Value extends Comparable<Value> {
    Boolean isNotNull(EvaluationContext evaluationContext);

    default Set<AnnotationExpression> dynamicTypeAnnotations(EvaluationContext evaluationContext) { return Set.of(); }

    default IntValue toInt() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    default String asString() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    default Optional<Variable> variableIsNull() {
        if (this instanceof EqualsValue &&
                ((EqualsValue) this).lhs == NullValue.NULL_VALUE &&
                ((EqualsValue) this).rhs instanceof VariableValue) {
            return Optional.of(((VariableValue) ((EqualsValue) this).rhs).value);
        }
        return Optional.empty();
    }

    default Optional<Variable> variableIsNotNull() {
        if (this instanceof NegatedValue) return ((NegatedValue) this).value.variableIsNull();
        return Optional.empty();
    }

    @NotModified
    default Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return Set.of();
    }
}
