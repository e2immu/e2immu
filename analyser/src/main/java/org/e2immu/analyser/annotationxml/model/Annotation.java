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

package org.e2immu.analyser.annotationxml.model;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;

import java.util.ArrayList;
import java.util.List;

public record Annotation(String name, List<Value> values) {

    public static Annotation from(AnnotationExpression ae) {
        Builder builder = new Builder(ae.typeInfo().fullyQualifiedName);
        for (Expression expression : ae.expressions()) {
            Value value = new Value(expression);
            builder.addValue(value);
        }
        return builder.build();
    }

    public static class Builder {
        private final String name;
        private final List<Value> values = new ArrayList<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder addValue(Value value) {
            values.add(value);
            return this;
        }

        public Annotation build() {
            return new Annotation(name, List.copyOf(values));
        }
    }
}
