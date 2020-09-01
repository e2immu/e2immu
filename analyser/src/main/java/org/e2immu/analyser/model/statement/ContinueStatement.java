package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.StringUtil;

import java.util.Set;

public class ContinueStatement extends BreakOrContinueStatement {

    public ContinueStatement(String label) {
        super(label);
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
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

    @Override
    public Set<String> imports() {
        return Set.of();
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return Set.of();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.STATIC_ONLY;
    }
}
