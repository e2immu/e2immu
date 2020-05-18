package org.e2immu.analyser.model;

import java.util.StringJoiner;

public interface ForwardEvaluationInfo {

    int getNotNull();

    int getNotModified();

    boolean isAssignmentTarget();

    class ForwardEvaluationInfoContainer implements ForwardEvaluationInfo {
        public final int notNull;
        public final boolean assignmentTarget;
        public final int notModified;

        public ForwardEvaluationInfoContainer(int notNull, int notModified, boolean assignmentTarget) {
            this.notNull = notNull;
            this.notModified = notModified;
            this.assignmentTarget = assignmentTarget;
        }

        @Override
        public int getNotNull() {
            return notNull;
        }

        @Override
        public int getNotModified() {
            return notModified;
        }

        @Override
        public boolean isAssignmentTarget() {
            return assignmentTarget;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ForwardEvaluationInfoContainer.class.getSimpleName() + "[", "]")
                    .add("notNull=" + notNull)
                    .add("assignmentTarget=" + assignmentTarget)
                    .add("notModified=" + notModified)
                    .toString();
        }
    }

    ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfoContainer(Level.FALSE, Level.DELAY, false);

    // the FALSE on not-null is because we intend to set it, so it really does not matter what the current value is
    ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfoContainer(Level.FALSE, Level.DELAY, true);

    ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfoContainer(Level.TRUE, Level.DELAY, false);

    static ForwardEvaluationInfo create(int notNull, int notModified) {
        return new ForwardEvaluationInfoContainer(notNull, notModified, false);
    }
}
