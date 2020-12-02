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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;

/**
 * the thing that, for now, makes TypeValue different from UnknownValue is that it is not null.
 * <p>
 * for object flows: TypeValue is used as the scope for static methods.
 */
public class TypeValue implements Value {
    public final ParameterizedType parameterizedType;
    public final ObjectFlow objectFlow;

    public TypeValue(ParameterizedType parameterizedType, ObjectFlow objectFlow) {
        this.parameterizedType = parameterizedType;
        this.objectFlow = objectFlow;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public ParameterizedType type() {
        return parameterizedType;
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return parameterizedType.print(printMode);
    }

    @Override
    public int order() {
        return ORDER_TYPE;
    }

    @Override
    public int internalCompareTo(Value v) {
        return parameterizedType.detailedString().compareTo(((TypeValue) v).parameterizedType.detailedString());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) return MultiLevel.EFFECTIVELY_NOT_NULL;
        return Level.FALSE;
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return null;
    }
}
