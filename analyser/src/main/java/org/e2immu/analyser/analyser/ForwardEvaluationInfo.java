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

import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public record ForwardEvaluationInfo(Map<Property, DV> properties,
                                    boolean doNotReevaluateVariableExpressions,
                                    boolean notAssignmentTarget,
                                    Variable assignmentTarget,
                                    boolean complainInlineConditional,
                                    Stage stage) {

    public ForwardEvaluationInfo(Map<Property, DV> properties,
                                 boolean doNotReevaluateVariableExpressions,
                                 boolean notAssignmentTarget,
                                 Variable assignmentTarget,
                                 boolean complainInlineConditional,
                                 Stage stage) {
        this.properties = Map.copyOf(properties);
        this.notAssignmentTarget = notAssignmentTarget;
        this.assignmentTarget = assignmentTarget;
        this.complainInlineConditional = complainInlineConditional;
        this.doNotReevaluateVariableExpressions = doNotReevaluateVariableExpressions;
        this.stage = stage;
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
            Stage.INITIAL);

    public static ForwardEvaluationInfo MERGE = new ForwardEvaluationInfo(
            Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV), false, true, null, true,
            Stage.MERGE);

    public static ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfo(
            Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV), false,
            false, null, true, Stage.INITIAL);

    public static ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfo(
            Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV), false,
            true, null, true, Stage.INITIAL);

    public ForwardEvaluationInfo copyDefault() {
        return new ForwardEvaluationInfo(Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV),
                doNotReevaluateVariableExpressions, true, assignmentTarget, complainInlineConditional, stage);
    }

    public ForwardEvaluationInfo notNullNotAssignment() {
        return new ForwardEvaluationInfo(
                Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV), doNotReevaluateVariableExpressions,
                true, assignmentTarget, complainInlineConditional, stage);
    }

    public ForwardEvaluationInfo notNullKeepAssignment() {
        return new ForwardEvaluationInfo(
                Map.of(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV), doNotReevaluateVariableExpressions,
                notAssignmentTarget, assignmentTarget, complainInlineConditional, stage);
    }

    public ForwardEvaluationInfo copyModificationEnsureNotNull() {
        Map<Property, DV> map = new HashMap<>();
        map.put(Property.CONTEXT_MODIFIED,
                properties.getOrDefault(Property.CONTEXT_MODIFIED, DV.FALSE_DV));
        map.put(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        return new ForwardEvaluationInfo(map, doNotReevaluateVariableExpressions, true,
                assignmentTarget, complainInlineConditional, stage);
    }

    public ForwardEvaluationInfo copyAddAssignmentTarget(Variable variable) {
        return new ForwardEvaluationInfo(properties, doNotReevaluateVariableExpressions,
                notAssignmentTarget, variable, complainInlineConditional, stage);
    }

    public ForwardEvaluationInfo copyAddAssignmentTargetEnsureNotNull(Variable variable) {
        Map<Property, DV> map = new HashMap<>(properties);
        map.merge(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV, DV::max);
        return new ForwardEvaluationInfo(map, doNotReevaluateVariableExpressions,
                notAssignmentTarget, variable, complainInlineConditional, stage);
    }

    public ForwardEvaluationInfo copyDoNotComplainInlineConditional() {
        return new ForwardEvaluationInfo(properties, doNotReevaluateVariableExpressions,
                notAssignmentTarget, assignmentTarget, false, stage);
    }

    public ForwardEvaluationInfo copyDoNotReevaluateVariableExpressionsDoNotComplain() {
        return new ForwardEvaluationInfo(properties, true, notAssignmentTarget,
                assignmentTarget, false, stage);
    }

    public ForwardEvaluationInfo copyRemoveContextNotNull() {
        Map<Property, DV> map = new HashMap<>(properties);
        map.remove(Property.CONTEXT_NOT_NULL);
        return new ForwardEvaluationInfo(map, doNotReevaluateVariableExpressions, notAssignmentTarget, assignmentTarget,
                complainInlineConditional, stage);
    }
}
