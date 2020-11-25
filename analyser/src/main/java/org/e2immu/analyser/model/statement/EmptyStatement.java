package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.util.StringUtil;

public class EmptyStatement extends StatementWithStructure {
    public static final EmptyStatement EMPTY_STATEMENT = new EmptyStatement();

    private EmptyStatement() {
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append(";\n");
        return sb.toString();
    }
}

