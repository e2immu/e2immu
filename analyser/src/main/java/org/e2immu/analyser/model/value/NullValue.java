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
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;

import static org.e2immu.analyser.model.Level.FALSE;
import static org.e2immu.analyser.model.Level.TRUE;

public class NullValue implements Value, Constant<Object> {
    public static final NullValue NULL_VALUE = new NullValue();

    private NullValue() {
    }

    @Override
    public int compareTo(Value o) {
        if (o == this) return 0;
        return -1; // I'm always at the front
    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public String asString() {
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
        if (variableProperty == VariableProperty.NOT_NULL) return FALSE;
        if (variableProperty == VariableProperty.CONTAINER) return TRUE;
        if (variableProperty == VariableProperty.IMMUTABLE) return VariableProperty.IMMUTABLE.best;
        throw new UnsupportedOperationException();
    }
}
