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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class ClassValue extends ConstantValue implements Constant<ParameterizedType> {
    public final ParameterizedType value;

    public ClassValue(ParameterizedType value) {
        this(value, null);
    }

    public ClassValue(ParameterizedType value, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public String toString() {
        return value.detailedString();
    }

    @Override
    public int order() {
        return ORDER_CONSTANT_CLASS;
    }

    @Override
    public int internalCompareTo(Value v) {
        return value.detailedString().compareTo(((ClassValue) v).value.detailedString());
    }

    @Override
    public ParameterizedType getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassValue that = (ClassValue) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.classTypeInfo.asParameterizedType();
    }
}
