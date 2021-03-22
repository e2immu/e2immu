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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.SetUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface TypeAnalysis extends Analysis {

    Set<ObjectFlow> getConstantObjectFlows();

    Map<FieldInfo, Expression> getApprovedPreconditionsE1();

    Map<FieldInfo, Expression> getApprovedPreconditionsE2();

    Expression getApprovedPreconditions(boolean e2, FieldInfo fieldInfo);

    default Map<FieldInfo, Expression> getApprovedPreconditions(boolean e2) {
        return e2 ? getApprovedPreconditionsE2() : getApprovedPreconditionsE1();
    }

    boolean approvedPreconditionsIsSet(boolean e2, FieldInfo fieldInfo);

    boolean approvedPreconditionsIsFrozen(boolean e2);

    Set<FieldInfo> getEventuallyImmutableFields();


    default String markLabel() {
        return marksRequiredForImmutable().stream().map(f -> f.name).sorted().collect(Collectors.joining(","));
    }

    default boolean isEventual() {
        return !getApprovedPreconditionsE1().isEmpty() || !getApprovedPreconditionsE2().isEmpty() ||
                !getEventuallyImmutableFields().isEmpty();
    }

    /*
    if value is an eventually immutable field, then the e2 precondition on field value.t will be present
    as well. We don't want them to show up in the annotations.
     */
    default Set<FieldInfo> marksRequiredForImmutable() {
        Set<FieldInfo> res = new HashSet<>(getEventuallyImmutableFields());
        if (res.isEmpty()) {
            return SetUtil.immutableUnion(getApprovedPreconditionsE1().keySet(), getApprovedPreconditionsE2().keySet());
        }

        for (FieldInfo e1 : getApprovedPreconditionsE1().keySet()) {
            for (FieldInfo fieldInfo : res) {
                if (!isPartOf(e1, fieldInfo)) {
                    res.add(e1);
                }
            }
        }
        for (FieldInfo e2 : getApprovedPreconditionsE2().keySet()) {
            for (FieldInfo fieldInfo : res) {
                if (!isPartOf(e2, fieldInfo)) {
                    res.add(e2);
                }
            }
        }
        return res;
    }

    static boolean isPartOf(FieldInfo target, FieldInfo field) {
        if (target.equals(field)) return true;
        if (field.type.typeInfo != null && field.type.typeInfo != field.owner) {
            for (FieldInfo subField : field.type.typeInfo.typeInspection.get().fields()) {
                if (isPartOf(target, subField)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return null when not yet set
     */
    Set<ParameterizedType> getImplicitlyImmutableDataTypes();

    default int getTypeProperty(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL_EXPRESSION) return MultiLevel.EFFECTIVELY_NOT_NULL;
        return internalGetProperty(variableProperty);
    }

    /**
     * @return the labels of the aspects, pointing to the method on which the aspect has been marked
     */
    Map<String, MethodInfo> getAspects();

    /**
     * Invariants can be associated with an aspect, such as "size()>=0". They can describe the aspect and the state of fields.
     * Invariants that are not associated with an aspect must only describe the state of fields.
     *
     * @return a list of values, each of boolean return type, describing invariants.
     */
    List<Expression> getInvariants();

    default boolean aspectsIsSet(String aspect) {
        return getAspects().containsKey(aspect);
    }
}
