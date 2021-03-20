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

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public record ForwardEvaluationInfo(Map<VariableProperty, Integer> properties, boolean notAssignmentTarget) {

    public ForwardEvaluationInfo(Map<VariableProperty, Integer> properties, boolean notAssignmentTarget) {
        this.properties = Map.copyOf(properties);
        this.notAssignmentTarget = notAssignmentTarget;
    }

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public boolean isNotAssignmentTarget() {
        return notAssignmentTarget;
    }

    public String toString() {
        return new StringJoiner(", ", ForwardEvaluationInfo.class.getSimpleName() + "[", "]")
                .add("notAssignmentTarget=" + notAssignmentTarget)
                .add("properties=" + properties)
                .toString();
    }

    public static ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfo(
            Map.of(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE), true);

    // the FALSE on not-null is because we intend to set it, so it really does not matter what the current value is
    public static ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfo(
            Map.of(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE),
            false);

    public static ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfo(
            Map.of(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL),
            true);

    public ForwardEvaluationInfo copyModificationEnsureNotNull() {
        Map<VariableProperty, Integer> map = new HashMap<>();
        map.put(VariableProperty.CONTEXT_MODIFIED,
                properties.getOrDefault(VariableProperty.CONTEXT_MODIFIED, Level.DELAY));
        map.put(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        return new ForwardEvaluationInfo(map, true);
    }
}
