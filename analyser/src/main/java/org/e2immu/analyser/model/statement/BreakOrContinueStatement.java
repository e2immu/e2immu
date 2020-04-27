package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.Statement;

public abstract class BreakOrContinueStatement implements Statement {

    public final String label;

    protected BreakOrContinueStatement(String label) {
        this.label = label;
    }
}
