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
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotModified;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Value extends Comparable<Value> {
    // outside of scope of evaluation
    default Boolean isNotNull(TypeContext typeContext) { return true; }

    // inside scope of evaluation
    default Boolean isNotNull(EvaluationContext evaluationContext) { return true; }

    default Set<AnnotationExpression> dynamicTypeAnnotations(TypeContext typeContext) {
        return Set.of();
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
     * @return the type, if we are certain
     */
    default ParameterizedType type() {
        return null;
    }

    /**
     * return a value, but with the guarantee that isNotNull will evaluate to true
     *
     * @return a copy, equal to this, but with the isNotNull flag set to true
     */
    default Value finalNotNullCopy() {
        return this;
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
