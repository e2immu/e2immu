package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.StringUtil;

import java.util.Set;

public class ContinueStatement implements Statement {

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("continue;\n");
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        return Set.of();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return SideEffect.STATIC_ONLY;
    }
}
