package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LabeledStatement implements Statement {
    public final String label;
    public final Statement statement;

    public LabeledStatement(String label, Statement statement) {
        this.label = label;
        this.statement = statement;
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append(label).append(": ").append(statement.statementString(0));
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        return statement.imports();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return statement.sideEffect(sideEffectContext);
    }

    @Override
    public CodeOrganization codeOrganization() {
        return statement.codeOrganization();
    }
}
