package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.util.StringUtil;

public class EmptyStatement extends StatementWithStructure {
    public static final EmptyStatement EMPTY_STATEMENT = new EmptyStatement();

    private EmptyStatement() {
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(Symbol.SEMICOLON);
    }
}

