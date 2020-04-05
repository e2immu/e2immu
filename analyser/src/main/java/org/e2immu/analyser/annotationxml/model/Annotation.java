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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Mark;

import java.util.ArrayList;
import java.util.List;

@E2Immutable(after = "freeze")
public class Annotation {
    public final String name;
    private List<Value> values = new ArrayList<>();

    public Annotation(String annotationType) {
        this.name = annotationType;
    }

    // TODO @Mark("freeze")
    public Annotation(AnnotationExpression ae) {
        name = ae.typeInfo.fullyQualifiedName;
        if (ae.expressions.isSet()) {
            for (Expression expression : ae.expressions.get()) {
                Value value = new Value(expression);
                values.add(value);
            }
        }
        freeze();
    }

    @Mark("freeze")
    public void freeze() {
        values = ImmutableList.copyOf(values);
    }

    public List<Value> getValues() {
        return values;
    }
}
