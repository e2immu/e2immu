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
import org.e2immu.analyser.model.Location;
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
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_A_VARIABLE_FIELD;
import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_YET_READ;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

class VariableInfoImpl implements VariableInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInfoImpl.class);
    private final Location location;
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

    // 20211023 needs to be frozen explicitly
    private final EventuallyFinal<LinkedVariables> linkedVariables = new EventuallyFinal<>();
    private final SetOnce<Integer> statementTime = new SetOnce<>();

    // ONLY for testing!
    VariableInfoImpl(Variable variable) {
        this(Location.NOT_YET_SET, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, NOT_A_VARIABLE_FIELD, Set.of(), null);
    }

    // used by merge code
    private VariableInfoImpl(Location location, Variable variable, AssignmentIds assignmentIds, String readId) {
        this.location = Objects.requireNonNull(location);
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = assignmentIds;
        this.readId = readId;
        this.readAtStatementTimes = Set.of();
        CausesOfDelay causesOfDelay = initialValue(location, variable);
        value.setVariable(DelayedVariableExpression.forVariable(variable, causesOfDelay));
        linkedVariables.setVariable(new LinkedVariables(Map.of(), causesOfDelay));
    }

    // normal one for creating an initial or evaluation
    VariableInfoImpl(Location location,
                     Variable variable,
                     AssignmentIds assignmentIds,
                     String readId,
                     int statementTime,
                     Set<Integer> readAtStatementTimes,
                     Expression delayedValue) {
        this.location = Objects.requireNonNull(location);
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = Objects.requireNonNull(assignmentIds);
        this.readId = Objects.requireNonNull(readId);
        if (statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            this.statementTime.set(statementTime);
        }
        this.readAtStatementTimes = Objects.requireNonNull(readAtStatementTimes);
        CausesOfDelay causesOfDelay = initialValue(location, variable);
        value.setVariable(delayedValue == null ? DelayedVariableExpression.forVariable(variable, causesOfDelay) : delayedValue);
        linkedVariables.setVariable(new LinkedVariables(Map.of(), causesOfDelay));
    }

    private static CausesOfDelay initialValue(Location location, Variable variable) {
        return new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(variable, location, CauseOfDelay.Cause.INITIAL_VALUE));
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
    public Stream<Map.Entry<VariableProperty, DV>> propertyStream() {
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
    public Expression getValue() {
        return value.get();
    }

    @Override
    public DV getProperty(VariableProperty variableProperty, DV defaultValue) {
        return properties.getOrDefault(variableProperty, defaultValue);
    }

    @Override
    public DV getProperty(VariableProperty variableProperty) {
        DV dv = properties.getOrDefault(variableProperty, null);
        if(dv == null) {
            return new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(variable, location, CauseOfDelay.Cause.from(variableProperty)));
        }
        return dv;
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

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    void setProperty(VariableProperty variableProperty, DV value) {
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
        this.linkedVariables.setVariable(linkedVariables);
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

    /*
    things to set for a new variable
     */
    public void newVariable(boolean notNull) {
        setProperty(VariableProperty.CONTEXT_NOT_NULL, (notNull ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV)
                .max(variable.parameterizedType().defaultNotNull()));
        setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE_DV);
        setProperty(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV);
        setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV); // even if the variable is a primitive...
        setProperty(EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED_DV);
    }

    public void ensureProperty(VariableProperty vp, DV dv) {
        DV inMap = getProperty(vp, null);
        if (inMap == null) {
            setProperty(vp, dv);
        }
    }

    // ***************************** MERGE RELATED CODE *********************************

    private record MergeOp(VariableProperty variableProperty, BinaryOperator<DV> operator, DV initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:


    private static final BinaryOperator<DV> MAX_CM = (i1, i2) ->
            i1.valueIsTrue() || i2.valueIsTrue() ? Level.TRUE_DV :
                    i1.isDelayed() || i2.isDelayed() ? i1.min(i2) : Level.FALSE_DV;

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(CONTEXT_NOT_NULL_DELAY, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),

            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),

            new MergeOp(NOT_NULL_EXPRESSION, DV::min, NOT_NULL_EXPRESSION.bestDv),
            new MergeOp(CONTEXT_NOT_NULL, DV::min, CONTEXT_NOT_NULL.falseDv),
            new MergeOp(EXTERNAL_NOT_NULL, DV::min, EXTERNAL_NOT_NULL.bestDv),
            new MergeOp(IMMUTABLE, DV::min, IMMUTABLE.bestDv),
            new MergeOp(EXTERNAL_IMMUTABLE, DV::min, EXTERNAL_IMMUTABLE.bestDv),
            new MergeOp(CONTEXT_IMMUTABLE, DV::max, CONTEXT_IMMUTABLE.falseDv),

            new MergeOp(CONTAINER, DV::min, CONTAINER.bestDv),
            new MergeOp(IDENTITY, DV::min, IDENTITY.bestDv),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseDv),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseDv)
    );

    // value properties: IDENTITY, IMMUTABLE, CONTAINER, NOT_NULL_EXPRESSION, INDEPENDENT
    private static final List<MergeOp> MERGE_WITHOUT_VALUE_PROPERTIES = List.of(
            new MergeOp(CONTEXT_NOT_NULL_DELAY, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),

            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),
            new MergeOp(CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED, DV::maxIgnoreDelay, Level.NOT_INVOLVED_DV),

            new MergeOp(CONTEXT_NOT_NULL, DV::max, CONTEXT_NOT_NULL.falseDv),
            new MergeOp(EXTERNAL_NOT_NULL, DV::min, EXTERNAL_NOT_NULL.bestDv),
            new MergeOp(EXTERNAL_IMMUTABLE, DV::min, EXTERNAL_IMMUTABLE.bestDv),
            new MergeOp(CONTEXT_IMMUTABLE, DV::max, CONTEXT_IMMUTABLE.falseDv),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseDv),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseDv)
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
        VariableInfoImpl newObject = new VariableInfoImpl(evaluationContext.getLocation(),
                variable, mergedAssignmentIds, mergedReadId);
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

        setValue(mergedValue, mergedValue.isDelayed());
        mergeStatementTime(evaluationContext, atLeastOneBlockExecuted, previous.getStatementTime(), mergeSources);
        if (!mergedValue.isDelayed()) {
            setMergedValueProperties(evaluationContext, mergedValue);
        }
        mergePropertiesIgnoreValue(atLeastOneBlockExecuted, previous, mergeSources, groupPropertyValues);
        if (evaluationContext.isMyself(variable)) {
            setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV);
        }
    }

    private void setMergedValueProperties(EvaluationContext evaluationContext, Expression mergedValue) {
        Map<VariableProperty, DV> map = evaluationContext.getValueProperties(mergedValue, false);
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
            DV commonValue = mergeOp.initial;

            for (VariableInfo vi : list) {
                if (vi != null) {
                    DV value = vi.getProperty(mergeOp.variableProperty);
                    commonValue = mergeOp.operator.apply(commonValue, value);
                }
            }
            // important that we always write to CNN, CM, even if there is a delay
            if (GroupPropertyValues.PROPERTIES.contains(mergeOp.variableProperty)) {
                groupPropertyValues.set(mergeOp.variableProperty, previous.variable(), commonValue);
            } else {
                if (commonValue.isDone()) {
                    setProperty(mergeOp.variableProperty, commonValue);
                }
            }
        }
    }

    // used by change data
    public static Map<VariableProperty, DV> mergeIgnoreAbsent(Map<VariableProperty, DV> m1, Map<VariableProperty, DV> m2) {
        if (m2.isEmpty()) return m1;
        if (m1.isEmpty()) return m2;
        Map<VariableProperty, DV> map = new HashMap<>();
        for (MergeOp mergeOp : MERGE) {
            DV v1 = m1.getOrDefault(mergeOp.variableProperty, null);
            DV v2 = m2.getOrDefault(mergeOp.variableProperty, null);

            if (v1 == null) {
                if (v2 != null) {
                    map.put(mergeOp.variableProperty, v2);
                }
            } else {
                if (v2 == null) {
                    map.put(mergeOp.variableProperty, v1);
                } else {
                    DV v = mergeOp.operator.apply(v1, v2);
                    if (v.isDelayed()) {
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

        DV worstNotNull = reduced.stream().map(cav -> cav.variableInfo().getProperty(NOT_NULL_EXPRESSION))
                .min().orElseThrow();
        DV worstNotNullIncludingCurrent = atLeastOneBlockExecuted ? worstNotNull :
                worstNotNull.min(evaluationContext.getProperty(currentValue, NOT_NULL_EXPRESSION, false, true));
        Map<VariableProperty, DV> valueProperties = Map.of(NOT_NULL_EXPRESSION, worstNotNullIncludingCurrent);
        // FIXME
        return mergeHelper.noConclusion(valueProperties);
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
