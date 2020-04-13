package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Set;

public class AssertStatement implements Statement {

    public final Expression check;
    public final Expression message; // can be null

    public AssertStatement(Expression check, Expression message) {
        this.check = check;
        this.message = message;
    }

    // we're currently NOT adding message!


    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder().setExpression(check).build();
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("assert ");
        sb.append(check.expressionString(0));
        if (message != null) {
            sb.append(", ");
            sb.append(message.expressionString(0));
        }
        sb.append(";\n");
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        return SetUtil.immutableUnion(check.imports(), message == null ? Set.of() : message.imports());
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return check.sideEffect(sideEffectContext);
    }
}
