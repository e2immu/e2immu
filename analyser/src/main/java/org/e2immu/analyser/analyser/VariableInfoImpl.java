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
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.*;
import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;

class VariableInfoImpl implements VariableInfo {

    private final Variable variable;
    private final String assignmentId;
    private final String readId;
    // goal, for now: from iteration 0 to iteration 1, when a field has been read, collect the statement times
    // it is too early to know if the field will be variable or nor; if variable, new local copies need
    // creating before iteration 1's evaluation starts
    // ONLY set to values in iteration 0's evaluation
    private final Set<Integer> readAtStatementTimes;

    private final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);
    private final SetOnce<Expression> value = new SetOnce<>(); // value from step 3 (initialisers)
    private final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();
    private final SetOnce<LinkedVariables> linkedVariables = new SetOnce<>();
    private final SetOnce<Integer> statementTime = new SetOnce<>();

    // ONLY for testing!
    VariableInfoImpl(Variable variable) {
        this(variable, NOT_YET_ASSIGNED, NOT_YET_READ, NOT_A_VARIABLE_FIELD, Set.of());
    }

    // used by merge code
    private VariableInfoImpl(Variable variable, String assignmentId, String readId) {
        this.variable = Objects.requireNonNull(variable);
        this.assignmentId = assignmentId;
        this.readId = readId;
        this.readAtStatementTimes = Set.of();
    }

    // normal one for creating an initial or evaluation
    VariableInfoImpl(Variable variable, String assignmentId, String readId, int statementTime, Set<Integer> readAtStatementTimes) {
        this.variable = Objects.requireNonNull(variable);
        this.assignmentId = Objects.requireNonNull(assignmentId);
        this.readId = Objects.requireNonNull(readId);
        if (statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            this.statementTime.set(statementTime);
        }
        this.readAtStatementTimes = Objects.requireNonNull(readAtStatementTimes);
    }

    @Override
    public String getAssignmentId() {
        return assignmentId;
    }

    @Override
    public String getReadId() {
        return readId;
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
        return variable.fullyQualifiedName();
    }

    @Override
    public Variable variable() {
        return variable;
    }

    @Override
    public LinkedVariables getLinkedVariables() {
        return linkedVariables.getOrElse(LinkedVariables.DELAY);
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
        sb.append("[name=").append(name()).append(", props=").append(properties);
        if (value.isSet()) {
            sb.append(", value=").append(value.get());
        }
        return sb.append("]").toString();
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

    public Set<Integer> getReadAtStatementTimes() {
        return readAtStatementTimes;
    }

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    void setProperty(VariableProperty variableProperty, int value) {
        properties.put(variableProperty, value);
    }

    void setObjectFlow(ObjectFlow objectFlow) {
        if (!this.objectFlow.isSet() || !this.objectFlow.get().equals(objectFlow)) {
            this.objectFlow.set(objectFlow);
        }
    }

    void setStatementTime(int statementTime) {
        if (!this.statementTime.isSet() || statementTime != this.statementTime.get()) {
            this.statementTime.set(statementTime);
        }
    }

    void setLinkedVariables(LinkedVariables linkedVariables) {
        assert linkedVariables != null && linkedVariables != LinkedVariables.DELAY;
        if (!linkedVariablesIsSet() || !getLinkedVariables().equals(linkedVariables)) {
            this.linkedVariables.set(linkedVariables);
        }
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

    // ***************************** MERGE RELATED CODE *********************************

    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(VariableProperty.NOT_NULL, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IMMUTABLE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.CONTAINER, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IDENTITY, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.MODIFIED, Math::max, Level.DELAY)
    );

    /*
    Merge this object and merge sources into a newly created VariableInfo object.

     */
    public VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                               Expression stateOfDestination,
                                               boolean atLeastOneBlockExecuted,
                                               Map<Expression, VariableInfo> mergeSources) {

        String mergedAssignmentId = mergedId(evaluationContext, atLeastOneBlockExecuted, getAssignmentId(),
                VariableInfo::getAssignmentId, mergeSources);
        String mergedReadId = mergedId(evaluationContext, atLeastOneBlockExecuted, getReadId(),
                VariableInfo::getReadId, mergeSources);

        VariableInfoImpl newObject = new VariableInfoImpl(variable, mergedAssignmentId, mergedReadId);
        newObject.mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, this, mergeSources);
        return newObject;
    }

    public void mergeIntoMe(EvaluationContext evaluationContext,
                            Expression stateOfDestination,
                            boolean atLeastOneBlockExecuted,
                            VariableInfoImpl previous,
                            Map<Expression, VariableInfo> mergeSources) {
        assert atLeastOneBlockExecuted || previous != this;

        Expression mergedValue = evaluationContext.replaceLocalVariables(
                previous.mergeValue(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources));
        if (mergedValue != NO_VALUE) {
            setValue(mergedValue);
        }

        mergeStatementTime(evaluationContext, atLeastOneBlockExecuted, previous.getStatementTime(), mergeSources);
        mergeProperties(atLeastOneBlockExecuted, previous, mergeSources.values());
        mergeLinkedVariables(atLeastOneBlockExecuted, previous, mergeSources);
    }

    private static String mergedId(EvaluationContext evaluationContext,
                                   boolean existingValuesWillBeOverwritten,
                                   String previousId,
                                   Function<VariableInfo, String> getter,
                                   Map<Expression, VariableInfo> merge) {
        // null current statement in tests
        String currentStatementId = (evaluationContext.getCurrentStatement() == null ? "" :
                evaluationContext.getCurrentStatement().index()) + ":M";
        boolean inSubBlocks = existingValuesWillBeOverwritten ||
                merge.values().stream().anyMatch(vi -> getter.apply(vi).compareTo(currentStatementId) > 0);
        return inSubBlocks ? currentStatementId : previousId;
    }

    /*
    Compute and set statement time into this object from the previous object and the merge sources.
     */
    private void mergeStatementTime(EvaluationContext evaluationContext,
                                    boolean existingValuesWillBeOverwritten,
                                    int previousStatementTime,
                                    Map<Expression, VariableInfo> mergeSources) {
        boolean noVariableFieldDelay = (existingValuesWillBeOverwritten || previousStatementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) &&
                mergeSources.values().stream().noneMatch(vi -> vi.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY);
        if (noVariableFieldDelay) {
            int statementTimeToSet = previousStatementTime == NOT_A_VARIABLE_FIELD ?
                    NOT_A_VARIABLE_FIELD : evaluationContext.getFinalStatementTime();
            setStatementTime(statementTimeToSet);
        }
    }


    /*
    Compute and set the linked variables
     */
    void mergeLinkedVariables(boolean existingValuesWillBeOverwritten,
                              VariableInfo existing,
                              Map<Expression, VariableInfo> merge) {
        Set<Variable> merged = new HashSet<>();
        if (!existingValuesWillBeOverwritten) {
            if (existing.linkedVariablesIsSet()) {
                merged.addAll(existing.getLinkedVariables().variables());
            } //else
            // typical situation: int a; if(x) { a = 5; }. Existing has not been assigned
            // this will end up an error when the variable is read before being assigned
        }
        for (VariableInfo vi : merge.values()) {
            if (!vi.linkedVariablesIsSet()) return;
            merged.addAll(vi.getLinkedVariables().variables());
        }
        setLinkedVariables(new LinkedVariables(merged));
    }


    /*
    Compute and set or update in this object, the properties resulting from merging previous and merge sources.
    If existingValuesWillBeOverwritten is true, the previous object is ignored.
     */
    void mergeProperties(boolean existingValuesWillBeOverwritten, VariableInfo previous, Collection<VariableInfo> mergeSources) {
        VariableInfo[] list = mergeSources
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

    public static Map<VariableProperty, Integer> mergeProperties(Map<VariableProperty, Integer> m1, Map<VariableProperty, Integer> m2) {
        if (m2.isEmpty()) return m1;
        if (m1.isEmpty()) return m2;
        ImmutableMap.Builder<VariableProperty, Integer> map = new ImmutableMap.Builder<>();
        for (MergeOp mergeOp : MERGE) {

            int v1 = m1.getOrDefault(mergeOp.variableProperty, Level.DELAY);
            int v2 = m2.getOrDefault(mergeOp.variableProperty, Level.DELAY);

            int v = mergeOp.operator.applyAsInt(v1, v2);
            if (v > Level.DELAY) {
                map.put(mergeOp.variableProperty, v);
            }
        }
        return map.build();
    }

    /*
    Compute, but do not set, the merge value between this object (the "previous") and the merge sources.
    If atLeastOneBlockExecuted is true, this object's value is ignored.
     */
    private Expression mergeValue(EvaluationContext evaluationContext,
                                  Expression stateOfDestination,
                                  boolean atLeastOneBlockExecuted,
                                  Map<Expression, VariableInfo> mergeSources) {
        Expression currentValue = getValue();
        if (!atLeastOneBlockExecuted && currentValue.isUnknown()) return currentValue;

        boolean haveANoValue = mergeSources.values().stream().anyMatch(v -> !v.valueIsSet());
        if (haveANoValue) return NO_VALUE;

        if (mergeSources.isEmpty()) {
            if (atLeastOneBlockExecuted) throw new UnsupportedOperationException();
            return currentValue;
        }

        boolean allValuesIdentical = mergeSources.values().stream().allMatch(v -> currentValue.equals(v.getValue()));
        if (allValuesIdentical) return currentValue;

        MergeHelper mergeHelper = new MergeHelper(evaluationContext, this);

        if (mergeSources.size() == 1) {
            Map.Entry<Expression, VariableInfo> e = mergeSources.entrySet().stream().findFirst().orElseThrow();
            Expression result = mergeHelper.one(e.getValue(), stateOfDestination, e.getKey());
            if (result != null) return result;
        }

        if (mergeSources.size() == 2) {
            Map.Entry<Expression, VariableInfo> e = mergeSources.entrySet().stream().findFirst().orElseThrow();
            Expression negated = Negation.negate(evaluationContext, e.getKey());
            VariableInfo vi2 = mergeSources.get(negated);
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
