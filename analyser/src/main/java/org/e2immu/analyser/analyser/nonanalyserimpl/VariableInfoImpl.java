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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.util.MergeHelper;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_YET_READ;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
import static org.e2immu.analyser.util.Logger.LogTarget.EXPRESSION;
import static org.e2immu.analyser.util.Logger.log;

public class VariableInfoImpl implements VariableInfo {
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

    private final org.e2immu.analyser.analyser.Properties properties = Properties.writable();
    private final EventuallyFinal<Expression> value = new EventuallyFinal<>();

    // 20211023 needs to be frozen explicitly
    private final EventuallyFinal<LinkedVariables> linkedVariables = new EventuallyFinal<>();

    // ONLY for testing!
    public VariableInfoImpl(Variable variable) {
        this(Location.NOT_YET_SET, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), null);
    }

    // used for returning delayed values
    public VariableInfoImpl(Location location, Variable variable) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), null);
        assert location != Location.NOT_YET_SET;
    }

    // used to break initialisation delay
    public VariableInfoImpl(Location location, Variable variable, Expression value) {
        this(location, variable, AssignmentIds.NOT_YET_ASSIGNED, NOT_YET_READ, Set.of(), value);
        assert location != Location.NOT_YET_SET;
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
                     Set<Integer> readAtStatementTimes,
                     Expression delayedValue) {
        this.location = Objects.requireNonNull(location);
        this.variable = Objects.requireNonNull(variable);
        this.assignmentIds = Objects.requireNonNull(assignmentIds);
        this.readId = Objects.requireNonNull(readId);
        this.readAtStatementTimes = Objects.requireNonNull(readAtStatementTimes);
        CausesOfDelay causesOfDelay = initialValue(location, variable);
        value.setVariable(delayedValue == null ? DelayedVariableExpression.forVariable(variable, causesOfDelay) : delayedValue);
        linkedVariables.setVariable(new LinkedVariables(Map.of(), causesOfDelay));
    }

    private static CausesOfDelay initialValue(Location location, Variable variable) {
        return new SimpleSet(new VariableCause(variable, location, CauseOfDelay.Cause.INITIAL_VALUE));
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
    public Stream<Map.Entry<Property, DV>> propertyStream() {
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
    public DV getProperty(Property property, DV defaultValue) {
        if (defaultValue == null) return properties.getOrDefaultNull(property);
        return properties.getOrDefault(property, defaultValue);
    }

    @Override
    public DV getProperty(Property property) {
        DV dv = properties.getOrDefaultNull(property);
        if (dv == null) {
            return new SimpleSet(new VariableCause(variable, location, property.causeOfDelay()));
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
    public Map<Property, DV> getProperties() {
        return properties.toImmutableMap();
    }

    @Override
    public VariableInfo freeze() {
        return null;
    }

    public Set<Integer> getReadAtStatementTimes() {
        return readAtStatementTimes;
    }

    // ***************************** NON-INTERFACE CODE: SETTERS ************************

    void setProperty(Property property, DV value) {
        try {
            properties.put(property, value);
        } catch (RuntimeException e) {
            LOGGER.error("Error setting property {} of {} to {}", property, variable.fullyQualifiedName(), value);
            throw e;
        }
    }

    void setLinkedVariables(LinkedVariables linkedVariables) {
        assert linkedVariables != null;
        this.linkedVariables.setVariable(linkedVariables);
    }

    void setValue(Expression value) {
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null && ve.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        if (value.isDelayed()) {
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

            try {
                setFinalAllowEquals(this.value, value);
            } catch (IllegalStateException ise) {
                LOGGER.error("Variable {}: overwriting final value", variable.fullyQualifiedName());
                throw ise;
            }
        }
    }

    /*
    things to set for a new variable
     */
    public void newVariable(boolean notNull) {
        setProperty(Property.CONTEXT_NOT_NULL, (notNull ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV)
                .max(AnalysisProvider.defaultNotNull(variable.parameterizedType())));
        setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        setProperty(EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV);
        setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV); // even if the variable is a primitive...
        setProperty(EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED_DV);
    }

    public void ensureProperty(Property vp, DV dv) {
        DV inMap = getProperty(vp, null);
        if (inMap == null) {
            setProperty(vp, dv);
        }
    }

    // ***************************** MERGE RELATED CODE *********************************

    // TESTING ONLY!!
    VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                        Expression stateOfDestination,
                                        Expression postProcessState,
                                        boolean atLeastOneBlockExecuted,
                                        List<ConditionAndVariableInfo> mergeSources) {
        return mergeIntoNewObject(evaluationContext, stateOfDestination, postProcessState, atLeastOneBlockExecuted, mergeSources,
                new GroupPropertyValues());
    }

    /*
    Merge this object and merge sources into a newly created VariableInfo object.

     */
    public VariableInfoImpl mergeIntoNewObject(EvaluationContext evaluationContext,
                                               Expression stateOfDestination,
                                               Expression postProcessState,
                                               boolean atLeastOneBlockExecuted,
                                               List<ConditionAndVariableInfo> mergeSources,
                                               GroupPropertyValues groupPropertyValues) {
        AssignmentIds mergedAssignmentIds = mergedAssignmentIds(evaluationContext, atLeastOneBlockExecuted,
                getAssignmentIds(), mergeSources);
        String mergedReadId = mergedReadId(evaluationContext, getReadId(), mergeSources);
        VariableInfoImpl newObject = new VariableInfoImpl(evaluationContext.getLocation(),
                variable, mergedAssignmentIds, mergedReadId);
        newObject.mergeIntoMe(evaluationContext, stateOfDestination, postProcessState, atLeastOneBlockExecuted, this, mergeSources,
                groupPropertyValues);
        return newObject;
    }

    // test only!
    void mergeIntoMe(EvaluationContext evaluationContext,
                     Expression stateOfDestination,
                     Expression postProcessState,
                     boolean atLeastOneBlockExecuted,
                     VariableInfoImpl previous,
                     List<ConditionAndVariableInfo> mergeSources) {
        mergeIntoMe(evaluationContext, stateOfDestination, postProcessState, atLeastOneBlockExecuted, previous, mergeSources,
                new GroupPropertyValues());
    }

    void mergePropertiesIgnoreValue(boolean existingValuesWillBeOverwritten,
                                    VariableInfo previous,
                                    List<ConditionAndVariableInfo> mergeSources) {
        mergePropertiesIgnoreValue(existingValuesWillBeOverwritten, previous, mergeSources, new GroupPropertyValues());
    }

    /*
        We know that in each of the merge sources, the variable is either read or assigned to
     */
    public void mergeIntoMe(EvaluationContext evaluationContext,
                            Expression stateOfDestination,
                            Expression postProcessState,
                            boolean atLeastOneBlockExecuted,
                            VariableInfoImpl previous,
                            List<ConditionAndVariableInfo> mergeSources,
                            GroupPropertyValues groupPropertyValues) {
        assert atLeastOneBlockExecuted || previous != this;

        Expression mergeValue = previous.mergeValue(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources);
        Expression beforePostProcess = evaluationContext.replaceLocalVariables(mergeValue);
        Expression mergedValue = postProcess(evaluationContext, beforePostProcess, postProcessState);
        setValue(mergedValue);
        if (!mergedValue.isDelayed()) {
            setMergedValueProperties(evaluationContext, mergedValue);
        }
        mergePropertiesIgnoreValue(atLeastOneBlockExecuted, previous, mergeSources, groupPropertyValues);
        if (evaluationContext.isMyself(variable)) {
            setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV);
        }
    }

    private Expression postProcess(EvaluationContext evaluationContext,
                                   Expression beforePostProcess,
                                   Expression postProcessState) {
        if (postProcessState != null && !beforePostProcess.isDelayed() && !postProcessState.isDelayed() && !postProcessState.isBoolValueTrue()) {
            EvaluationContext child = evaluationContext.childState(postProcessState);
            Expression reEval = beforePostProcess.evaluate(child, ForwardEvaluationInfo.DEFAULT).getExpression();
            log(EXPRESSION, "Post-processed {} into {} to reflect state after block", beforePostProcess, reEval);
            return reEval;
        }
        return beforePostProcess;
    }

    private void setMergedValueProperties(EvaluationContext evaluationContext, Expression mergedValue) {
        Map<Property, DV> map = evaluationContext.getValueProperties(mergedValue, false);
        map.forEach(this::setProperty);
    }

    private static String mergedReadId(EvaluationContext evaluationContext,
                                       String previousId,
                                       List<ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getReadId().compareTo(currentStatementIdE) > 0);
        return inSubBlocks ? currentStatementIdM : previousId;
    }

    private static AssignmentIds mergedAssignmentIds(EvaluationContext evaluationContext,
                                                     boolean atLeastOneBlockExecuted,
                                                     AssignmentIds previousIds,
                                                     List<ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getAssignmentIds().getLatestAssignment().compareTo(currentStatementIdE) > 0);
        if (!inSubBlocks) return previousIds;
        Stream<AssignmentIds> sub = merge.stream().map(cav -> cav.variableInfo().getAssignmentIds());
        Stream<AssignmentIds> inclPrev = atLeastOneBlockExecuted ? sub : Stream.concat(Stream.of(previousIds), sub);
        return new AssignmentIds(currentStatementIdM, inclPrev);
    }

    /*
    Compute and set or update in this object, the properties resulting from merging previous and merge sources.
    If existingValuesWillBeOverwritten is true, the previous object is ignored.
     */
    void mergePropertiesIgnoreValue(boolean existingValuesWillBeOverwritten,
                                    VariableInfo previous,
                                    List<ConditionAndVariableInfo> mergeSources,
                                    GroupPropertyValues groupPropertyValues) {
        List<VariableInfo> list = mergeSources.stream()
                .map(ConditionAndVariableInfo::variableInfo)
                .collect(Collectors.toCollection(() -> new ArrayList<>(mergeSources.size() + 1)));
        if (!existingValuesWillBeOverwritten) {
            assert previous != null;
            list.add(previous);
        }
        for (MergeOp mergeOp : MERGE_WITHOUT_VALUE_PROPERTIES) {
            DV commonValue = mergeOp.initial();

            for (VariableInfo vi : list) {
                if (vi != null) {
                    DV value = vi.getProperty(mergeOp.property());
                    commonValue = mergeOp.operator().apply(commonValue, value);
                }
            }
            // important that we always write to CNN, CM, even if there is a delay
            if (GroupPropertyValues.PROPERTIES.contains(mergeOp.property())) {
                groupPropertyValues.set(mergeOp.property(), previous.variable(), commonValue);
            } else {
                if (commonValue.isDone()) {
                    setProperty(mergeOp.property(), commonValue);
                }
            }
        }
    }

    /*
    Compute, but do not set, the merge value between this object (the "previous") and the merge sources.
    If atLeastOneBlockExecuted is true, this object's value is ignored.
     */
    private Expression mergeValue(EvaluationContext evaluationContext,
                                  Expression stateOfDestination,
                                  boolean atLeastOneBlockExecuted,
                                  List<ConditionAndVariableInfo> mergeSources) {
        Expression currentValue = evaluationContext.getVariableValue(variable, this);
        if (!atLeastOneBlockExecuted && currentValue.isUnknown()) return currentValue;

        if (mergeSources.isEmpty()) {
            if (atLeastOneBlockExecuted) {
                throw new UnsupportedOperationException("No merge sources for " + variable.fullyQualifiedName());
            }
            return currentValue;
        }

        // here is the correct point to remove dead branches
        List<ConditionAndVariableInfo> reduced =
                mergeSources.stream().filter(cav -> !cav.alwaysEscapes()).toList();

        boolean allValuesIdentical = reduced.stream().allMatch(cav ->
                currentValue.equals(evaluationContext.getVariableValue(variable, cav.variableInfo())));
        if (allValuesIdentical) return currentValue;
        boolean allReducedIdentical = atLeastOneBlockExecuted && reduced.stream().skip(1)
                .allMatch(cav -> specialEquals(evaluationContext.getVariableValue(variable, reduced.get(0).variableInfo()),
                        evaluationContext.getVariableValue(variable, cav.variableInfo())));
        if (allReducedIdentical) return reduced.get(0).value();

        MergeHelper mergeHelper = new MergeHelper(evaluationContext, this);

        if (reduced.size() == 1) {
            ConditionAndVariableInfo e = reduced.get(0);
            if (atLeastOneBlockExecuted) {
                return e.value();
            }
            Expression result = mergeHelper.one(e.value(), stateOfDestination, e.condition());
            if (result != null) return result;
        }

        if (reduced.size() == 2 && atLeastOneBlockExecuted) {
            ConditionAndVariableInfo e = reduced.get(0);
            Expression negated = Negation.negate(evaluationContext, e.condition());
            ConditionAndVariableInfo e2 = reduced.get(1);

            if (e2.condition().equals(negated)) {
                Expression result = mergeHelper.twoComplementary(e.value(), stateOfDestination, e.condition(), e2.value());
                if (result != null) return result;
            } else if (e2.condition().isBoolValueTrue()) {
                return e2.value();
            }
        }

        // all the rest is the territory of switch and try statements, not yet implemented

        // one thing we can already do: if the try statement ends with a 'finally', we return this value
        ConditionAndVariableInfo eLast = reduced.get(reduced.size() - 1);
        if (eLast.condition().isBoolValueTrue()) return eLast.value();

        CausesOfDelay valuesDelayed = reduced.stream().map(cav -> cav.variableInfo().getValue().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (valuesDelayed.isDelayed()) {
            // all are delayed, they're not all identical delayed field references.
            return mergeHelper.delayedConclusion(valuesDelayed);
        }
        // no clue

        DV worstNotNull = reduced.stream().map(cav -> cav.variableInfo().getProperty(NOT_NULL_EXPRESSION))
                .reduce(DV.MIN_INT_DV, DV::min);
        DV worstNotNullIncludingCurrent = atLeastOneBlockExecuted ? worstNotNull :
                worstNotNull.min(evaluationContext.getProperty(currentValue, NOT_NULL_EXPRESSION, false, true));
        Map<Property, DV> valueProperties = Map.of(NOT_NULL_EXPRESSION, worstNotNullIncludingCurrent);
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
