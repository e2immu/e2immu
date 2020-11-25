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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class BoolValue extends ConstantValue implements Constant<Boolean> {
    public final boolean value;
    private final ParameterizedType booleanParameterizedType;

    public BoolValue(Primitives primitives, boolean value) {
        this(primitives, value, ObjectFlow.NO_FLOW);
    }

    public BoolValue(Primitives primitives, boolean value, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = value;
        this.booleanParameterizedType = primitives.booleanParameterizedType;
    }

    private BoolValue(ParameterizedType booleanParameterizedType, boolean value, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = value;
        this.booleanParameterizedType = booleanParameterizedType;
    }

    public static EvaluationResult of(boolean b, Location location, EvaluationContext evaluationContext, Origin origin) {
        Primitives primitives = evaluationContext.getPrimitives();
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        ObjectFlow objectFlow = builder.createInternalObjectFlow(location, primitives.booleanParameterizedType, origin);
        return builder.setValue(new BoolValue(primitives, b, objectFlow)).build();
    }

    public static Value createTrue(Primitives primitives) {
        return new BoolValue(primitives, true, ObjectFlow.NO_FLOW);
    }

    public static Value createFalse(Primitives primitives) {
        return new BoolValue(primitives, false, ObjectFlow.NO_FLOW);
    }

    @Override
    public int order() {
        return ORDER_CONSTANT_BOOLEAN;
    }

    @Override
    public int internalCompareTo(Value v) {
        return Boolean.compare(value, ((BoolValue) v).value);
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoolValue boolValue = (BoolValue) o;
        return value == boolValue.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public ParameterizedType type() {
        return booleanParameterizedType;
    }

    public Value negate() {
        return new BoolValue(booleanParameterizedType, !value, objectFlow);
    }
}
