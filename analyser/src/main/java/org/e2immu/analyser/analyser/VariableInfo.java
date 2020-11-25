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

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public interface VariableInfo {
    String name();

    Variable variable();

    /**
     * @return null when not yet set
     */
    Set<Variable> getLinkedVariables();

    default boolean linkedVariablesIsSet() {
        return getLinkedVariables() != null;
    }

    ObjectFlow getObjectFlow();

    Value getValue();

    default boolean valueIsSet() {
        return getValue() != UnknownValue.NO_VALUE;
    }

    /**
     * Shortcut for checking preconditions for @Mark and @Only; describes the full state at the moment of assignment.
     * EMPTY when no info (empty state), NO_VALUE when not set
     *
     * @return Returns NO_VALUE when not yet set.
     */
    Value getStateOnAssignment();

    int getProperty(VariableProperty variableProperty);

    int getProperty(VariableProperty variableProperty, int defaultValue);

    /**
     * @return immutable copy of the properties map, for debugging mostly
     */
    Map<VariableProperty, Integer> getProperties();

    /**
     * @return an immutable copy, or the same object frozen
     */
    VariableInfo freeze();

    boolean hasProperty(VariableProperty variableProperty);

    Stream<Map.Entry<VariableProperty, Integer>> propertyStream();

    default boolean stateOnAssignmentIsSet() {
        return getStateOnAssignment() != UnknownValue.NO_VALUE;
    }

    boolean isVariableField();
}
