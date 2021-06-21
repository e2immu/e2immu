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
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.util.StringUtil;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.INITIAL;
import static org.e2immu.analyser.analyser.VariableInfoContainer.*;

public interface VariableInfo {
    String name();

    Variable variable();

    /**
     * @return null when not yet set
     */
    LinkedVariables getLinkedVariables();

    default boolean linkedVariablesIsSet() {
        return getLinkedVariables() != LinkedVariables.DELAY;
    }

    Expression getValue();

    default boolean isDelayed() {
        return !valueIsSet();
    }

    default boolean isNotDelayed() {
        return valueIsSet();
    }

    Set<Integer> getReadAtStatementTimes();

    boolean valueIsSet();

    int getProperty(VariableProperty variableProperty);

    int getProperty(VariableProperty variableProperty, int defaultValue);

    /**
     * @return immutable copy of the properties map, for debugging mostly
     */
    VariableProperties getProperties();

    /**
     * @return an immutable copy, or the same object frozen
     */
    VariableInfo freeze();

    boolean hasProperty(VariableProperty variableProperty);

    Stream<Map.Entry<VariableProperty, Integer>> propertyStream();

    /*
    After the assignment a = b, the variable 'b' is statically assigned to 'a'.
    Statically indicates that the assignment is taken at the level of statements, rather than the level of values:
    if b == 3, then a == 3, and the relation between a and b is immediately lost.
    Similarly, if b == c, then at value level a == c, while at 'static level', a == b == c.

    This notion was introduced to speed up the decision on context-not-null, which is crucial for obtaining a value of a field:
    If b is a field, then its value is delayed in the first iteration; but if a comes in a non-null context, we can add that
    non-null value to b as well; see Modification_3.

    This static assignment system is the non-null equivalent for linked variables and modification.
     */
    LinkedVariables getStaticallyAssignedVariables();

    /*
    if statement time < 0, this means that statement time is irrelevant for this variable.

    Otherwise, it depends on the type of variable.
    If it is a field reference, it is variable, and this is the time of the latest assignment in this method.
    If there has not yet been an assignment is this method for a variable field reference, 0 is used.
     */
    int getStatementTime();

    /**
     * @return the empty string if has not been an assignment in this method yet; otherwise the statement id
     * of the latest assignment to this variable (field, local variable, dependent variable), followed
     * by "-E" for evaluation or ":M" for merge (see Level)
     */
    String getAssignmentId();

    String getReadId();

    default boolean statementTimeIsSet() {
        return getStatementTime() != VARIABLE_FIELD_DELAY;
    }

    default boolean isRead() {
        return !getReadId().equals(NOT_YET_READ);
    }

    default boolean isAssigned() {
        return !getAssignmentId().equals(NOT_YET_ASSIGNED) && !getAssignmentId().equals(INITIAL.label);
    }

    default boolean isConfirmedVariableField() {
        return getStatementTime() >= 0;
    }

    default boolean statementTimeDelayed() {
        return getStatementTime() == VARIABLE_FIELD_DELAY;
    }

    default boolean notReadAfterAssignment(String index) {
        return isAssigned()
                && (!isRead() || getReadId().compareTo(getAssignmentId()) < 0)
                && StringUtil.inSameBlock(getAssignmentId(), index);
    }

    boolean staticallyAssignedVariablesIsSet();

    default boolean isNotConditionalInitialization() {
        return !(variable() instanceof LocalVariableReference lvr) ||
                !(lvr.variable.nature() instanceof VariableNature.ConditionalInitialization);
    }

    default boolean isConditionalInitializationNotCreatedHere(String index) {
        if (variable() instanceof LocalVariableReference lvr &&
                lvr.variable.nature() instanceof VariableNature.ConditionalInitialization ci) {
            return !index.equals(ci.statementIndex());
        }
        return false;
    }

    default boolean isCopyOfVariableField() {
       return variable() instanceof LocalVariableReference lvr
                && lvr.variable.nature() instanceof VariableNature.CopyOfVariableField;
    }
}
