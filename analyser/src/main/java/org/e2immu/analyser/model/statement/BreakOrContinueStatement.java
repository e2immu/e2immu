package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.Variable;

import java.util.Map;

public abstract class BreakOrContinueStatement implements Statement {

    public final String label;

    protected BreakOrContinueStatement(String label) {
        this.label = label;
    }

    @Override
    public Statement translate(Map<? extends Variable, ? extends Variable> translationMap) {
        return this;
    }
}
