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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Container to store different versions of a VariableInfo object, one or more of this list:
 * INITIAL OR PREVIOUS: initial if first occurrence of variable
 * EVALUATION: values of the evaluation
 * MERGE: created by step 4 in the analyser
 */
public interface VariableInfoContainer {

    /* note: local variables also have a variableNature. This one can override their value
        but only when this variable nature is VariableDefinedOutsideLoop. All the others have to agree exactly,
        and can be used interchangeably.
         */

    @NotNull
    VariableNature variableNature();

    int NOT_A_FIELD = -1;
    int NOT_A_VARIABLE_FIELD = -2;
    int IN_FIELD_ANALYSER = -3;
    int NOT_RELEVANT = -4;

    // prefixes in assignment id
    // see TestLevelSuffixes to visually understand the order

    String NOT_YET_READ = "-";

    boolean hasEvaluation();

    boolean hasMerge();

    default boolean isNotAssignedInThisStatement() {
        return !hasEvaluation() ||
                getPreviousOrInitial().getAssignmentIds().compareTo(best(Stage.EVALUATION).getAssignmentIds()) >= 0;
    }

    default boolean isReadInThisStatement() {
        return hasEvaluation() && getPreviousOrInitial().getReadId().compareTo(best(Stage.EVALUATION).getReadId()) < 0;
    }

    default boolean hasBeenAccessedInThisBlock(String blockIndex) {
        VariableInfo current = current();
        String subBlock = blockIndex + ".";
        if (subBlock.compareTo(current.getReadId()) < 0) return true;
        return subBlock.compareTo(current.getAssignmentIds().getLatestAssignment()) < 0;
    }

    @Modified
    void setLinkedVariables(LinkedVariables linkedVariables, Stage level);

    @Modified
    void copyFromEvalIntoMerge(GroupPropertyValues groupPropertyValues);

    boolean isPrevious();

    boolean has(Stage level);

    @Modified
    @NotNull
    VariableInfo ensureLevelForPropertiesLinkedVariables(Location location, Stage level);

    void setDelayedValue(CausesOfDelay causesOfDelay, Stage evaluation);

    /*
    explicit freezing (DONE at the end of statement analyser): forbid any future writing
     */
    @Modified
    void freeze();

    /**
     * General method for obtaining the "most relevant" <code>VariableInfo</code> object describing the state
     * of the variable after executing this statement.
     *
     * @return a VariableInfo object, always. There would not have been a <code>VariableInfoContainer</code> if there
     * was not at least one <code>VariableInfo</code> object.
     */
    @NotNull
    VariableInfo current();

    /*
     * like current, but then with a limit
     */
    @NotNull
    VariableInfo best(Stage level);

    /**
     * Returns either the current() of the previous VIC, or the initial value if this is the first statement
     * where this variable occurs.
     */
    @NotNull
    VariableInfo getPreviousOrInitial();

    /**
     * @return if the variable was created in this statement
     */
    boolean isInitial();

    boolean isRecursivelyInitial();

    @NotNull
    VariableInfo getRecursiveInitialOrNull();

    // writing operations
    @Modified
    void setValue(Expression value,
                  LinkedVariables linkedVariables,
                  Map<Property, DV> propertiesToSet,
                  boolean initialOrEvaluation);

    // FIXME temp hack, this should become the native method
    default void setValue(Expression value,
                          LinkedVariables linkedVariables,
                          Properties propertiesToSet,
                          boolean initialOrEvaluation) {
        setValue(value, linkedVariables, propertiesToSet.toImmutableMap(), initialOrEvaluation);
    }

    @Modified
    default void setProperty(Property property, DV value, Stage level) {
        setProperty(property, value, false, level);
    }

    /**
     * set the property at the correct level
     *
     * @param property                              the property
     * @param value                                 its value
     * @param doNotFailWhenTryingToWriteALowerValue do not complain when the new value is worse (delay vs non-delay,
     *                                              lower) than the current value. Used in Enum_1 for the CONTAINER property
     *                                              which starts off FALSE on the parameter, and ends up TRUE in EVAL later
     * @param level                                 the level to write
     */
    @Modified
    void setProperty(Property property, DV value, boolean doNotFailWhenTryingToWriteALowerValue, Stage level);

    /*
    copy from one statement to the next.
    this method uses assignmentId and readId to determine which values can be copied, and which values will be set
    by the apply method in the statement analyser.
     */
    @Modified
    void copy();

    @Modified
    void copyNonContextFromPreviousOrEvalToMerge(GroupPropertyValues groupPropertyValues);

    @Modified
    CausesOfDelay copyAllFromPreviousOrEvalIntoMergeIfMergeExists();

    @Modified
    void ensureEvaluation(Location location,
                          AssignmentIds assignmentIds,
                          String readId,
                          Set<Integer> readAtStatementTimes);

    /**
     * Mostly for debugging
     *
     * @return if you access previous, do you get to EVAL or MERGE?
     */
    Stage getLevelForPrevious();

    boolean isNotRemoved();

    void remove();
}
