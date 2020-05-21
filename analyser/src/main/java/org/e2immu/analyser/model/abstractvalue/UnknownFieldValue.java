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
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;

// properties directly copied from type

public class UnknownFieldValue implements Value {

    public final FieldInfo fieldInfo;

    public UnknownFieldValue(FieldInfo fieldInfo) {
        this.fieldInfo = fieldInfo;
    }

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public int compareTo(Value o) {
        if (o == this) return 0;
        return 1; // I'm always at the end
    }

    @Override
    public String toString() {
        return fieldInfo.name + " non-final";
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return fieldInfo.type.getProperty(variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }
}
