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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.INITIAL;
import static org.e2immu.analyser.analyser.VariableInfoContainer.*;

public interface VariableInfo {
    String name();

    Variable variable();

    /**
     * @return null when not yet set
     */
    LinkedVariables getLinkedVariables();

    default boolean linkedVariablesIsSet() {
        return getLinkedVariables() != LinkedVariables.DELAY;
    }

    ObjectFlow getObjectFlow();

    Expression getValue();

    default boolean isDelayed() {
        return !valueIsSet();
    }

    default boolean isNotDelayed() {
        return valueIsSet();
    }

    Set<Integer> getReadAtStatementTimes();

    boolean valueIsSet();

    int getProperty(VariableProperty variableProperty);

    int getProperty(VariableProperty variableProperty, int defaultValue);

    /**
     * @return immutable copy of the properties map, for debugging mostly
     */
    VariableProperties getProperties();

    /**
     * @return an immutable copy, or the same object frozen
     */
    VariableInfo freeze();

    boolean hasProperty(VariableProperty variableProperty);

    Stream<Map.Entry<VariableProperty, Integer>> propertyStream();

    LinkedVariables getStaticallyAssignedVariables();

    default boolean objectFlowIsSet() {
        return getObjectFlow() != null;
    }

    /*
    if statement time < 0, this means that statement time is irrelevant for this variable.

    Otherwise, it depends on the type of variable.
    If it is a field reference, it is variable, and this is the time of the latest assignment in this method.
    If there has not yet been an assignment is this method for a variable field reference, 0 is used.
     */
    int getStatementTime();

    /**
     * @return the empty string if has not been an assignment in this method yet; otherwise the statement id
     * of the latest assignment to this variable (field, local variable, dependent variable), followed
     * by "-E" for evaluation or ":M" for merge (see Level)
     */
    String getAssignmentId();

    String getReadId();

    default boolean statementTimeIsSet() {
        return getStatementTime() != VARIABLE_FIELD_DELAY;
    }

    default boolean isRead() {
        return !getReadId().equals(NOT_YET_READ);
    }

    default boolean isAssigned() {
        return !getAssignmentId().equals(NOT_YET_ASSIGNED) && !getAssignmentId().equals(INITIAL.label);
    }

    default boolean isConfirmedVariableField() {
        return getStatementTime() >= 0;
    }

    default boolean notReadAfterAssignment() {
        boolean assigned = isAssigned();
        boolean read = isRead();
        return assigned && (!read || getReadId().compareTo(getAssignmentId()) < 0);
    }

    boolean staticallyAssignedVariablesIsSet();

    default boolean noContextNotNullDelay() {
        if (getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY) == Level.DELAY) return true;
        return getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY_RESOLVED) == Level.TRUE;
    }

    default boolean noMethodDelay() {
        if (getProperty(VariableProperty.METHOD_DELAY) == Level.DELAY) return true;
        return getProperty(VariableProperty.METHOD_DELAY_RESOLVED) == Level.TRUE;
    }

    default boolean noContextDelay(VariableProperty variableProperty) {
        return switch (variableProperty) {
            case CONTEXT_MODIFIED -> noMethodDelay();
            case CONTEXT_NOT_NULL -> noContextNotNullDelay();
            default -> throw new UnsupportedOperationException();
        };
    }
}
