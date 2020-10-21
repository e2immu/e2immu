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
import org.e2immu.analyser.model.LocalVariableReference;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnce;

import java.util.Map;
import java.util.Objects;

public class VariableInfo {
    public final Value initialValue;
    public final Value resetValue;
    public final VariableInfo localCopyOf; // can be null, when the variable is created/first used in this block
    public final Variable variable;
    public final String name;

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);


    private final SetOnce<Value> currentValue = new SetOnce<>();
    private final SetOnce<Value> stateOnAssignment = new SetOnce<>(); // EMPTY when no info
    private final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();

    public boolean isLocalCopy() {
        return localCopyOf != null;
    }

    public boolean isNotLocalCopy() {
        return localCopyOf == null;
    }

    public boolean isLocalVariableReference() {
        return variable instanceof LocalVariableReference;
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow.getOrElse(ObjectFlow.NO_FLOW);
    }

    public enum FieldReferenceState {
        SINGLE_COPY,

    }

    public VariableInfo(Variable variable, VariableInfo localCopyOf, String name, Value initialValue, Value resetValue) {
        this.initialValue = Objects.requireNonNull(initialValue);
        this.localCopyOf = localCopyOf;
        this.name = Objects.requireNonNull(name);
        this.resetValue = Objects.requireNonNull(resetValue);
        this.variable = Objects.requireNonNull(variable);
    }

    public Value getCurrentValue() {
        return currentValue.getOrElse(UnknownValue.NO_VALUE);
    }

    public Value getStateOnAssignment() {
        return stateOnAssignment.getOrElse(UnknownValue.NO_VALUE);
    }

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public VariableInfo localCopy() {
        VariableInfo variableInfo = new VariableInfo(variable, this, name, initialValue, resetValue);
        variableInfo.properties.putAll(properties);
        return variableInfo;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("props=").append(properties);
        if (currentValue.isSet()) {
            sb.append(", currentValue=").append(currentValue.get());
        }
        return sb.toString();
    }

    public void setCurrentValue(Value value, Value stateOnAssignment, ObjectFlow objectFlow) {
        assert value != UnknownValue.NO_VALUE;
        this.currentValue.set(value);
        this.stateOnAssignment.set(stateOnAssignment);
        this.objectFlow.set(objectFlow);
    }

    public void setProperty(VariableProperty variableProperty, int value) {
        properties.put(variableProperty, value);
    }

    public void markAssigned() {
        int assigned = getProperty(VariableProperty.ASSIGNED);
        setProperty(VariableProperty.ASSIGNED, Math.max(1, assigned + 1));
    }

    public void markRead() {
        int assigned = Math.max(getProperty(VariableProperty.ASSIGNED), 0);
        int read = getProperty(VariableProperty.READ);
        int val = Math.min(1, Math.max(read + 1, assigned + 1));
        setProperty(VariableProperty.READ, val);
    }

    public void removeAfterAssignment() {
        properties.put(VariableProperty.REMOVED, Level.TRUE);
    }
}
