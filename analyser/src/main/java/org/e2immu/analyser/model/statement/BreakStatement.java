package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.util.StringUtil;

import java.util.Set;

public class BreakStatement extends BreakOrContinueStatement {

    public BreakStatement(String label) {
        super(label);
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("break");
        if (label != null) {
            sb.append(" ");
            sb.append(label);
        }
        sb.append(";\n");
        return sb.toString();
    }
}
