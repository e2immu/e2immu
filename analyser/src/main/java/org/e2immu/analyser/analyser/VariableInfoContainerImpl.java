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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.Freezable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;

public class VariableInfoContainerImpl extends Freezable implements VariableInfoContainer {
    private final static Logger LOGGER = LoggerFactory.getLogger(VariableInfoContainerImpl.class);

    public static final int LEVELS = 5;

    private final VariableInfo[] data = new VariableInfo[LEVELS];
    private int currentLevel;
    private final VariableInfoContainer firstOccurrence;
    public final AddOnceSet<String> assignmentsInLoop;

    public VariableInfoContainerImpl(VariableInfo previous, VariableInfoContainer firstOccurrence) {
        Objects.requireNonNull(previous);
        data[LEVEL_0_PREVIOUS] = previous;
        currentLevel = LEVEL_0_PREVIOUS;
        Objects.requireNonNull(firstOccurrence);
        this.firstOccurrence = firstOccurrence;
        this.assignmentsInLoop = null;
    }

    public VariableInfoContainerImpl(Variable variable, String assignmentIndex, int statementTime, VariableInfoContainer firstOccurrence) {
        Objects.requireNonNull(variable);
        data[LEVEL_1_INITIALISER] = new VariableInfoImpl(variable, assignmentIndex + ":1", statementTime);
        currentLevel = LEVEL_1_INITIALISER;
        if (firstOccurrence == null) {
            this.firstOccurrence = this;
            this.assignmentsInLoop = new AddOnceSet<>();
        } else {
            this.firstOccurrence = firstOccurrence;
            this.assignmentsInLoop = null;
        }
    }

    @Override
    public VariableInfoContainer getFirstOccurrence() {
        return firstOccurrence;
    }

    @Override
    public Stream<String> streamAssignmentsInLoop() {
        assert assignmentsInLoop != null;
        return assignmentsInLoop.stream();
    }

    @Override
    public boolean assignmentsInLoopAreFrozen() {
        assert assignmentsInLoop != null;
        return assignmentsInLoop.isFrozen();
    }

    @Override
    public void setStatementTime(int level, int statementTime) {
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        variableInfo.statementTime.set(statementTime);
    }

    @Override
    public VariableInfo best(int maxLevel) {
        VariableInfo vi = null;
        int level = maxLevel;
        while (level >= 0 && (vi = data[level]) == null) {
            level--;
        }
        if (level == -1) throw new UnsupportedOperationException("Was nothing at level " + level + " or lower");
        assert vi != null;
        return vi;
    }

    @Override
    public VariableInfo current() {
        if (currentLevel == -1) throw new UnsupportedOperationException("No VariableInfo object set yet");
        return data[currentLevel];
    }

    public VariableInfo get(int level) {
        return data[level];
    }

    @Override
    public int getCurrentLevel() {
        return currentLevel;
    }

    private VariableInfoImpl getAndCast(int level) {
        return (VariableInfoImpl) data[level];
    }

    @Override
    public void freeze() {
        for (int i = 0; i < data.length; i++) {
            if (data[i] != null) {
                data[i] = data[i].freeze();
            }
        }
        super.freeze();
    }

    // should only be called when relevant
    public void freezeAssignmentsInLoop() {
        assert assignmentsInLoop != null;
        assignmentsInLoop.freeze();
    }

    @Override
    public void addAssignmentInLoop(String assignmentId) {
        assert assignmentsInLoop != null;
        if (!assignmentsInLoop.contains(assignmentId)) {
            assignmentsInLoop.add(assignmentId);
        }
    }

    /* ******************************* modifying methods related to assignment ************************************** */

    @Override
    public void prepareForValueChange(int level, String assignmentIndex, int statementTime) {
        ensureNotFrozen();
        String assignmentId = assignmentIndex + ":" + level;
        if (level <= currentLevel) {
            VariableInfo vi = data[level];
            if (vi != null) {
                // the data is already there; only update delays
                if (vi.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY &&
                        statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
                    ((VariableInfoImpl) vi).statementTime.set(statementTime);
                } else {
                    assert statementTime == vi.getStatementTime() :
                            "New statement time is " + statementTime + ", had " + vi.getStatementTime();
                    assert assignmentId.equals(vi.getAssignmentId()) :
                            "New assignment id is " + assignmentId + ", had " + vi.getAssignmentId();
                }
                return;
            }
            throw new UnsupportedOperationException("In the first iteration, an assignment should start a new level for " +
                    current().variable().fullyQualifiedName());
        }
        int assigned = data[currentLevel].getProperty(VariableProperty.ASSIGNED);
        VariableInfoImpl variableInfo = new VariableInfoImpl(data[currentLevel].variable(), assignmentId, statementTime);
        currentLevel = level;
        data[currentLevel] = variableInfo;
        variableInfo.setProperty(VariableProperty.ASSIGNED, assigned);
    }

    private VariableInfoImpl currentLevelForWriting(int level) {
        if (level <= 0 || level >= LEVELS) throw new IllegalArgumentException();
        if (level > currentLevel) throw new IllegalArgumentException();
        VariableInfoImpl variableInfo = (VariableInfoImpl) data[currentLevel];
        if (variableInfo == null) {
            throw new IllegalArgumentException("No assignment announcement was made at level " + level);
        }
        return variableInfo;
    }

    @Override
    public void setValueOnAssignment(int level, Expression value, Map<VariableProperty, Integer> propertiesToSet) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = currentLevelForWriting(level);
        if (value != NO_VALUE) {
            variableInfo.setValue(value);
        }
        propertiesToSet.forEach(variableInfo::setProperty);
    }

    @Override
    public void setValueAndStateOnAssignment(int level, Expression value, Expression state, Map<VariableProperty, Integer> propertiesToSet) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = currentLevelForWriting(level);
        if (value != NO_VALUE) {
            variableInfo.setValue(value);
        }
        if (state != NO_VALUE && (!variableInfo.stateOnAssignmentIsSet() ||
                !state.equals(variableInfo.stateOnAssignment.get()))) {
            variableInfo.stateOnAssignment.set(state);
        }
        propertiesToSet.forEach(variableInfo::setProperty);
    }


    @Override
    public void setStateOnAssignment(int level, Expression state) {
        ensureNotFrozen();
        Objects.requireNonNull(state);
        VariableInfoImpl variableInfo = currentLevelForWriting(level);
        if (state != NO_VALUE && (!variableInfo.stateOnAssignment.isSet() || !state.equals(variableInfo.stateOnAssignment.get()))) {
            variableInfo.stateOnAssignment.set(state);
        }
    }

    /* ******************************* modifying methods unrelated to assignment ************************************ */

    @Override
    public void setLinkedVariablesFromAnalyser(Set<Variable> variables) {
        internalSetLinkedVariables(LEVEL_1_INITIALISER, variables);
    }

    private void internalSetLinkedVariables(int level, Set<Variable> variables) {
        ensureNotFrozen();
        Objects.requireNonNull(variables);
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        if (!variableInfo.linkedVariables.isSet() || !variables.equals(variableInfo.linkedVariables.get())) {
            variableInfo.linkedVariables.set(variables);
            liftCurrentLevel(writeLevel);
        }
    }

    @Override
    public void setInitialValueFromAnalyser(Expression value, Map<VariableProperty, Integer> propertiesToSet) {
        internalSetValue(LEVEL_1_INITIALISER, value);
        propertiesToSet.forEach((vp, i) -> setProperty(LEVEL_1_INITIALISER, vp, i));
    }

    private void internalSetValue(int level, Expression value) {
        ensureNotFrozen();

        Objects.requireNonNull(value);
        if (value.isUnknown()) {
            throw new IllegalArgumentException("Value should not be NO_VALUE");
        }
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        if (!variableInfo.valueIsSet() || !value.equals(variableInfo.getValue())) {
            variableInfo.setValue(value);
            liftCurrentLevel(writeLevel);
        }
    }

    private void internalSetStateOnAssignment(int level, Expression state) {
        ensureNotFrozen();

        Objects.requireNonNull(state);
        if (state.isUnknown()) {
            throw new IllegalArgumentException("State should not be NO_VALUE");
        }
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        if (!variableInfo.stateOnAssignment.isSet() || !state.equals(variableInfo.stateOnAssignment.get())) {
            variableInfo.stateOnAssignment.set(state);
            liftCurrentLevel(writeLevel);
        }
    }

    private int findLevelForWriting(int level) {
        if (level <= 0 || level >= LEVELS) throw new IllegalArgumentException();

        int levelToWrite = level;
        while (levelToWrite >= 0 && data[levelToWrite] == null) {
            levelToWrite--;
        }
        if (levelToWrite < 0) throw new UnsupportedOperationException("Should not be possible");
        if (levelToWrite == 0) {
            // need to make a new copy at given level, copying from 0
            assert data[0] != null;
            VariableInfo variableInfo = new VariableInfoImpl((VariableInfoImpl) data[0]);
            levelToWrite = level;
            data[levelToWrite] = variableInfo;
        }
        return levelToWrite;
    }

    @Override
    public void setProperty(int level, VariableProperty variableProperty, int value) {
        setProperty(level, variableProperty, value, true);
    }

    @Override
    public void setProperty(int level, VariableProperty variableProperty, int value, boolean failWhenTryingToWriteALowerValue) {
        ensureNotFrozen();
        Objects.requireNonNull(variableProperty);

        int valueAtReadLevel = best(level).getProperty(variableProperty);
        if (valueAtReadLevel <= value) {
            int writeLevel = findLevelForWriting(level);
            VariableInfoImpl variableInfo = getAndCast(writeLevel);
            if (variableInfo.setProperty(variableProperty, value)) {
                liftCurrentLevel(writeLevel);
            }
        } else if (failWhenTryingToWriteALowerValue) {
            throw new UnsupportedOperationException("Trying to write a lower value " + value +
                    ", already have " + valueAtReadLevel + ", property " + variableProperty);
        }
    }

    private void liftCurrentLevel(int level) {
        if (currentLevel < level) currentLevel = level;
    }

    @Override
    public void markRead(int level) {
        ensureNotFrozen();
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        // this could be the current level, but can be lower as well; because we're doing this only in the 1st iteration
        // there should be no problems with overwriting
        int assigned = Math.max(variableInfo.getProperty(VariableProperty.ASSIGNED), 0);
        int read = variableInfo.getProperty(VariableProperty.READ);
        int val = Math.max(1, Math.max(read + 1, assigned + 1));
        if (variableInfo.setProperty(VariableProperty.READ, val)) {
            liftCurrentLevel(writeLevel);
        }
    }

    @Override
    public void setLinkedVariables(int level, Set<Variable> variables) {
        ensureNotFrozen();
        Objects.requireNonNull(variables);
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        if (!variableInfo.linkedVariables.isSet() || !variableInfo.linkedVariables.get().equals(variables)) {
            variableInfo.linkedVariables.set(variables);
            liftCurrentLevel(writeLevel);
        }
    }

    @Override
    public void setObjectFlow(int level, ObjectFlow objectFlow) {
        ensureNotFrozen();
        Objects.requireNonNull(objectFlow);
        int writeLevel = findLevelForWriting(level);
        VariableInfoImpl variableInfo = getAndCast(writeLevel);
        if (!variableInfo.objectFlow.isSet() || !variableInfo.objectFlow.get().equals(objectFlow)) {
            variableInfo.objectFlow.set(objectFlow);
            liftCurrentLevel(writeLevel);
        }
    }

    @Override
    public void copy(int level, VariableInfo previousVariableInfo, boolean failWhenTryingToWriteALowerValue, boolean copyValue) {
        previousVariableInfo.propertyStream().forEach(e -> setProperty(level, e.getKey(), e.getValue(), failWhenTryingToWriteALowerValue));

        if (copyValue && previousVariableInfo.valueIsSet()) {
            internalSetValue(level, previousVariableInfo.getValue());
        }
        if (copyValue && previousVariableInfo.stateOnAssignmentIsSet()) {
            internalSetStateOnAssignment(level, previousVariableInfo.getStateOnAssignment());
        }
        if (previousVariableInfo.linkedVariablesIsSet()) {
            internalSetLinkedVariables(level, previousVariableInfo.getLinkedVariables());
        }
        if (previousVariableInfo.getObjectFlow() != null) {
            setObjectFlow(level, previousVariableInfo.getObjectFlow());
        }
    }

    @Override
    public void merge(int level,
                      EvaluationContext evaluationContext,
                      boolean existingValuesWillBeOverwritten,
                      List<VariableInfo> merge) {
        Objects.requireNonNull(merge);
        Objects.requireNonNull(evaluationContext);

        VariableInfoImpl existing = (VariableInfoImpl) best(level - 1);
        VariableInfoImpl merged = existing.merge(evaluationContext, (VariableInfoImpl) data[level], existingValuesWillBeOverwritten, merge);
        if (merged != existing) {
            data[level] = merged;
            currentLevel = level;
        }
        Expression mergedValue = merged.getValue();

        /*
         the following condition explicitly catches a situation with merging return value objects:

         if(x) return a;
         return b;

         The first statement produces a return value of retVal1 = x?a:<return value>
         After the first statement, the state is !x. The assignment to the return value takes this into account,
         The second statement should be read as: if(!x) retVal2 = b, or retVal2 = !x?b:<return value>
         Merging the two gives the correct result, but <return value> keeps lingering, which has a nefarious effect on properties.
         */
        if (!mergedValue.isUnknown() && !existingValuesWillBeOverwritten &&
                notExistingStateEqualsAndMergeStates(evaluationContext, existing, merge)) {
            VariableProperty.VALUE_PROPERTIES.forEach(vp -> merged.setProperty(vp, mergedValue.getProperty(evaluationContext, vp)));
        } else {
            try {
                merged.mergeProperties(existingValuesWillBeOverwritten, existing, merge);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught exception while merging overwrite? {} variable: {}",
                        existingValuesWillBeOverwritten, current().variable().fullyQualifiedName());
                LOGGER.warn("Properties at different levels: ");
                for (int i = 0; i < data.length; i++) {
                    LOGGER.warn("level {}: {}", i, data[i]);
                }
                throw rte;
            }
        }

        merged.mergeLinkedVariables(existingValuesWillBeOverwritten, existing, merge);
    }

    private static boolean notExistingStateEqualsAndMergeStates(EvaluationContext evaluationContext, VariableInfo oneSide, List<VariableInfo> merge) {
        if (!oneSide.stateOnAssignmentIsSet() || oneSide.getStateOnAssignment().isBoolValueTrue()) return false;
        if (merge.stream().anyMatch(vi -> !vi.stateOnAssignmentIsSet() || vi.getStateOnAssignment().isBoolValueTrue()))
            return false;

        Expression notOne = Negation.negate(evaluationContext, oneSide.getStateOnAssignment());

        Expression andOtherSide = new And(evaluationContext.getPrimitives()).append(evaluationContext,
                merge.stream().map(VariableInfo::getStateOnAssignment).toArray(Expression[]::new));
        return notOne.equals(andOtherSide);
    }
}
