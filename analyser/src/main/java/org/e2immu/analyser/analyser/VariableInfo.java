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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.LocalVariableReference;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Map;

public interface VariableInfo {

    enum FieldReferenceState {
        EFFECTIVELY_FINAL_DELAYED,
        SINGLE_COPY,
        MULTI_COPY,
    }

    Value getCurrentValue();

    Value getStateOnAssignment();

    ObjectFlow getObjectFlow();

    Value getInitialValue();

    Value getResetValue();

    VariableInfo getLocalCopyOf();

    default boolean isLocalCopy() {
        return getLocalCopyOf() != null;
    }

    default boolean isNotLocalCopy() {
        return getLocalCopyOf() == null;
    }

    Map<VariableProperty, Integer> properties();

    Variable getVariable();

    // does not always agree with variable.name
    String getName();

    FieldReferenceState getFieldReferenceState();

    int getProperty(VariableProperty variableProperty);

    default boolean haveProperty(VariableProperty variableProperty) {
        int i = getProperty(variableProperty);
        return i != Level.DELAY;
    }

    default boolean isLocalVariableReference() {
        return getVariable() instanceof LocalVariableReference;
    }
}
