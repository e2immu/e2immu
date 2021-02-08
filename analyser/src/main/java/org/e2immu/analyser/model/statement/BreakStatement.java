package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.output.*;

public class BreakStatement extends BreakOrContinueStatement {

    public BreakStatement(String label) {
        super(label);
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("break"));
        if (label != null) {
            outputBuilder.add(Space.ONE).add(new Text(label));
        }
        outputBuilder.add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
        return outputBuilder;
    }
}
