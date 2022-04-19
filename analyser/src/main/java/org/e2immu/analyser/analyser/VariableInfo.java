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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.NOT_YET_READ;

public interface VariableInfo {
    @NotNull
    String name();

    @NotNull
    Variable variable();

    /**
     * @return null when not yet set
     */
    LinkedVariables getLinkedVariables();

    default boolean linkedVariablesIsSet() {
        return !getLinkedVariables().isDelayed();
    }

    @NotNull
    Expression getValue();

    default boolean isDelayed() {
        return !valueIsSet();
    }

    @NotNull1
    Set<Integer> getReadAtStatementTimes();

    boolean valueIsSet();

    DV getProperty(Property property, DV delayValue);

    @NotNull
    DV getProperty(Property property);

    /**
     * @return immutable copy of the properties map, for debugging mostly
     */
    @NotNull
    Map<Property, DV> getProperties();

    /**
     * @return an immutable copy, or the same object frozen
     */
    @NotNull
    VariableInfo freeze();

    @NotNull1
    Stream<Map.Entry<Property, DV>> propertyStream();

    /**
     * @return the empty set if has not been an assignment in this method yet; otherwise the statement ids
     * of the latest assignment to this variable (field, local variable, dependent variable), followed
     * by "-E" for evaluation or ":M" for merge (see Level)
     * <p>
     * The last one in the tree set is the last assignment. The other ones are earlier assignments
     * which still contribute to the value, i.e., the last assignment was conditional
     */
    @NotNull
    AssignmentIds getAssignmentIds();

    @NotNull
    String getReadId();

    default boolean isRead() {
        return !getReadId().equals(NOT_YET_READ);
    }

    default boolean isAssigned() {
        return !getAssignmentIds().hasNotYetBeenAssigned();
    }

    default boolean notReadAfterAssignment(String index) {
        AssignmentIds assignmentIds = getAssignmentIds();
        if (assignmentIds.hasNotYetBeenAssigned()) return false;
        String latest = getAssignmentIds().getLatestAssignment();
        return latest.compareTo(index) < 0 // assigned before me!
                && (!isRead() || getReadId().compareTo(latest) < 0)
                && StringUtil.inSameBlock(latest, index);
    }

    // extract the value properties
    @NotNull
    Properties valueProperties();

    default boolean isAssignedAt(String index) {
        return getAssignmentIds().getLatestAssignmentIndex().equals(index);
    }


    record MergeOp(Property property, BinaryOperator<DV> operator, DV initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:


    BinaryOperator<DV> MAX_CM = (i1, i2) ->
            i1.valueIsTrue() || i2.valueIsTrue() ? DV.TRUE_DV :
                    i1.isDelayed() || i2.isDelayed() ? i1.min(i2) : DV.FALSE_DV;

    List<MergeOp> MERGE = List.of(
            new MergeOp(IN_NOT_NULL_CONTEXT, DV::min, IN_NOT_NULL_CONTEXT.bestDv),

            new MergeOp(NOT_NULL_EXPRESSION, DV::min, NOT_NULL_EXPRESSION.bestDv),
            new MergeOp(CONTEXT_NOT_NULL, DV::min, CONTEXT_NOT_NULL.falseDv),
            new MergeOp(EXTERNAL_NOT_NULL, DV::min, EXTERNAL_NOT_NULL.bestDv),

            new MergeOp(IMMUTABLE, DV::min, IMMUTABLE.bestDv),
            new MergeOp(EXTERNAL_IMMUTABLE, DV::min, EXTERNAL_IMMUTABLE.bestDv),
            new MergeOp(CONTEXT_IMMUTABLE, DV::max, CONTEXT_IMMUTABLE.falseDv),

            new MergeOp(EXTERNAL_CONTAINER, DV::min, EXTERNAL_CONTAINER.bestDv),
            new MergeOp(CONTAINER, DV::min, CONTAINER.bestDv),
            new MergeOp(CONTEXT_CONTAINER, DV::max, CONTEXT_CONTAINER.falseDv),

            new MergeOp(IDENTITY, DV::min, IDENTITY.bestDv),
            new MergeOp(IGNORE_MODIFICATIONS, DV::min, IGNORE_MODIFICATIONS.bestDv),
            new MergeOp(EXTERNAL_IGNORE_MODIFICATIONS, DV::min, EXTERNAL_IGNORE_MODIFICATIONS.bestDv),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseDv),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseDv)
    );

    // value properties: IDENTITY, IGNORE_MODIFICATIONS, IMMUTABLE, CONTAINER, NOT_NULL_EXPRESSION, INDEPENDENT
    List<MergeOp> MERGE_WITHOUT_VALUE_PROPERTIES = List.of(
            new MergeOp(IN_NOT_NULL_CONTEXT, DV::min, IN_NOT_NULL_CONTEXT.bestDv),

            new MergeOp(CONTEXT_NOT_NULL, DV::max, CONTEXT_NOT_NULL.falseDv),
            new MergeOp(EXTERNAL_NOT_NULL, DV::min, EXTERNAL_NOT_NULL.bestDv),
            new MergeOp(EXTERNAL_IMMUTABLE, DV::min, EXTERNAL_IMMUTABLE.bestDv),
            new MergeOp(CONTEXT_IMMUTABLE, DV::max, CONTEXT_IMMUTABLE.falseDv),
            new MergeOp(EXTERNAL_CONTAINER, DV::min, EXTERNAL_CONTAINER.bestDv),
            new MergeOp(CONTEXT_CONTAINER, DV::max, CONTEXT_CONTAINER.falseDv),

            new MergeOp(EXTERNAL_IGNORE_MODIFICATIONS, DV::min, EXTERNAL_IGNORE_MODIFICATIONS.bestDv),

            new MergeOp(CONTEXT_MODIFIED, MAX_CM, CONTEXT_MODIFIED.falseDv),
            new MergeOp(MODIFIED_OUTSIDE_METHOD, MAX_CM, MODIFIED_OUTSIDE_METHOD.falseDv)
    );

    // used by change data
    static Map<Property, DV> mergeIgnoreAbsent(Map<Property, DV> m1, Map<Property, DV> m2) {
        if (m2.isEmpty()) return m1;
        if (m1.isEmpty()) return m2;
        Map<Property, DV> map = new HashMap<>();
        for (MergeOp mergeOp : MERGE) {
            DV v1 = m1.getOrDefault(mergeOp.property, null);
            DV v2 = m2.getOrDefault(mergeOp.property, null);

            if (v1 == null) {
                if (v2 != null) {
                    map.put(mergeOp.property, v2);
                }
            } else {
                if (v2 == null) {
                    map.put(mergeOp.property, v1);
                } else {
                    DV v = mergeOp.operator.apply(v1, v2);
                    map.put(mergeOp.property, v);
                }
            }
        }
        return Map.copyOf(map);
    }

}
