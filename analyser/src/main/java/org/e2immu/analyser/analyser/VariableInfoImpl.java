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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.IncrementalMap;
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

    public boolean setProperty(VariableProperty variableProperty, int value) {
        Integer prev = properties.put(variableProperty, value);
        return prev == null || prev < value;
    }

    public boolean remove() {
        Integer prev = properties.put(VariableProperty.REMOVED, Level.TRUE);
        return prev == null || prev < Level.TRUE;

    }

    public void setObjectFlow(ObjectFlow objectFlow) {
        this.objectFlow.set(objectFlow);
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

    /**
     * This method has to decide whether a new variableInfo object should be created.
     * <p>
     * As soon as there is a need for overwriting the value or the state, either the provided newObject must be used
     * (where we assume we can write) or a completely new object must be returned. This new object then starts as a copy
     * of this object.
     */
    public VariableInfoImpl merge(EvaluationContext evaluationContext,
                                  VariableInfoImpl newObject,
                                  boolean existingValuesWillBeOverwritten,
                                  List<VariableInfo> merge) {
        Value mergedValue = mergeValue(evaluationContext, existingValuesWillBeOverwritten, merge);
        Value currentValue = getValue();
        if (mergedValue == NO_VALUE || currentValue.equals(mergedValue))
            return newObject == null ? this : newObject; // no need to create
        if (newObject == null) {
            if (!value.isSet()) {
                value.set(mergedValue);
                return this;
            }
            VariableInfoImpl newVi = new VariableInfoImpl(variable);
            //if (!existingValuesWillBeOverwritten) newVi.properties.putAll(properties);
            newVi.value.set(mergedValue);
            newVi.stateOnAssignment.copy(stateOnAssignment);
            return newVi;
        }
        if (!newObject.value.isSet() || !newObject.value.get().equals(mergedValue)) {
            newObject.value.set(mergedValue); // will cause severe problems if the value already there is different :-)
        }
        return newObject;
    }

    public void mergeProperties(boolean existingValuesWillBeOverwritten, VariableInfo previous, List<VariableInfo> merge) {
        VariableInfo[] list = merge
                .stream().filter(vi -> vi.getValue().isComputeProperties())
                .toArray(n -> new VariableInfo[n + 1]);
        if (!existingValuesWillBeOverwritten && getValue().isComputeProperties()) {
            list[list.length - 1] = previous;
        }
        for (MergeOp mergeOp : MERGE) {
            int commonValue = mergeOp.initial;

            for (VariableInfo vi : list) {
                if (vi != null) {
                    int value = vi.getProperty(mergeOp.variableProperty);
                    commonValue = mergeOp.operator.applyAsInt(commonValue, value);
                }
            }
            if (commonValue != mergeOp.initial) {
                setProperty(mergeOp.variableProperty, commonValue);
            }
        }
    }

    private Value mergeValue(EvaluationContext evaluationContext,
                             boolean existingValuesWillBeOverwritten,
                             List<VariableInfo> merge) {
        Value currentValue = getValue();
        if (!existingValuesWillBeOverwritten && currentValue == NO_VALUE) return NO_VALUE;
        boolean haveANoValue = merge.stream().anyMatch(v -> !v.stateOnAssignmentIsSet());
        if (haveANoValue) return NO_VALUE;
        boolean allValuesIdentical = merge.stream().allMatch(v -> currentValue.equals(v.getValue()));
        if (allValuesIdentical) return currentValue;

        if (merge.isEmpty()) {
            if (existingValuesWillBeOverwritten) throw new UnsupportedOperationException();
            return currentValue;
        }

        if (merge.size() == 1) {
            if (existingValuesWillBeOverwritten) return merge.get(0).getValue();
            Value result = oneNotOverwritten(evaluationContext, currentValue, merge.get(0));
            if (result != null) return result;
        }

        if (merge.size() == 2) {
            Value result = existingValuesWillBeOverwritten ? twoOverwritten(evaluationContext, merge.get(0), merge.get(1))
                    : two(evaluationContext, currentValue, merge.get(0), merge.get(1));
            if (result != null) return result;
        }

        boolean noneEmpty = merge.stream().noneMatch(vi -> vi.getStateOnAssignment() == UnknownValue.EMPTY);
        if (noneEmpty) {
            Variable variable = allInvolveConstantsEqualToAVariable(merge);
            if (variable != null) {
                return inlineSwitch(existingValuesWillBeOverwritten, currentValue, variable, merge);
            }
        }

        // no clue
        return new VariableValue(variable);
    }

    private Value inlineSwitch(boolean existingValuesWillBeOverwritten, Value currentValue, Variable variable, List<VariableInfo> merge) {

        // fail
        return new VariableValue(variable);
    }

    private Variable allInvolveConstantsEqualToAVariable(List<VariableInfo> merge) {

        return null; // fail
    }


    private Value oneNotOverwritten(EvaluationContext evaluationContext, Value a, VariableInfo vi) {
        Value b = vi.getValue();
        Value x = vi.getStateOnAssignment();

        // int c = a; if(x) c = b;  --> c = x?b:a
        if (x != UnknownValue.EMPTY) {
            return safe(ConditionalValue.conditionalValueConditionResolved(evaluationContext, x, b, a, ObjectFlow.NO_FLOW));
        }

        return new VariableValue(variable);
    }

    private Value two(EvaluationContext evaluationContext, Value x, VariableInfo vi1, VariableInfo vi2) {
        Value s1 = vi1.getStateOnAssignment();
        Value s2 = vi2.getStateOnAssignment();

        // silly situation, twice the same condition
        // int c = ex; if(s1) c = a; if(s1) c =b;
        if (s1.equals(s2) && s1 != UnknownValue.EMPTY) {
            return safe(ConditionalValue.conditionalValueConditionResolved(evaluationContext, s1, vi2.getValue(), x, ObjectFlow.NO_FLOW));
        }
        // int c = x; if(s1) c = a; if(s2) c = b; --> s1?a:(s2?b:x)
        if (s1 != UnknownValue.EMPTY && s2 != UnknownValue.EMPTY) {
            Value s2bx = safe(ConditionalValue.conditionalValueConditionResolved(evaluationContext, s2, vi2.getValue(), x, ObjectFlow.NO_FLOW));
            return safe(ConditionalValue.conditionalValueConditionResolved(evaluationContext, s1, vi1.getValue(), s2bx, ObjectFlow.NO_FLOW));
        }
        return new VariableValue(variable);
    }

    private Value twoOverwritten(EvaluationContext evaluationContext, VariableInfo vi1, VariableInfo vi2) {
        Value s1 = vi1.getStateOnAssignment();
        Value s2 = vi2.getStateOnAssignment();

        if (s1 != UnknownValue.EMPTY && s2 != UnknownValue.EMPTY) {
            // int c; if(s1) c = a; else c = b;
            if (NegatedValue.negate(evaluationContext, s1).equals(s2)) {
                return safe(ConditionalValue.conditionalValueConditionResolved(evaluationContext, s1, vi1.getValue(), vi2.getValue(), ObjectFlow.NO_FLOW));
            } else throw new UnsupportedOperationException("? impossible situation");
        }
        return new VariableValue(variable);
    }

    private Value safe(EvaluationResult result) {
        if (result.getModificationStream().anyMatch(m -> m instanceof StatementAnalyser.RaiseErrorMessage)) {
            // something gone wrong, retreat
            return new VariableValue(variable);
        }
        return result.value;
    }
}
