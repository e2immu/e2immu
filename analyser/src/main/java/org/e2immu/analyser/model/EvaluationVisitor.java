package org.e2immu.analyser.model;

public interface EvaluationVisitor {

    void visit(Expression expression, EvaluationContext evaluationContext, Value value, boolean changes);

    default void visit(Expression expression, EvaluationContext evaluationContext, Value value) {
        visit(expression, evaluationContext, value, false);
    }


    EvaluationVisitor NO_VISITOR = new EvaluationVisitor() {
        @Override
        public void visit(Expression expression, EvaluationContext evaluationContext, Value value, boolean changes) {
            // no code at all
        }
    };

}
