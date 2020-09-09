package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public Statement translate(TranslationMap translationMap) {
        return new AssertStatement(translationMap.translateExpression(check), translationMap.translateExpression(message));
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
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
    public List<? extends Element> subElements() {
        return message == null ? List.of(check) : List.of(check, message);
    }
}
