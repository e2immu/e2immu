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

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnce;

import java.util.Objects;

public class VariableInfo {
    public final VariableInfo localCopyOf; // can be null, when the variable is created/first used in this block
    public final Variable variable;
    public final String name;

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

    public final SetOnce<Value> initialValue = new SetOnce<>(); // value from step 3 (initialisers)
    public final SetOnce<Value> expressionValue = new SetOnce<>(); // value from step 4 (main evaluation)
    public final SetOnce<Value> endValue = new SetOnce<>(); // value from step 9 (summary of sub-blocks)

    public final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();
    public final SetOnce<Value> stateOnAssignment = new SetOnce<>(); // EMPTY when no info, NO_VALUE when not set

    public boolean isLocalCopy() {
        return localCopyOf != null;
    }

    public boolean isNotLocalCopy() {
        return localCopyOf == null;
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow.getOrElse(ObjectFlow.NO_FLOW);
    }

    public VariableInfo(Variable variable, VariableInfo localCopyOf, String name) {
        this.localCopyOf = localCopyOf;
        this.name = Objects.requireNonNull(name);
        this.variable = Objects.requireNonNull(variable);
    }

    public Value valueForNextStatement() {
        if (endValue.isSet()) return endValue.get();
        if (expressionValue.isSet()) return expressionValue.get();
        return initialValue.getOrElse(UnknownValue.NO_VALUE);
    }

    public Value getStateOnAssignment() {
        return stateOnAssignment.getOrElse(UnknownValue.NO_VALUE);
    }

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    // simply copy from the same level
    public VariableInfo copy(boolean isLocalCopy) {
        VariableInfo variableInfo = new VariableInfo(variable, isLocalCopy ? this : null, name);
        variableInfo.properties.putAll(properties);
        variableInfo.initialValue.copy(initialValue);
        variableInfo.expressionValue.copy(expressionValue);
        variableInfo.endValue.copy(endValue);
        variableInfo.stateOnAssignment.copy(stateOnAssignment);
        return variableInfo;
    }

    // simply copy from the same level
    public VariableInfo copyValueToInitial(boolean isLocalCopy) {
        VariableInfo variableInfo = new VariableInfo(variable, isLocalCopy ? this : null, name);
        variableInfo.properties.putAll(properties);
        if (!variableInfo.initialValue.isSet()) {
            variableInfo.writeInitialValue(valueForNextStatement());
        }
        variableInfo.stateOnAssignment.copy(stateOnAssignment);
        return variableInfo;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("props=").append(properties);
        if (initialValue.isSet()) {
            sb.append(", initialValue=").append(initialValue.get());
        }
        if (expressionValue.isSet()) {
            sb.append(", expressionValue=").append(expressionValue.get());
        }
        if (endValue.isSet()) {
            sb.append(", endValue=").append(endValue.get());
        }
        return sb.toString();
    }

    public void setProperty(VariableProperty variableProperty, int value) {
        properties.put(variableProperty, value);
    }

    public void markAssigned(Value stateOnAssignment) {
        int assigned = getProperty(VariableProperty.ASSIGNED);
        setProperty(VariableProperty.ASSIGNED, Math.max(1, assigned + 1));
        if (!this.stateOnAssignment.isSet() && stateOnAssignment != UnknownValue.NO_VALUE) {
            this.stateOnAssignment.set(stateOnAssignment);
        }
    }

    public void markRead() {
        int assigned = Math.max(getProperty(VariableProperty.ASSIGNED), 0);
        int read = getProperty(VariableProperty.READ);
        int val = Math.max(1, Math.max(read + 1, assigned + 1));
        setProperty(VariableProperty.READ, val);
    }

    public void remove() {
        properties.put(VariableProperty.REMOVED, Level.TRUE);
    }

    public void setObjectFlow(ObjectFlow objectFlow) {
        this.objectFlow.set(objectFlow);
    }

    public boolean hasNoValue() {
        return valueForNextStatement() == UnknownValue.NO_VALUE;
    }

    public void writeInitialValue(Value value) {
        if (value != UnknownValue.NO_VALUE) {
            initialValue.set(value);
        }
    }
}
