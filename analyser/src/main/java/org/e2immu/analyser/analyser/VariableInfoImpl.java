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
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@E2Container
public class VariableInfoImpl implements VariableInfo {
    private final Value initialValue;
    private final Value resetValue;
    private final VariableInfo localCopyOf;
    private final Variable variable;
    private final String name;
    private final FieldReferenceState fieldReferenceState;

    private final Map<VariableProperty, Integer> properties;
    private final Value currentValue;
    private final Value stateOnAssignment; // NO_VALUE on delay, EMPTY when no info
    private final ObjectFlow objectFlow;

    private VariableInfoImpl(Value initialValue, Value resetValue, Value currentValue, Value stateOnAssignment,
                             Variable variable, VariableInfo localCopyOf,
                             String name, FieldReferenceState fieldReferenceState, ObjectFlow objectFlow,
                             Map<VariableProperty, Integer> properties) {
        this.currentValue = currentValue;
        this.fieldReferenceState = fieldReferenceState;
        this.initialValue = initialValue;
        this.localCopyOf = localCopyOf;
        this.name = name;
        this.objectFlow = objectFlow;
        this.properties = ImmutableMap.copyOf(properties);
        this.resetValue = resetValue;
        this.stateOnAssignment = stateOnAssignment;
        this.variable = variable;
    }

    @Override
    public Value getCurrentValue() {
        return currentValue;
    }

    @Override
    public Value getStateOnAssignment() {
        return stateOnAssignment;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public Value getInitialValue() {
        return initialValue;
    }

    @Override
    public Value getResetValue() {
        return resetValue;
    }

    @Override
    public VariableInfo getLocalCopyOf() {
        return localCopyOf;
    }

    @Override
    public Map<VariableProperty, Integer> properties() {
        return properties;
    }

    @Override
    public Variable getVariable() {
        return variable;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FieldReferenceState getFieldReferenceState() {
        return fieldReferenceState;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public VariableInfoImpl.Builder localCopy() {
        Builder builder = new Builder(variable, name, this, initialValue, currentValue, objectFlow, fieldReferenceState);
        builder.properties.putAll(properties);
        return builder;
    }

    @Container(builds = VariableInfoImpl.class)
    public static class Builder implements VariableInfo {
        final Value initialValue;
        final Value resetValue;
        final VariableInfo localCopyOf;
        final Variable variable;
        final String name;
        final FieldReferenceState fieldReferenceState;

        private final Map<VariableProperty, Integer> properties = new HashMap<>();
        private Value currentValue;
        private Value stateOnAssignment; // NO_VALUE on delay, EMPTY when no info
        private ObjectFlow objectFlow;

        public Builder(Variable variable, String name, VariableInfo localCopyOf, Value initialValue, Value resetValue,
                       ObjectFlow initialObjectFlow,
                       FieldReferenceState fieldReferenceState) {
            this.localCopyOf = localCopyOf;
            this.initialValue = initialValue;
            this.currentValue = resetValue;
            this.resetValue = resetValue;
            this.variable = variable;
            this.name = name; // the value used to put it in the map
            this.fieldReferenceState = fieldReferenceState;
            this.objectFlow = Objects.requireNonNull(initialObjectFlow);
        }

        public Builder(VariableInfo variableInfo) {
            this(variableInfo.getVariable(),
                    variableInfo.getName(),
                    variableInfo.getLocalCopyOf(),
                    variableInfo.getInitialValue(),
                    variableInfo.getResetValue(),
                    variableInfo.getObjectFlow(),
                    variableInfo.getFieldReferenceState());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("props=").append(properties);
            if (currentValue != null) {
                sb.append(", currentValue=").append(currentValue);
            }
            return sb.toString();
        }

        public VariableInfoImpl.Builder localCopy() {
            Builder builder = new Builder(variable, name, this, initialValue, currentValue, objectFlow, fieldReferenceState);
            builder.properties.putAll(properties);
            return builder;
        }

        VariableInfoImpl build() {
            return new VariableInfoImpl(initialValue, resetValue, currentValue,
                    stateOnAssignment, variable, localCopyOf, name, fieldReferenceState, objectFlow, properties);
        }

        @Override
        public Value getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(Value value, Value stateOnAssignment, ObjectFlow objectFlow) {
            this.currentValue = value;
            this.stateOnAssignment = stateOnAssignment;
            this.objectFlow = Objects.requireNonNull(objectFlow);
        }

        @Override
        public Value getStateOnAssignment() {
            return stateOnAssignment;
        }

        public void setStateOnAssignment(Value stateOnAssignment) {
            this.stateOnAssignment = stateOnAssignment;
        }

        @Override
        public ObjectFlow getObjectFlow() {
            return objectFlow;
        }

        public void setObjectFlow(ObjectFlow objectFlow) {
            this.objectFlow = objectFlow;
        }

        @Override
        public Value getInitialValue() {
            return initialValue;
        }

        @Override
        public Value getResetValue() {
            return resetValue;
        }

        @Override
        public VariableInfo getLocalCopyOf() {
            return localCopyOf;
        }

        @Override
        public Variable getVariable() {
            return variable;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public FieldReferenceState getFieldReferenceState() {
            return fieldReferenceState;
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        public void setProperty(VariableProperty variableProperty, int value) {
            properties.put(variableProperty, value);
        }

        @Override
        public Map<VariableProperty, Integer> properties() {
            return ImmutableMap.copyOf(properties);
        }

        public void markRead() {
            properties.remove(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT);
            int read = getProperty(VariableProperty.READ);
            setProperty(VariableProperty.READ, Level.incrementReadAssigned(read));
        }

        public void removeAfterAssignment() {
            properties.keySet().removeAll(VariableProperty.REMOVE_AFTER_ASSIGNMENT);
        }
    }
}
