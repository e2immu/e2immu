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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.*;
import static org.e2immu.analyser.analyser.VariableProperty.*;

class VariableInfoImpl implements VariableInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInfoImpl.class);

    private final Variable variable;
    private final String assignmentId;
    private final String readId;
    // goal, for now: from iteration 0 to iteration 1, when a field has been read, collect the statement times
    // it is too early to know if the field will be variable or nor; if variable, new local copies need
    // creating before iteration 1's evaluation starts
    // ONLY set to values in iteration 0's evaluation
    private final Set<Integer> readAtStatementTimes;

    private final VariableProperties properties = new VariableProperties();
    private final SetOnce<Expression> value = new SetOnce<>(); // may be written exactly once
    private Expression currentDelayedValue; // may be written multiple times
    private final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();
    private final SetOnce<LinkedVariables> linkedVariables = new SetOnce<>();
    private final SetOnce<Integer> statementTime = new SetOnce<>();

    private final SetOnce<LinkedVariables> staticallyAssignedVariables = new SetOnce<>();

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
        currentDelayedValue = DelayedVariableExpression.forVariable(variable);
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
        currentDelayedValue = DelayedVariableExpression.forVariable(variable);
    }

    @Override
    public boolean valueIsSet() {
        return value.isSet();
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
        return value.getOrElse(currentDelayedValue);
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
    public VariableProperties getProperties() {
        return properties;
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

    @Override
    public LinkedVariables getStaticallyAssignedVariables() {
        return staticallyAssignedVariables.getOrElse(LinkedVariables.EMPTY);
    }

    public boolean staticallyAssignedVariablesIsSet() {
        return staticallyAssignedVariables.isSet();
    }

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    void setProperty(VariableProperty variableProperty, int value) {
        try {
            properties.put(variableProperty, value);
        } catch (RuntimeException e) {
            LOGGER.error("Error setting property {} of {} to {}", variableProperty, variable.fullyQualifiedName(), value);
            throw e;
        }
    }

    void setObjectFlow(ObjectFlow objectFlow) {
        if (!this.objectFlow.isSet() || !this.objectFlow.get().equals(objectFlow)) {
            this.objectFlow.set(objectFlow);
        }
    }

    void setStatementTime(int statementTime) {
        try {
            if (!this.statementTime.isSet() || statementTime != this.statementTime.get()) {
                this.statementTime.set(statementTime);
            }
        } catch (RuntimeException re) {
            LOGGER.error("Caught exception while setting statement time of " + variable.fullyQualifiedName());
            throw re;
        }
    }

    void setLinkedVariables(LinkedVariables linkedVariables) {
        assert linkedVariables != null && linkedVariables != LinkedVariables.DELAY;
        if (!linkedVariablesIsSet() || !getLinkedVariables().equals(linkedVariables)) {
            this.linkedVariables.set(linkedVariables);
        }
    }

    void setValue(Expression value, boolean valueIsDelayed) {
        if (value instanceof VariableExpression variableValue && variableValue.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        if (valueIsDelayed) {
            currentDelayedValue = value;
        } else {
            assert !(value instanceof DelayedExpression); // simple safe-guard, others are more difficult to check
            if (!this.value.isSet() || !this.value.get().equals(value)) { // crash if different, keep same
                this.value.set(value);
            }
        }
    }

    void setStaticallyAssignedVariables(LinkedVariables staticallyAssignedVariables) {
        assert !staticallyAssignedVariables.variables().contains(this.variable);
        if (!this.staticallyAssignedVariables.isSet() ||
                !this.staticallyAssignedVariables.get().equals(staticallyAssignedVariables)) {
            this.staticallyAssignedVariables.set(staticallyAssignedVariables);
        }
    }

    /*
    things to set for a new variable
     */
    public void newVariable() {
        setProperty(VariableProperty.CONTEXT_NOT_NULL, variable.parameterizedType().defaultNotNull());
        setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        setProperty(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED);
    }

    // ***************************** MERGE RELATED CODE *********************************

    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    private static final IntBinaryOperator MAX = (i1, i2) -> i1 == Level.DELAY || i2 == Level.DELAY
            ? Level.DELAY : Math.max(i1, i2);
    private static final IntBinaryOperator MIN = (i1, i2) -> i1 == Level.DELAY || i2 == Level.DELAY
            ? Level.DELAY : Math.min(i1, i2);

    private static final IntBinaryOperator MAX_CM = (i1, i2) ->
            i1 == Level.TRUE || i2 == Level.TRUE ? Level.TRUE :
                    i1 == Level.DELAY || i2 == Level.DELAY ? Level.DELAY : Level.FALSE;

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(SCOPE_DELAY, Math::max, Level.DELAY),
            new MergeOp(METHOD_CALLED, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_MODIFIED_DELAY, Math::max, Level.DELAY),

            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, Math::max, Level.DELAY),

            new MergeOp(NOT_NULL_EXPRESSION, MIN, NOT_NULL_EXPRESSION.best),
            new MergeOp(CONTEXT_NOT_NULL, MAX, CONTEXT_NOT_NULL.falseValue),
            new MergeOp(EXTERNAL_NOT_NULL, MIN, MultiLevel.NULLABLE),
            new MergeOp(IMMUTABLE, MIN, IMMUTABLE.best),
            new MergeOp(CONTAINER, MIN, CONTAINER.best),
            new MergeOp(IDENTITY, MIN, IDENTITY.best),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseValue),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseValue)
    );

    // TESTING ONLY!!
    VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                        Expression stateOfDestination,
                                        boolean atLeastOneBlockExecuted,
                                        List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        return mergeIntoNewObject(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources,
                new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /*
    Merge this object and merge sources into a newly created VariableInfo object.

     */
    public VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                               Expression stateOfDestination,
                                               boolean atLeastOneBlockExecuted,
                                               List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                                               Map<Variable, Integer> externalNotNull,
                                               Map<Variable, Integer> contextNotNull,
                                               Map<Variable, Integer> contextModified) {
        String mergedAssignmentId = mergedId(evaluationContext, getAssignmentId(), VariableInfo::getAssignmentId, mergeSources);
        String mergedReadId = mergedId(evaluationContext, getReadId(), VariableInfo::getReadId, mergeSources);
        VariableInfoImpl newObject = new VariableInfoImpl(variable, mergedAssignmentId, mergedReadId);
        newObject.mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, this, mergeSources,
                externalNotNull, contextNotNull, contextModified);
        return newObject;
    }

    // test only!
    void mergeIntoMe(EvaluationContext evaluationContext,
                     Expression stateOfDestination,
                     boolean atLeastOneBlockExecuted,
                     VariableInfoImpl previous,
                     List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, previous, mergeSources,
                new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /*
        We know that in each of the merge sources, the variable is either read or assigned to
     */
    public void mergeIntoMe(EvaluationContext evaluationContext,
                            Expression stateOfDestination,
                            boolean atLeastOneBlockExecuted,
                            VariableInfoImpl previous,
                            List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                            Map<Variable, Integer> externalNotNull,
                            Map<Variable, Integer> contextNotNull,
                            Map<Variable, Integer> contextModified) {
        assert atLeastOneBlockExecuted || previous != this;

        Expression mergedValue = evaluationContext.replaceLocalVariables(
                previous.mergeValue(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources));
        setValue(mergedValue, evaluationContext.isDelayed(mergedValue));

        mergeStatementTime(evaluationContext, atLeastOneBlockExecuted, previous.getStatementTime(), mergeSources);
        mergeProperties(atLeastOneBlockExecuted, previous, mergeSources, externalNotNull, contextNotNull, contextModified);
        mergeLinkedVariables(atLeastOneBlockExecuted, previous, mergeSources);
        mergeStaticallyAssignedVariables(atLeastOneBlockExecuted, previous, mergeSources);
    }

    private static String mergedId(EvaluationContext evaluationContext,
                                   String previousId,
                                   Function<VariableInfo, String> getter,
                                   List<StatementAnalysis.ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? NOT_YET_ASSIGNED :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? NOT_YET_ASSIGNED :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(StatementAnalysis.ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> getter.apply(vi).compareTo(currentStatementIdE) > 0);
        return inSubBlocks ? currentStatementIdM : previousId;
    }

    /*
    Compute and set statement time into this object from the previous object and the merge sources.
     */
    private void mergeStatementTime(EvaluationContext evaluationContext,
                                    boolean existingValuesWillBeOverwritten,
                                    int previousStatementTime,
                                    List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        boolean noVariableFieldDelay = (existingValuesWillBeOverwritten || previousStatementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) &&
                mergeSources.stream()
                        .map(StatementAnalysis.ConditionAndVariableInfo::variableInfo)
                        .noneMatch(vi -> vi.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY);
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
                              List<StatementAnalysis.ConditionAndVariableInfo> merge) {
        Set<Variable> merged = new HashSet<>();
        if (!existingValuesWillBeOverwritten) {
            if (existing.linkedVariablesIsSet()) {
                merged.addAll(existing.getLinkedVariables().variables());
            } else if (existing.isAssigned()) {
                return; // DELAY
            }
            // typical situation: int a; if(x) { a = 5; }. Existing has not been assigned
            // this will end up an error when the variable is read before being assigned
        }
        for (StatementAnalysis.ConditionAndVariableInfo cav : merge) {
            VariableInfo vi = cav.variableInfo();
            if (!vi.linkedVariablesIsSet()) return; // DELAY
            merged.addAll(vi.getLinkedVariables().variables());
        }
        setLinkedVariables(new LinkedVariables(merged));
    }


    /*
    Compute and set statically assigned variables: has NO delay!
     */
    void mergeStaticallyAssignedVariables(boolean existingValuesWillBeOverwritten,
                                          VariableInfo existing,
                                          List<StatementAnalysis.ConditionAndVariableInfo> merge) {
        Set<Variable> merged = new HashSet<>();
        if (!existingValuesWillBeOverwritten) {
            merged.addAll(existing.getStaticallyAssignedVariables().variables());
        }
        for (StatementAnalysis.ConditionAndVariableInfo cav : merge) {
            VariableInfo vi = cav.variableInfo();
            merged.addAll(vi.getStaticallyAssignedVariables().variables());
        }
        setStaticallyAssignedVariables(new LinkedVariables(merged));
    }

    // testing only!!
    void mergeProperties(boolean existingValuesWillBeOverwritten, VariableInfo previous,
                         List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        mergeProperties(existingValuesWillBeOverwritten, previous, mergeSources, new HashMap<>(),
                new HashMap<>(), new HashMap<>());
    }

    /*
    Compute and set or update in this object, the properties resulting from merging previous and merge sources.
    If existingValuesWillBeOverwritten is true, the previous object is ignored.
     */
    void mergeProperties(boolean existingValuesWillBeOverwritten, VariableInfo previous,
                         List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                         Map<Variable, Integer> externalNotNull,
                         Map<Variable, Integer> contextNotNull,
                         Map<Variable, Integer> contextModified) {
        VariableInfo[] list = mergeSources.stream()
                .map(StatementAnalysis.ConditionAndVariableInfo::variableInfo)
                .filter(vi -> vi.getValue().isComputeProperties())
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
            // important that we always write to CNN, CM, even if there is a delay
            switch (mergeOp.variableProperty) {
                case EXTERNAL_NOT_NULL -> externalNotNull.put(previous.variable(), commonValue);
                case CONTEXT_NOT_NULL -> contextNotNull.put(previous.variable(), commonValue);
                case CONTEXT_MODIFIED -> contextModified.put(previous.variable(), commonValue);
                default -> {
                    if (commonValue > Level.DELAY) {
                        setProperty(mergeOp.variableProperty, commonValue);
                    }
                }
            }
        }
    }

    public static Map<VariableProperty, Integer> mergeProperties
            (Map<VariableProperty, Integer> m1, Map<VariableProperty, Integer> m2) {
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
                                  List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        Expression currentValue = getValue();
        if (!atLeastOneBlockExecuted && currentValue.isUnknown()) return currentValue;

        if (mergeSources.isEmpty()) {
            if (atLeastOneBlockExecuted) {
                throw new UnsupportedOperationException("No merge sources for " + variable.fullyQualifiedName());
            }
            return currentValue;
        }

        // here is the correct point to remove dead branches
        List<StatementAnalysis.ConditionAndVariableInfo> reduced =
                mergeSources.stream().filter(cav -> !cav.alwaysEscapes()).collect(Collectors.toUnmodifiableList());

        boolean allValuesIdentical = reduced.stream().allMatch(cav -> currentValue.equals(cav.variableInfo().getValue()));
        if (allValuesIdentical) return currentValue;
        boolean allReducedIdentical = atLeastOneBlockExecuted && reduced.stream().skip(1).allMatch(cav -> reduced.get(0).variableInfo().getValue().equals(cav.variableInfo().getValue()));
        if (allReducedIdentical) return reduced.get(0).variableInfo().getValue();

        MergeHelper mergeHelper = new MergeHelper(evaluationContext, this);

        if (reduced.size() == 1) {
            StatementAnalysis.ConditionAndVariableInfo e = reduced.get(0);
            if (atLeastOneBlockExecuted) {
                return e.variableInfo().getValue();
            }
            Expression result = mergeHelper.one(e.variableInfo(), stateOfDestination, e.condition());
            if (result != null) return result;
        }

        if (reduced.size() == 2 && atLeastOneBlockExecuted) {
            StatementAnalysis.ConditionAndVariableInfo e = reduced.get(0);
            Expression negated = Negation.negate(evaluationContext, e.condition());
            StatementAnalysis.ConditionAndVariableInfo e2 = reduced.get(1);

            if (e2.condition().equals(negated)) {
                Expression result = mergeHelper.twoComplementary(e.variableInfo(), stateOfDestination, e.condition(), e2.variableInfo());
                if (result != null) return result;
            } else if (e2.condition().isBoolValueTrue()) {
                return e2.variableInfo().getValue();
            }
        }

        // all the rest is the territory of switch and try statements, not yet implemented

        // one thing we can already do: if the try statement ends with a 'finally', we return this value
        StatementAnalysis.ConditionAndVariableInfo eLast = reduced.get(reduced.size() - 1);
        if (eLast.condition().isBoolValueTrue()) return eLast.variableInfo().getValue();

        // no clue
        return mergeHelper.noConclusion();
    }
}
