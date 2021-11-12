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
import org.e2immu.annotation.NotNull;

import java.util.List;
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
    VariableNature variableNature();

    int VARIABLE_FIELD_DELAY = -1;
    int NOT_A_VARIABLE_FIELD = -2;

    // prefixes in assignment id
    // see TestLevelSuffixes to visually understand the order

    String NOT_YET_READ = "-";

    boolean hasEvaluation();

    boolean hasMerge();

    default boolean isNotAssignedInThisStatement() {
        return !hasEvaluation() ||
                getPreviousOrInitial().getAssignmentIds().compareTo(best(Level.EVALUATION).getAssignmentIds()) >= 0;
    }

    default boolean isReadInThisStatement() {
        return hasEvaluation() && getPreviousOrInitial().getReadId().compareTo(best(Level.EVALUATION).getReadId()) < 0;
    }

    void setLinkedVariables(LinkedVariables linkedVariables, Level level);

    void copyFromEvalIntoMerge(GroupPropertyValues groupPropertyValues);

    void newVariableWithoutValue();

    boolean isPrevious();

    boolean has(Level level);

    VariableInfo ensureLevelForPropertiesLinkedVariables(Location location, Level level);

    // suffixes in assignment id; these act as the 3 levels for setProperty
    enum Level {
        INITIAL("-C"), // C for creation, but essentially, it should be < E
        EVALUATION("-E"), // the - comes before the digits
        MERGE(":M"); // the : comes after the digits
        public final String label;

        Level(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /*
    explicit freezing (DONE at the end of statement analyser): forbid any future writing
     */
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
    VariableInfo best(Level level);

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

    // writing operations
    void setValue(Expression value,
                  LinkedVariables linkedVariables,
                  Map<VariableProperty, DV> propertiesToSet,
                  boolean initialOrEvaluation);

    default void setProperty(VariableProperty variableProperty, DV value, Level level) {
        setProperty(variableProperty, value, true, level);
    }

    void setProperty(VariableProperty variableProperty, DV value, boolean failWhenTryingToWriteALowerValue, Level level);

    /*
    copy from one statement to the next.
    this method uses assignmentId and readId to determine which values can be copied, and which values will by set
    by the apply method in the statement analyser.
     */
    void copy();

    void ensureEvaluation(Location location,
                          AssignmentIds assignmentIds,
                          String readId, int statementTime, Set<Integer> readAtStatementTimes);

    Expression merge(EvaluationContext evaluationContext,
                     Expression stateOfDestination,
                     boolean atLeastOneBlockExecuted,
                     List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                     GroupPropertyValues groupPropertyValues);

    /*
    Statement time is irrelevant for all but variable fields.
    The correct value comes in a later iteration; it is set in StatementAnalysis based on information
    from the field analyser.

     */
    void setStatementTime(int statementTime);
}
