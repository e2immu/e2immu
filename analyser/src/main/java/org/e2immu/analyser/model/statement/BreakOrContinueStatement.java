package org.e2immu.analyser.model.statement;

public abstract class BreakOrContinueStatement extends StatementWithStructure {
    public final String label;

    public BreakOrContinueStatement(String label) {
        this.label = label;
    }

    public boolean hasALabel() {
        return label != null;
    }
}
