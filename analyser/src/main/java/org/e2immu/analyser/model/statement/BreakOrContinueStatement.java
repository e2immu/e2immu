package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Statement;

public abstract class BreakOrContinueStatement extends StatementWithStructure {
    public final String label;

    public BreakOrContinueStatement(String label) {
        this.label = label;
    }
}
