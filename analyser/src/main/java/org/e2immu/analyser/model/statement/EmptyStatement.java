package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.StringUtil;

import java.util.Map;
import java.util.Set;

public class EmptyStatement implements Statement {
    public static final EmptyStatement EMPTY_STATEMENT = new EmptyStatement();

    private EmptyStatement() {
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
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

