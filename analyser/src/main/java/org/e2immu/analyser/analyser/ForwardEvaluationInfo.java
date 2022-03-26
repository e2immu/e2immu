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

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.SetUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public record ForwardEvaluationInfo(Map<Property, DV> properties,
                                    boolean doNotReevaluateVariableExpressions,
                                    boolean notAssignmentTarget,
                                    Variable assignmentTarget,
                                    boolean complainInlineConditional,
                                    Set<MethodInfo> inlining) {

    public ForwardEvaluationInfo(Map<Property, DV> properties,
                                 boolean doNotReevaluateVariableExpressions,
                                 boolean notAssignmentTarget,
                                 Variable assignmentTarget,
                                 boolean complainInlineConditional,
                                 Set<MethodInfo> inlining) {
        this.properties = Map.copyOf(properties);
        this.notAssignmentTarget = notAssignmentTarget;
        this.assignmentTarget = assignmentTarget;
        this.complainInlineConditional = complainInlineConditional;
        this.doNotReevaluateVariableExpressions = doNotReevaluateVariableExpressions;
        this.inlining = inlining;
    }

    public boolean assignToField() {
        return assignmentTarget instanceof FieldReference;
    }

    public DV getProperty(Property property) {
        return properties.getOrDefault(property, property.falseDv);
    }

    public boolean isNotAssignmentTarget() {
        return notAssignmentTarget;
    }

    public boolean isAssignmentTarget() {
        return !notAssignmentTarget;
    }

    public String toString() {
        return new StringJoiner(", ", ForwardEvaluationInfo.class.getSimpleName() + "[", "]")
                .add("notAssignmentTarget=" + notAssignmentTarget)
                .add("properties=" + properties)
                .toString();
    }

    public static ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfo(
            Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV), false, true, null, true,
            Set.of());

    public static ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfo(
            Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV), false,
            false, null, true, Set.of());

    public static ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfo(
            Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV), false,
            true, null, true, Set.of());

    public ForwardEvaluationInfo copyDefault() {
        return new ForwardEvaluationInfo(Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV),
                doNotReevaluateVariableExpressions, true, assignmentTarget, complainInlineConditional,
                inlining);
    }

    public ForwardEvaluationInfo notNullNotAssignment() {
        return new ForwardEvaluationInfo(
                Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV), doNotReevaluateVariableExpressions,
                true, assignmentTarget, complainInlineConditional, inlining);
    }

    public ForwardEvaluationInfo notNullKeepAssignment() {
        return new ForwardEvaluationInfo(
                Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV), doNotReevaluateVariableExpressions,
                notAssignmentTarget, assignmentTarget, complainInlineConditional, inlining);
    }

    public ForwardEvaluationInfo copyModificationEnsureNotNull() {
        Map<Property, DV> map = new HashMap<>();
        map.put(Property.CONTEXT_MODIFIED,
                properties.getOrDefault(Property.CONTEXT_MODIFIED, DV.FALSE_DV));
        map.put(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        return new ForwardEvaluationInfo(map, doNotReevaluateVariableExpressions, true,
                assignmentTarget, complainInlineConditional, inlining);
    }

    public ForwardEvaluationInfo copyAddAssignmentTarget(Variable variable) {
        return new ForwardEvaluationInfo(properties, doNotReevaluateVariableExpressions,
                notAssignmentTarget, variable, complainInlineConditional, inlining);
    }

    public ForwardEvaluationInfo copyAddAssignmentTargetEnsureNotNull(Variable variable) {
        Map<Property, DV> map = new HashMap<>(properties);
        map.merge(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV, DV::max);
        return new ForwardEvaluationInfo(map, doNotReevaluateVariableExpressions,
                notAssignmentTarget, variable, complainInlineConditional, inlining);
    }

    public ForwardEvaluationInfo copyDoNotComplainInlineConditional() {
        return new ForwardEvaluationInfo(properties, doNotReevaluateVariableExpressions,
                notAssignmentTarget, assignmentTarget, false, inlining);
    }

    public ForwardEvaluationInfo copyDoNotReevaluateVariableExpressionsDoNotComplain() {
        return new ForwardEvaluationInfo(properties, true, notAssignmentTarget,
                assignmentTarget, false, inlining);
    }

    public ForwardEvaluationInfo copyRemoveContextNotNull() {
        Map<Property, DV> map = new HashMap<>(properties);
        map.remove(Property.CONTEXT_NOT_NULL);
        return new ForwardEvaluationInfo(map, doNotReevaluateVariableExpressions, notAssignmentTarget, assignmentTarget,
                complainInlineConditional, inlining);
    }

    public boolean allowInline(MethodInfo methodInfo) {
        return !inlining.contains(methodInfo);
    }

    public ForwardEvaluationInfo addMethod(MethodInfo methodInfo) {
        Set<MethodInfo> set = SetUtil.immutableUnion(inlining, Set.of(methodInfo));
        return new ForwardEvaluationInfo(properties, doNotReevaluateVariableExpressions, notAssignmentTarget, assignmentTarget,
                complainInlineConditional, set);
    }
}
