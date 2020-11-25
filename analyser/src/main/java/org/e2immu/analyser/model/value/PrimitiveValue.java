/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Objects;

public abstract class PrimitiveValue implements Value {

    public final ObjectFlow objectFlow;

    public PrimitiveValue(ObjectFlow objectFlow) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER:
                return Level.TRUE;
            case NOT_NULL:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case MODIFIED:
            case METHOD_DELAY:
            case IDENTITY:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return null;
    }
}
