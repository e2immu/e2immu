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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.objectflow.ObjectFlow;

import static org.e2immu.analyser.model.Level.FALSE;
import static org.e2immu.analyser.model.Level.TRUE;

public class NullValue extends ConstantValue implements Constant<Object> {
    public static final NullValue NULL_VALUE = new NullValue();

    private NullValue() {
        this(ObjectFlow.NO_FLOW);
    }

    public NullValue(ObjectFlow objectFlow) {
        super(objectFlow);
    }

    @Override
    public int order() {
        return ORDER_CONSTANT_NULL;
    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                return MultiLevel.NULLABLE;
            case SIZE:
            case SIZE_COPY:
            case MODIFIED:
            case IDENTITY:
            case METHOD_DELAY:
                return FALSE;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER:
                return TRUE;
            default:
                throw new UnsupportedOperationException("Asking for " + variableProperty);
        }
    }
}
