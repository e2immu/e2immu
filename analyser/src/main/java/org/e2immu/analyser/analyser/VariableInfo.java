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
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.util.StringUtil;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_YET_READ;
import static org.e2immu.analyser.analyser.VariableInfoContainer.VARIABLE_FIELD_DELAY;

public interface VariableInfo {
    String name();

    Variable variable();

    /**
     * @return null when not yet set
     */
    LinkedVariables getLinkedVariables();

    default boolean linkedVariablesIsSet() {
        return !getLinkedVariables().isDelayed();
    }


    /**
     * @return null when not yet set
     */
    LinkedVariables getLinked1Variables();

    default boolean linked1VariablesIsSet() {
        return !getLinked1Variables().isDelayed();
    }

    Expression getValue();

    default Expression getVariableValue(Variable myself) {
        Expression value = getValue();
        Variable v = variable();
        if (!v.equals(myself) && value.isInstanceOf(NewObject.class)) {
            return new VariableExpression(v);
        }
        return value;
    }

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
     * @return the empty set if has not been an assignment in this method yet; otherwise the statement ids
     * of the latest assignment to this variable (field, local variable, dependent variable), followed
     * by "-E" for evaluation or ":M" for merge (see Level)
     * <p>
     * The last one in the tree set is the last assignment. The other ones are earlier assignments
     * which still contribute to the value, i.e., the last assignment was conditional
     */
    AssignmentIds getAssignmentIds();

    String getReadId();

    default boolean statementTimeIsSet() {
        return getStatementTime() != VARIABLE_FIELD_DELAY;
    }

    default boolean isRead() {
        return !getReadId().equals(NOT_YET_READ);
    }

    default boolean isAssigned() {
        return !getAssignmentIds().hasNotYetBeenAssigned();
    }

    default boolean isConfirmedVariableField() {
        return getStatementTime() >= 0;
    }

    default boolean statementTimeDelayed() {
        return getStatementTime() == VARIABLE_FIELD_DELAY;
    }

    default boolean notReadAfterAssignment(String index) {
        AssignmentIds assignmentIds = getAssignmentIds();
        if (assignmentIds.hasNotYetBeenAssigned()) return false;
        String latest = getAssignmentIds().getLatestAssignment();
        return latest.compareTo(index) < 0 // assigned before me!
                && (!isRead() || getReadId().compareTo(latest) < 0)
                && StringUtil.inSameBlock(latest, index);
    }

    boolean staticallyAssignedVariablesIsSet();

    default boolean isNotConditionalInitialization() {
        return !(variable() instanceof LocalVariableReference lvr) ||
                !(lvr.variable.nature() instanceof VariableNature.ConditionalInitialization);
    }
}
