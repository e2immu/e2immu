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

import org.e2immu.analyser.analyser.util.MergeHelper;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnce;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;

class VariableInfoImpl implements VariableInfo {

    public final Variable variable;
    /**
     * Generally the variable's fully qualified name, but can be more complicated (see DependentVariable)
     */
    public final String name;

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

    private final SetOnce<Expression> value = new SetOnce<>(); // value from step 3 (initialisers)

    public final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();
    public final SetOnce<Set<Variable>> linkedVariables = new SetOnce<>();

    public final SetOnce<Integer> statementTime = new SetOnce<>();
    public final String assignmentId;

    // ONLY for testing!
    VariableInfoImpl(Variable variable) {
        this(variable, VariableInfoContainer.START_OF_METHOD, VariableInfoContainer.NOT_A_VARIABLE_FIELD);
    }

    VariableInfoImpl(Variable variable, String assignmentId, int statementTime) {
        this.variable = Objects.requireNonNull(variable);
        this.name = variable.fullyQualifiedName();
        this.assignmentId = assignmentId;
        if (statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            this.statementTime.set(statementTime);
        }
    }

    VariableInfoImpl(VariableInfoImpl previous) {
        this.assignmentId = previous.assignmentId;
        this.name = previous.name;
        this.variable = previous.variable;
        this.properties.copyFrom(previous.properties);
        this.value.copy(previous.value);
        this.linkedVariables.copy(previous.linkedVariables);
        this.objectFlow.copy(previous.objectFlow);
        this.statementTime.copy(previous.statementTime);
    }

    @Override
    public String getAssignmentId() {
        return assignmentId;
    }

    @Override
    public int getStatementTime() {
        return statementTime.getOrElse(VariableInfoContainer.VARIABLE_FIELD_DELAY);
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
    public Expression getValue() {
        return value.getOrElse(NO_VALUE);
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
        sb.append("[name=").append(name).append(", props=").append(properties);
        if (value.isSet()) {
            sb.append(", value=").append(value.get());
        }
        return sb.append("]").toString();
    }

    public boolean setProperty(VariableProperty variableProperty, int value) {
        Integer prev = properties.put(variableProperty, value);
        return prev == null || prev < value;
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

    // we essentially compute the union
    public void mergeLinkedVariables(boolean existingValuesWillBeOverwritten, VariableInfoImpl existing,
                                     Map<Expression, VariableInfo> merge) {
        Set<Variable> merged = new HashSet<>();
        if (!existingValuesWillBeOverwritten) {
            if (existing.linkedVariablesIsSet()) {
                merged.addAll(existing.getLinkedVariables());
            } //else
            // typical situation: int a; if(x) { a = 5; }. Existing has not been assigned
            // this will end up an error when the variable is read before being assigned
        }
        for (VariableInfo vi : merge.values()) {
            if (!vi.linkedVariablesIsSet()) return;
            merged.addAll(vi.getLinkedVariables());
        }
        if (!linkedVariablesIsSet() || !getLinkedVariables().equals(merged)) {
            linkedVariables.set(merged);
        }
    }

    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(VariableProperty.NOT_NULL, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IMMUTABLE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.CONTAINER, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IDENTITY, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.MODIFIED, Math::max, Level.DELAY),
            new MergeOp(VariableProperty.READ, Math::max, Level.DELAY),
            new MergeOp(VariableProperty.ASSIGNED, Math::max, Level.DELAY)
    );

    /**
     * Merge the value of this object with the values of a list of other variables.
     * <p>
     * This method has to decide whether a new variableInfo object should be created.
     * <p>
     * As soon as there is a need for overwriting the value or the state, either the provided newObject must be used
     * (where we assume we can write) or a completely new object must be returned. This new object then starts as a copy
     * of this object.
     * <p>
     * This method takes the state of assignment into account: a delay arises when one of the states has been delayed.
     */
    public VariableInfoImpl merge(EvaluationContext evaluationContext,
                                  Expression stateOfDestination,
                                  VariableInfoImpl newObject,
                                  boolean atLeastOneBlockExecuted,
                                  Map<Expression, VariableInfo> merge) {
        Expression mergedValue = evaluationContext.replaceLocalVariables(
                mergeValue(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, merge));
        Expression currentValue = getValue();
        if (currentValue.equals(mergedValue)) {
            return newObject == null ? this : newObject; // no need to create
        }
        int mergedStatementTime = mergedStatementTime(evaluationContext);
        String assignmentId = mergedAssignmentId(evaluationContext, atLeastOneBlockExecuted, merge);

        if (newObject == null) {
            VariableInfoImpl newVi = new VariableInfoImpl(variable, assignmentId, mergedStatementTime);
            if (mergedValue != NO_VALUE) {
                newVi.setValue(mergedValue);
            }
            return newVi;
        }
        assert assignmentId.equals(newObject.assignmentId) :
                "Merged to " + assignmentId + ", newObject had " + newObject.assignmentId + " for variable " + name;

        if (mergedValue != NO_VALUE && (!newObject.value.isSet() || !newObject.value.get().equals(mergedValue))) {
            newObject.setValue(mergedValue); // will cause severe problems if the value already there is different :-)
            assert newObject.statementTime.getOrElse(VariableInfoContainer.VARIABLE_FIELD_DELAY) == mergedStatementTime;
        }
        return newObject;
    }

    private String mergedAssignmentId(EvaluationContext evaluationContext, boolean existingValuesWillBeOverwritten,
                                      Map<Expression, VariableInfo> merge) {
        // null current statement in tests
        String currentStatementId = (evaluationContext.getCurrentStatement() == null ? "" :
                evaluationContext.getCurrentStatement().index()) + ":4";
        boolean assignmentInSubBlocks = existingValuesWillBeOverwritten ||
                merge.values().stream().anyMatch(vi -> vi.getAssignmentId().compareTo(currentStatementId) > 0);
        return assignmentInSubBlocks ? currentStatementId : assignmentId;
    }

    private int mergedStatementTime(EvaluationContext evaluationContext) {
        if (statementTime.getOrElse(VariableInfoContainer.VARIABLE_FIELD_DELAY) == VariableInfoContainer.NOT_A_VARIABLE_FIELD)
            return VariableInfoContainer.NOT_A_VARIABLE_FIELD;
        return evaluationContext.getFinalStatementTime();
    }

    void setValue(Expression value) {
        if (value instanceof VariableExpression variableValue && variableValue.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        if (value == NO_VALUE) throw new UnsupportedOperationException("Cannot set NO_VALUE");
        if (!this.value.isSet() || !this.value.get().equals(value)) { // crash if different, keep same
            this.value.set(value);
        }
    }

    public void mergeProperties(boolean existingValuesWillBeOverwritten, VariableInfo previous, Collection<VariableInfo> merge) {
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
            if (commonValue != mergeOp.initial && commonValue > Level.DELAY) {
                setProperty(mergeOp.variableProperty, commonValue);
            }
        }
    }

    private Expression mergeValue(EvaluationContext evaluationContext,
                                  Expression stateOfDestination,
                                  boolean atLeastOneBlockExecuted,
                                  Map<Expression, VariableInfo> merge) {
        Expression currentValue = getValue();
        if (!atLeastOneBlockExecuted && currentValue.isUnknown()) return currentValue;

        boolean haveANoValue = merge.values().stream().anyMatch(v -> !v.valueIsSet());
        if (haveANoValue) return NO_VALUE;

        if (merge.isEmpty()) {
            if (atLeastOneBlockExecuted) throw new UnsupportedOperationException();
            return currentValue;
        }

        boolean allValuesIdentical = merge.values().stream().allMatch(v -> currentValue.equals(v.getValue()));
        if (allValuesIdentical) return currentValue;

        MergeHelper mergeHelper = new MergeHelper(evaluationContext, this);

        if (merge.size() == 1) {
            Map.Entry<Expression, VariableInfo> e = merge.entrySet().stream().findFirst().orElseThrow();
            Expression result = mergeHelper.one(e.getValue(), stateOfDestination, e.getKey());
            if (result != null) return result;
        }

        if (merge.size() == 2) {
            Map.Entry<Expression, VariableInfo> e = merge.entrySet().stream().findFirst().orElseThrow();
            Expression negated = Negation.negate(evaluationContext, e.getKey());
            VariableInfo vi2 = merge.get(negated);
            if (vi2 != null) {
                Expression result = mergeHelper.two(e.getValue(), stateOfDestination, e.getKey(), vi2);
                if (result != null) return result;
            }
        }

        // all the rest is the territory of switch statements, not yet implemented

        // no clue
        return mergeHelper.noConclusion();
    }
}
