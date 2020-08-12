package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class ForwardEvaluationInfo {
    public final boolean notAssignmentTarget;
    public final Map<VariableProperty, Integer> properties;

    public ForwardEvaluationInfo(Map<VariableProperty, Integer> properties, boolean notAssignmentTarget) {
        this.properties = ImmutableMap.copyOf(properties);
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


    public static ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfo(Map.of(VariableProperty.NOT_NULL, MultiLevel.NULLABLE), true);

    // the FALSE on not-null is because we intend to set it, so it really does not matter what the current value is
    public static ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfo(
            Map.of(VariableProperty.NOT_NULL, MultiLevel.NULLABLE),
            false);

    public static ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfo(
            Map.of(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL),
            true);

    public static ForwardEvaluationInfo NOT_NULL_MODIFIED = new ForwardEvaluationInfo(
            Map.of(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL,
                    VariableProperty.MODIFIED, Level.TRUE),
            true);

    public ForwardEvaluationInfo copyModificationEnsureNotNull() {
        Map<VariableProperty, Integer> map = new HashMap<>();
        map.put(VariableProperty.MODIFIED, properties.getOrDefault(VariableProperty.MODIFIED, Level.DELAY));
        map.put(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        return new ForwardEvaluationInfo(map, true);
    }
}
