package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class ForwardEvaluationInfo {
    public final boolean assignmentTarget;
    public final Map<VariableProperty, Integer> properties;

    public ForwardEvaluationInfo(Map<VariableProperty, Integer> properties, boolean assignmentTarget) {
        this.properties = ImmutableMap.copyOf(properties);
        this.assignmentTarget = assignmentTarget;
    }

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public boolean isAssignmentTarget() {
        return assignmentTarget;
    }

    public String toString() {
        return new StringJoiner(", ", ForwardEvaluationInfo.class.getSimpleName() + "[", "]")
                .add("assignmentTarget=" + assignmentTarget)
                .add("properties=" + properties)
                .toString();
    }


    public static ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfo(Map.of(VariableProperty.NOT_NULL, Level.FALSE), false);

    // the FALSE on not-null is because we intend to set it, so it really does not matter what the current value is
    public static ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfo(Map.of(VariableProperty.NOT_NULL, Level.FALSE), true);

    public static ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfo(Map.of(VariableProperty.NOT_NULL, Level.TRUE), false);

    public ForwardEvaluationInfo copyEnsureNotNull() {
        Map<VariableProperty, Integer> map = new HashMap<>(properties);
        map.put(VariableProperty.NOT_NULL, Level.TRUE);
        return new ForwardEvaluationInfo(map, assignmentTarget);
    }
}
