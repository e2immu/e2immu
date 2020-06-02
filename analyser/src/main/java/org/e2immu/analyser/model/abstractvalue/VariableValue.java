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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.Set;

public class VariableValue extends ValueWithVariable {

    @NotNull
    public final String name; // the name in the variable properties; this will speed up grabbing the variable properties

    // provided so that we can compute a proper equals(), which is executed...
    @NotNull
    public final EvaluationContext evaluationContext;

    // multiCopy means that due to absence of synchronisation, this non-final field's value cannot be correctly computed
    public final boolean multiCopyNonFinalField;

    public VariableValue(@NotNull EvaluationContext evaluationContext,
                         @NotNull Variable variable,
                         @NotNull String name) {
        this(evaluationContext, variable, name, false);
    }

    public VariableValue(@NotNull EvaluationContext evaluationContext,
                         @NotNull Variable variable,
                         @NotNull String name,
                         boolean multiCopyNonFinalField) {
        super(variable);
        this.evaluationContext = evaluationContext;
        this.name = name;
        this.multiCopyNonFinalField = multiCopyNonFinalField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableValue that = (VariableValue) o;
        return evaluationContext.equals(variable, that.variable);
    }

    @Override
    public boolean isUnknown() {
        return multiCopyNonFinalField; // multiCopy non-final field has an unknown value at all times
    }

    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) {
            if (variable.parameterizedType().isPrimitive()) return Level.TRUE;
            if (variable instanceof This) return Level.TRUE;
        }
        if (variableProperty == VariableProperty.MODIFIED) {
            ParameterizedType type = variable.parameterizedType();
            if (type.getProperty(VariableProperty.MODIFIED) == Level.FALSE) return Level.FALSE;
        }
        if (variable instanceof ParameterInfo) {
            return ((ParameterInfo) variable).parameterAnalysis.get().getProperty(variableProperty);
        }
        if (variable instanceof FieldInfo) {
            return ((FieldInfo) variable).fieldAnalysis.get().getProperty(variableProperty);
        }
        return Level.DELAY;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return evaluationContext.getProperty(variable, variableProperty);
    }
}
