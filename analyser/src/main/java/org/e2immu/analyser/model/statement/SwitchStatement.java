package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class SwitchStatement extends StatementWithExpression {
    public final List<SwitchEntry> switchEntries;

    public SwitchStatement(Expression selector, List<SwitchEntry> switchEntries) {
        super(selector);
        this.switchEntries = ImmutableList.copyOf(switchEntries);
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("switch(");
        sb.append(expression.expressionString(0));
        sb.append(") {\n");
        for (SwitchEntry switchEntry : switchEntries) {
            sb.append(switchEntry.statementString(indent + 4));
        }
        StringUtil.indent(sb, indent);
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect sideEffect = expression.sideEffect(sideEffectContext);
        return switchEntries.stream().map(s -> s.sideEffect(sideEffectContext))
                .reduce(sideEffect, SideEffect::combine);
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization(expression, switchEntries.stream().map(SwitchEntry::toExpressionsWithStatements).collect(Collectors.toList()));
    }
}
