/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
