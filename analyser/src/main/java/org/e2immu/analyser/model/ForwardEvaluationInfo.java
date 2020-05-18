package org.e2immu.analyser.model;

public interface ForwardEvaluationInfo {

    boolean isNotNull();

    boolean isAssignmentTarget();

    ForwardEvaluationInfo DEFAULT = new ForwardEvaluationInfo() {
        @Override
        public boolean isNotNull() {
            return false;
        }

        @Override
        public boolean isAssignmentTarget() {
            return false;
        }
    };

    ForwardEvaluationInfo ASSIGNMENT_TARGET = new ForwardEvaluationInfo() {
        @Override
        public boolean isNotNull() {
            return false;
        }

        @Override
        public boolean isAssignmentTarget() {
            return true;
        }
    };

    ForwardEvaluationInfo ASSIGNMENT_TARGET_NOT_NULL = new ForwardEvaluationInfo() {
        @Override
        public boolean isNotNull() {
            return true;
        }

        @Override
        public boolean isAssignmentTarget() {
            return true;
        }
    };

    ForwardEvaluationInfo NOT_NULL = new ForwardEvaluationInfo() {
        @Override
        public boolean isNotNull() {
            return true;
        }

        @Override
        public boolean isAssignmentTarget() {
            return false;
        }
    };
}
