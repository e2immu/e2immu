package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SwitchStatement extends StatementWithExpression {
    public final List<SwitchEntry> switchEntries;

    public SwitchStatement(Expression selector, List<SwitchEntry> switchEntries) {
        super(selector, ForwardEvaluationInfo.NOT_NULL);
        this.switchEntries = ImmutableList.copyOf(switchEntries);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SwitchStatement(translationMap.translateExpression(expression),
                switchEntries.stream().map(se -> (SwitchEntry) se.translate(translationMap)).collect(Collectors.toList()));
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
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect sideEffect = expression.sideEffect(evaluationContext);
        return switchEntries.stream().map(s -> s.sideEffect(evaluationContext))
                .reduce(sideEffect, SideEffect::combine);
    }

    @Override
    public CodeOrganization codeOrganization() {
        CodeOrganization.Builder builder = new CodeOrganization.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL);
        switchEntries.forEach(se -> builder.addSubStatement(se.codeOrganization()).setStatementsExecutedAtLeastOnce(v -> false));
        boolean haveNoDefault = switchEntries.stream().allMatch(SwitchEntry::isNotDefault);
        builder.setNoBlockMayBeExecuted(haveNoDefault);
        return builder.build();
    }
}
