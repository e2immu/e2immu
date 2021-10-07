/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.util.MergeHelper;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_A_VARIABLE_FIELD;
import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_YET_READ;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

class VariableInfoImpl implements VariableInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInfoImpl.class);

    private final Variable variable;
    private final AssignmentIds assignmentIds;
    private final String readId;
    // goal, for now: from iteration 0 to iteration 1, when a field has been read, collect the statement times
    // it is too early to know if the field will be variable or nor; if variable, new local copies need
    // creating before iteration 1's evaluation starts
    // ONLY set to values in iteration 0's evaluation
    private final Set<Integer> readAtStatementTimes;

    private final VariableProperties properties = new VariableProperties();
    private final EventuallyFinal<Expression> value = new EventuallyFinal<>();
    private final EventuallyFinal<LinkedVariables> linkedVariables = new EventuallyFinal<>();
    private final EventuallyFinal<LinkedVariables> linked1Variables = new EventuallyFinal<>();
    private final SetOnce<Integer> statementTime = new SetOnce<>();

    private final SetOnce<LinkedVariables> staticallyAssignedVariables = new SetOnce<>();

    // ONLY for testing!
    VariableInfoImpl(Variable variable) {
        this(variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, NOT_A_VARIABLE_FIELD, Set.of(), null);
    }

    // used by merge code
    private VariableInfoImpl(Variable variable, AssignmentIds assignmentIds, String readId) {
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = assignmentIds;
        this.readId = readId;
        this.readAtStatementTimes = Set.of();
        value.setVariable(DelayedVariableExpression.forVariable(variable));
        linkedVariables.setVariable(LinkedVariables.DELAYED_EMPTY);
        linked1Variables.setVariable(LinkedVariables.DELAYED_EMPTY);
    }

    // normal one for creating an initial or evaluation
    VariableInfoImpl(Variable variable,
                     AssignmentIds assignmentIds,
                     String readId,
                     int statementTime,
                     Set<Integer> readAtStatementTimes,
                     Expression delayedValue) {
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = Objects.requireNonNull(assignmentIds);
        this.readId = Objects.requireNonNull(readId);
        if (statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            this.statementTime.set(statementTime);
        }
        this.readAtStatementTimes = Objects.requireNonNull(readAtStatementTimes);
        value.setVariable(delayedValue == null ? DelayedVariableExpression.forVariable(variable) : delayedValue);
        linkedVariables.setVariable(LinkedVariables.DELAYED_EMPTY);
        linked1Variables.setVariable(LinkedVariables.DELAYED_EMPTY);
    }

    @Override
    public boolean valueIsSet() {
        return value.isFinal();
    }

    @Override
    public AssignmentIds getAssignmentIds() {
        return assignmentIds;
    }

    @Override
    public String getReadId() {
        return readId;
    }

    @Override
    public int getStatementTime() {
        return statementTime.getOrDefault(VariableInfoContainer.VARIABLE_FIELD_DELAY);
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
        return linkedVariables.get();
    }

    @Override
    public LinkedVariables getLinked1Variables() {
        return linked1Variables.get();
    }

    @Override
    public Expression getValue() {
        return value.get();
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
        if (value.isFinal()) {
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
        return staticallyAssignedVariables.getOrDefault(LinkedVariables.EMPTY);
    }

    public boolean staticallyAssignedVariablesIsSet() {
        return staticallyAssignedVariables.isSet();
    }

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    void setProperty(VariableProperty variableProperty, int value) {
        assert !GroupPropertyValues.DELAY_PROPERTIES.contains(variableProperty) :
                "?? trying to add a delay property to a variable: " + variableProperty;
        try {
            properties.put(variableProperty, value);
        } catch (RuntimeException e) {
            LOGGER.error("Error setting property {} of {} to {}", variableProperty, variable.fullyQualifiedName(), value);
            throw e;
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
        assert linkedVariables != null;
        if (linkedVariables.isDelayed()) {
            this.linkedVariables.setVariable(linkedVariables);
        } else if (!linkedVariablesIsSet() || !getLinkedVariables().equals(linkedVariables)) {
            this.linkedVariables.setFinal(linkedVariables);
        }
    }

    void setLinked1Variables(LinkedVariables linked1Variables) {
        assert linked1Variables != null;
        if (linked1Variables.isDelayed()) {
            this.linked1Variables.setVariable(linked1Variables);
        } else if (!linked1VariablesIsSet() || !getLinked1Variables().equals(linked1Variables)) {
            this.linked1Variables.setFinal(linked1Variables);
        }
    }

    void setValue(Expression value, boolean valueIsDelayed) {
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null && ve.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        if (valueIsDelayed) {
            try {
                this.value.setVariable(value);
            } catch (IllegalStateException ise) {
                LOGGER.error("Variable {}: value '{}' is delayed, but final value '{}' already present",
                        variable.fullyQualifiedName(), value, this.value.get());
                throw ise;
            }
        } else {
            assert !(value.isInstanceOf(DelayedExpression.class)); // simple safe-guard, others are more difficult to check
            assert !(value.isInstanceOf(DelayedVariableExpression.class));

            setFinalAllowEquals(this.value, value);
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
    public void newVariable(boolean notNull) {
        setProperty(VariableProperty.CONTEXT_NOT_NULL, Math.max(notNull ? MultiLevel.EFFECTIVELY_NOT_NULL : MultiLevel.NULLABLE,
                variable.parameterizedType().defaultNotNull()));
        setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        setProperty(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED);
        setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE); // even if the variable is a primitive...
        setProperty(EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED);
    }

    // ***************************** MERGE RELATED CODE *********************************

    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    public static final IntBinaryOperator MAX = (i1, i2) -> i1 == Level.DELAY || i2 == Level.DELAY
            ? Level.DELAY : Math.max(i1, i2);
    public static final IntBinaryOperator MIN = (i1, i2) -> i1 == Level.DELAY || i2 == Level.DELAY
            ? Level.DELAY : Math.min(i1, i2);

    private static final IntBinaryOperator MAX_CM = (i1, i2) ->
            i1 == Level.TRUE || i2 == Level.TRUE ? Level.TRUE :
                    i1 == Level.DELAY || i2 == Level.DELAY ? Level.DELAY : Level.FALSE;

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(SCOPE_DELAY, Math::max, Level.DELAY),
            new MergeOp(METHOD_CALLED, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_MODIFIED_DELAY, Math::max, Level.DELAY),
            new MergeOp(PROPAGATE_MODIFICATION_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_IMMUTABLE_DELAY, Math::max, Level.DELAY),
            new MergeOp(EXTERNAL_IMMUTABLE_BREAK_DELAY, Math::max, Level.DELAY),

            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, Math::max, Level.DELAY),

            new MergeOp(NOT_NULL_EXPRESSION, MIN, NOT_NULL_EXPRESSION.best),
            new MergeOp(CONTEXT_NOT_NULL, MAX, CONTEXT_NOT_NULL.falseValue),
            new MergeOp(EXTERNAL_NOT_NULL, MIN, EXTERNAL_NOT_NULL.best),
            new MergeOp(IMMUTABLE, MIN, IMMUTABLE.best),
            new MergeOp(EXTERNAL_IMMUTABLE, MIN, EXTERNAL_IMMUTABLE.best),
            new MergeOp(CONTEXT_IMMUTABLE, MAX, CONTEXT_IMMUTABLE.falseValue),

            new MergeOp(CONTAINER, MIN, CONTAINER.best),
            new MergeOp(IDENTITY, MIN, IDENTITY.best),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseValue),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseValue)
    );

    // value properties: IDENTITY, IMMUTABLE, CONTAINER, NOT_NULL_EXPRESSION, INDEPENDENT
    private static final List<MergeOp> MERGE_WITHOUT_VALUE_PROPERTIES = List.of(
            new MergeOp(SCOPE_DELAY, Math::max, Level.DELAY),
            new MergeOp(METHOD_CALLED, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_MODIFIED_DELAY, Math::max, Level.DELAY),
            new MergeOp(PROPAGATE_MODIFICATION_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_IMMUTABLE_DELAY, Math::max, Level.DELAY),
            new MergeOp(EXTERNAL_IMMUTABLE_BREAK_DELAY, Math::max, Level.DELAY),

            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, Math::max, Level.DELAY),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, Math::max, Level.DELAY),

            new MergeOp(CONTEXT_NOT_NULL, MAX, CONTEXT_NOT_NULL.falseValue),
            new MergeOp(EXTERNAL_NOT_NULL, MIN, EXTERNAL_NOT_NULL.best),
            new MergeOp(EXTERNAL_IMMUTABLE, MIN, EXTERNAL_IMMUTABLE.best),
            new MergeOp(CONTEXT_IMMUTABLE, MAX, CONTEXT_IMMUTABLE.falseValue),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseValue),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseValue)
    );

    // TESTING ONLY!!
    VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                        Expression stateOfDestination,
                                        boolean atLeastOneBlockExecuted,
                                        List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        return mergeIntoNewObject(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources,
                new GroupPropertyValues());
    }

    /*
    Merge this object and merge sources into a newly created VariableInfo object.

     */
    public VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                               Expression stateOfDestination,
                                               boolean atLeastOneBlockExecuted,
                                               List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                                               GroupPropertyValues groupPropertyValues) {
        AssignmentIds mergedAssignmentIds = mergedAssignmentIds(evaluationContext, atLeastOneBlockExecuted,
                getAssignmentIds(), mergeSources);
        String mergedReadId = mergedReadId(evaluationContext, getReadId(), mergeSources);
        VariableInfoImpl newObject = new VariableInfoImpl(variable, mergedAssignmentIds, mergedReadId);
        newObject.mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, this, mergeSources,
                groupPropertyValues);
        return newObject;
    }

    // test only!
    void mergeIntoMe(EvaluationContext evaluationContext,
                     Expression stateOfDestination,
                     boolean atLeastOneBlockExecuted,
                     VariableInfoImpl previous,
                     List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, previous, mergeSources,
                new GroupPropertyValues());
    }

    void mergePropertiesIgnoreValue(boolean existingValuesWillBeOverwritten,
                                    VariableInfo previous,
                                    List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        mergePropertiesIgnoreValue(existingValuesWillBeOverwritten, previous, mergeSources, new GroupPropertyValues());
    }

    /*
        We know that in each of the merge sources, the variable is either read or assigned to
     */
    public void mergeIntoMe(EvaluationContext evaluationContext,
                            Expression stateOfDestination,
                            boolean atLeastOneBlockExecuted,
                            VariableInfoImpl previous,
                            List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                            GroupPropertyValues groupPropertyValues) {
        assert atLeastOneBlockExecuted || previous != this;

        Expression mergedValue = evaluationContext.replaceLocalVariables(
                previous.mergeValue(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources));

        setValue(mergedValue, evaluationContext.isDelayed(mergedValue));
        mergeStatementTime(evaluationContext, atLeastOneBlockExecuted, previous.getStatementTime(), mergeSources);
        if (!mergedValue.isDelayed(evaluationContext)) {
            setMergedValueProperties(evaluationContext, mergedValue);
        }
        mergePropertiesIgnoreValue(atLeastOneBlockExecuted, previous, mergeSources, groupPropertyValues);
        mergeLinkedVariables(atLeastOneBlockExecuted, previous, mergeSources);
        mergeLinked1Variables(atLeastOneBlockExecuted, previous, mergeSources);
        mergeStaticallyAssignedVariables(atLeastOneBlockExecuted, previous, mergeSources);
    }

    private void setMergedValueProperties(EvaluationContext evaluationContext, Expression mergedValue) {
        Map<VariableProperty, Integer> map = evaluationContext.getValueProperties(mergedValue, false);
        map.forEach(this::setProperty);
    }

    private static String mergedReadId(EvaluationContext evaluationContext,
                                       String previousId,
                                       List<StatementAnalysis.ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(StatementAnalysis.ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getReadId().compareTo(currentStatementIdE) > 0);
        return inSubBlocks ? currentStatementIdM : previousId;
    }

    private static AssignmentIds mergedAssignmentIds(EvaluationContext evaluationContext,
                                                     boolean atLeastOneBlockExecuted,
                                                     AssignmentIds previousIds,
                                                     List<StatementAnalysis.ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(StatementAnalysis.ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getAssignmentIds().getLatestAssignment().compareTo(currentStatementIdE) > 0);
        if (!inSubBlocks) return previousIds;
        Stream<AssignmentIds> sub = merge.stream().map(cav -> cav.variableInfo().getAssignmentIds());
        Stream<AssignmentIds> inclPrev = atLeastOneBlockExecuted ? sub : Stream.concat(Stream.of(previousIds), sub);
        return new AssignmentIds(currentStatementIdM, inclPrev);
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
        LinkedVariables lv;
        if (!existingValuesWillBeOverwritten) {
            lv = existing.getLinkedVariables();
        } else {
            lv = LinkedVariables.EMPTY;
        }
        for (StatementAnalysis.ConditionAndVariableInfo cav : merge) {
            VariableInfo vi = cav.variableInfo();
            lv = lv.merge(vi.getLinkedVariables());
        }
        setLinkedVariables(lv);
    }

    void mergeLinked1Variables(boolean existingValuesWillBeOverwritten,
                              VariableInfo existing,
                              List<StatementAnalysis.ConditionAndVariableInfo> merge) {
        LinkedVariables lv;
        if (!existingValuesWillBeOverwritten) {
            lv = existing.getLinked1Variables();
        } else {
            lv = LinkedVariables.EMPTY;
        }
        for (StatementAnalysis.ConditionAndVariableInfo cav : merge) {
            VariableInfo vi = cav.variableInfo();
            lv = lv.merge(vi.getLinked1Variables());
        }
        setLinked1Variables(lv);
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
        setStaticallyAssignedVariables(new LinkedVariables(merged, false));
    }

    /*
    Compute and set or update in this object, the properties resulting from merging previous and merge sources.
    If existingValuesWillBeOverwritten is true, the previous object is ignored.
     */
    void mergePropertiesIgnoreValue(boolean existingValuesWillBeOverwritten,
                                    VariableInfo previous,
                                    List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                                    GroupPropertyValues groupPropertyValues) {
        List<VariableInfo> list = mergeSources.stream()
                .map(StatementAnalysis.ConditionAndVariableInfo::variableInfo)
                .collect(Collectors.toCollection(() -> new ArrayList<>(mergeSources.size() + 1)));
        if (!existingValuesWillBeOverwritten) {
            assert previous != null;
            list.add(previous);
        }
        for (MergeOp mergeOp : MERGE_WITHOUT_VALUE_PROPERTIES) {
            int commonValue = mergeOp.initial;

            for (VariableInfo vi : list) {
                if (vi != null) {
                    int value = vi.getProperty(mergeOp.variableProperty);
                    commonValue = mergeOp.operator.applyAsInt(commonValue, value);
                }
            }
            // important that we always write to CNN, CM, even if there is a delay
            if (GroupPropertyValues.PROPERTIES.contains(mergeOp.variableProperty)) {
                groupPropertyValues.set(mergeOp.variableProperty, previous.variable(), commonValue);
            } else {
                if (commonValue > Level.DELAY) {
                    setProperty(mergeOp.variableProperty, commonValue);
                }
            }
        }
    }

    // used by change data
    public static Map<VariableProperty, Integer> mergeIgnoreAbsent
    (Map<VariableProperty, Integer> m1, Map<VariableProperty, Integer> m2) {
        if (m2.isEmpty()) return m1;
        if (m1.isEmpty()) return m2;
        Map<VariableProperty, Integer> map = new HashMap<>();
        for (MergeOp mergeOp : MERGE) {
            Integer v1 = m1.getOrDefault(mergeOp.variableProperty, null);
            Integer v2 = m2.getOrDefault(mergeOp.variableProperty, null);

            if (v1 == null) {
                if (v2 != null) {
                    map.put(mergeOp.variableProperty, v2);
                }
            } else {
                if (v2 == null) {
                    map.put(mergeOp.variableProperty, v1);
                } else {
                    int v = mergeOp.operator.applyAsInt(v1, v2);
                    if (v > Level.DELAY) {
                        map.put(mergeOp.variableProperty, v);
                    }
                }
            }
        }
        return Map.copyOf(map);
    }

    /*
    Compute, but do not set, the merge value between this object (the "previous") and the merge sources.
    If atLeastOneBlockExecuted is true, this object's value is ignored.
     */
    private Expression mergeValue(EvaluationContext evaluationContext,
                                  Expression stateOfDestination,
                                  boolean atLeastOneBlockExecuted,
                                  List<StatementAnalysis.ConditionAndVariableInfo> mergeSources) {
        Expression currentValue = getVariableValue(variable);
        if (!atLeastOneBlockExecuted && currentValue.isUnknown()) return currentValue;

        if (mergeSources.isEmpty()) {
            if (atLeastOneBlockExecuted) {
                throw new UnsupportedOperationException("No merge sources for " + variable.fullyQualifiedName());
            }
            return currentValue;
        }

        // here is the correct point to remove dead branches
        List<StatementAnalysis.ConditionAndVariableInfo> reduced =
                mergeSources.stream().filter(cav -> !cav.alwaysEscapes()).toList();

        boolean allValuesIdentical = reduced.stream().allMatch(cav ->
                currentValue.equals(cav.variableInfo().getVariableValue(variable)));
        if (allValuesIdentical) return currentValue;
        boolean allReducedIdentical = atLeastOneBlockExecuted && reduced.stream().skip(1)
                .allMatch(cav -> specialEquals(reduced.get(0).variableInfo().getVariableValue(variable),
                        cav.variableInfo().getVariableValue(variable)));
        if (allReducedIdentical) return reduced.get(0).value();

        MergeHelper mergeHelper = new MergeHelper(evaluationContext, this);

        if (reduced.size() == 1) {
            StatementAnalysis.ConditionAndVariableInfo e = reduced.get(0);
            if (atLeastOneBlockExecuted) {
                return e.value();
            }
            Expression result = mergeHelper.one(e.value(), stateOfDestination, e.condition());
            if (result != null) return result;
        }

        if (reduced.size() == 2 && atLeastOneBlockExecuted) {
            StatementAnalysis.ConditionAndVariableInfo e = reduced.get(0);
            Expression negated = Negation.negate(evaluationContext, e.condition());
            StatementAnalysis.ConditionAndVariableInfo e2 = reduced.get(1);

            if (e2.condition().equals(negated)) {
                Expression result = mergeHelper.twoComplementary(e.value(), stateOfDestination, e.condition(), e2.value());
                if (result != null) return result;
            } else if (e2.condition().isBoolValueTrue()) {
                return e2.value();
            }
        }

        // all the rest is the territory of switch and try statements, not yet implemented

        // one thing we can already do: if the try statement ends with a 'finally', we return this value
        StatementAnalysis.ConditionAndVariableInfo eLast = reduced.get(reduced.size() - 1);
        if (eLast.condition().isBoolValueTrue()) return eLast.value();

        if (reduced.stream().anyMatch(cav -> !cav.variableInfo().valueIsSet())) {
            // all are delayed, they're not all identical delayed field references.
            return mergeHelper.delayedConclusion();
        }
        // no clue

        int worstNotNull = reduced.stream().mapToInt(cav -> cav.variableInfo().getProperty(NOT_NULL_EXPRESSION))
                .min().orElseThrow();
        int worstNotNullIncludingCurrent = atLeastOneBlockExecuted ? worstNotNull :
                Math.min(worstNotNull, evaluationContext.getProperty(currentValue, NOT_NULL_EXPRESSION, false, true));
        return mergeHelper.noConclusion(worstNotNullIncludingCurrent);
    }

    /*
    there is the special situation of multiple delayed versions of the same field.
    we cannot have an idea yet if the result is equals (certainly when effectively final) or not (possibly
    when variable, with different statement times).

     */
    private boolean specialEquals(Expression e1, Expression e2) {
        if (e1 instanceof DelayedVariableExpression dve1 && e2 instanceof DelayedVariableExpression dve2 &&
                dve1.variable().equals(dve2.variable())) {
            return true;
        }
        return e1.equals(e2);
    }
}
