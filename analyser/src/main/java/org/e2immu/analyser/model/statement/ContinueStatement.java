package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.util.StringUtil;

public class ContinueStatement extends BreakOrContinueStatement {

    public ContinueStatement(String label) {
        super(label);
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("continue");
        if (label != null) {
            sb.append(" ");
            sb.append(label);
        }
        sb.append(";\n");
        return sb.toString();
    }
}
