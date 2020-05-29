package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;

import java.util.Map;
import java.util.StringJoiner;

public interface ForwardEvaluationInfo {

    int getProperty(VariableProperty variableProperty);

    boolean isAssignmentTarget();

    class ForwardEvaluationInfoContainer implements ForwardEvaluationInfo {
        public final boolean assignmentTarget;
        public final Map<VariableProperty, Integer> properties;

        public ForwardEvaluationInfoContainer(Map<VariableProperty, Integer> properties, boolean assignmentTarget) {
            this.properties = ImmutableMap.copyOf(properties);
            this.assignmentTarget = assignmentTarget;
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        @Override
        public boolean isAssignmentTarget() {
            return assignmentTarget;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ForwardEvaluationInfoContainer.class.getSimpleName() + "[", "]")
                    .add("assignmentTarget=" + assignmentTarget)
                    .add("properties=" + properties)
                    .toString();
        }
    }

    ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfoContainer(Map.of(VariableProperty.NOT_NULL, Level.FALSE), false);

    // the FALSE on not-null is because we intend to set it, so it really does not matter what the current value is
    ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfoContainer(Map.of(VariableProperty.NOT_NULL, Level.FALSE), true);

    ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfoContainer(Map.of(VariableProperty.NOT_NULL, Level.TRUE), false);

    static ForwardEvaluationInfo create(Map<VariableProperty, Integer> properties) {
        return new ForwardEvaluationInfoContainer(properties, false);
    }
}
