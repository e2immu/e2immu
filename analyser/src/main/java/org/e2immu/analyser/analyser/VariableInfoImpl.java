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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetOnce;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;

class VariableInfoImpl implements VariableInfo {
    public final Variable variable;
    /**
     * Generally the variable's fully qualified name, but can be more complicated (see DependentVariable)
     */
    public final String name;

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

    public final SetOnce<Value> value = new SetOnce<>(); // value from step 3 (initialisers)
    public final SetOnce<Value> stateOnAssignment = new SetOnce<>(); // EMPTY when no info, NO_VALUE when not set

    public final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();
    public final SetOnce<Set<Variable>> linkedVariables = new SetOnce<>();

    VariableInfoImpl(Variable variable) {
        this.variable = Objects.requireNonNull(variable);
        this.name = variable.fullyQualifiedName();
    }

    VariableInfoImpl(VariableInfoImpl previous) {
        this.name = previous.name();
        this.variable = previous.variable();
        this.properties.copyFrom(previous.properties);
        this.value.copy(previous.value);
        this.stateOnAssignment.copy(previous.stateOnAssignment);
        this.linkedVariables.copy(previous.linkedVariables);
        this.objectFlow.copy(previous.objectFlow);
    }

    @Override
    public Stream<Map.Entry<VariableProperty, Integer>> propertyStream() {
        return properties.stream();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Variable variable() {
        return variable;
    }

    @Override
    public Set<Variable> getLinkedVariables() {
        return linkedVariables.getOrElse(null);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow.getOrElse(ObjectFlow.NO_FLOW);
    }

    @Override
    public Value getValue() {
        return value.getOrElse(UnknownValue.NO_VALUE);
    }

    @Override
    public Value getStateOnAssignment() {
        return stateOnAssignment.getOrElse(UnknownValue.NO_VALUE);
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public int getProperty(VariableProperty variableProperty, int defaultValue) {
        return properties.getOrDefault(variableProperty, defaultValue);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("name=").append(name).append("props=").append(properties);
        if (value.isSet()) {
            sb.append(", value=").append(value.get());
        }
        return sb.toString();
    }

    public void setProperty(VariableProperty variableProperty, int value) {
        properties.put(variableProperty, value);
    }

    public void remove() {
        properties.put(VariableProperty.REMOVED, Level.TRUE);
    }

    public void setObjectFlow(ObjectFlow objectFlow) {
        this.objectFlow.set(objectFlow);
    }

    @Override
    public boolean hasNoValue() {
        return getValue() == UnknownValue.NO_VALUE;
    }

    @Override
    public Map<VariableProperty, Integer> getProperties() {
        return properties.toImmutableMap();
    }

    @Override
    public VariableInfo freeze() {
        return null;
    }

    @Override
    public boolean hasProperty(VariableProperty variableProperty) {
        return properties.isSet(variableProperty);
    }

    public void writeValue(Value value) {
        if (value != UnknownValue.NO_VALUE) {
            this.value.set(value);
        }
    }

    public void ensureProperty(VariableProperty variableProperty, int value) {
        int current = properties.getOrDefault(variableProperty, Level.DELAY);
        if (value > current) properties.put(variableProperty, value);
    }


    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(VariableProperty.NOT_NULL, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.SIZE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IMMUTABLE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.CONTAINER, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.READ, Math::max, Level.DELAY),
            new MergeOp(VariableProperty.ASSIGNED, Math::max, Level.DELAY)
    );

    public void merge(VariableInfo existing,
                      boolean existingValuesWillBeOverwritten,
                      List<VariableInfo> merge) {

        Value mergedValue = mergeValue(existing, existingValuesWillBeOverwritten, merge);
        writeValue(mergedValue);

        // now common properties

        List<VariableInfo> list = existingValuesWillBeOverwritten ?
                ListUtil.immutableConcat(merge, List.of(existing)) : ImmutableList.copyOf(merge);
        for (MergeOp mergeOp : MERGE) {
            int commonValue = Level.DELAY;

            for (VariableInfo vi : list) {
                int value = vi.getProperty(mergeOp.variableProperty);
                commonValue = mergeOp.operator.applyAsInt(commonValue, value);
            }
            if (commonValue != mergeOp.initial) {
                setProperty(mergeOp.variableProperty, commonValue);
            }
        }
    }

    private Value mergeValue(VariableInfo existing,
                             boolean existingValuesWillBeOverwritten,
                             List<VariableInfo> merge) {
        Value currentValue = existing.getValue();
        if (!existingValuesWillBeOverwritten && currentValue == NO_VALUE) return NO_VALUE;
        boolean haveANoValue = merge.stream().anyMatch(v -> v.getStateOnAssignment() == null);
        if (haveANoValue) return NO_VALUE;

        // situation: we understand c
        // x=i; if(c) x=a; we do NOT overwrite; result is c?a:i
        // x=i; if(c) x=a; else x=b, overwrite; result is
        // x=i; switch(y) case c1 -> a; case c2 -> b; default -> d; overwrite

        // no need to change the value
        return currentValue;
    }

}
