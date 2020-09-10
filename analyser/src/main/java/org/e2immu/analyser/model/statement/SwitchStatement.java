package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class SwitchStatement extends StatementWithExpression {
    public final List<SwitchEntry> switchEntries;

    public SwitchStatement(Expression selector, List<SwitchEntry> switchEntries) {
        super(codeOrganization(selector, switchEntries));
        this.switchEntries = ImmutableList.copyOf(switchEntries);
    }

    private static Structure codeOrganization(Expression expression, List<SwitchEntry> switchEntries) {
        Structure.Builder builder = new Structure.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL);
        switchEntries.forEach(se -> builder.addSubStatement(se.codeOrganization()).setStatementsExecutedAtLeastOnce(v -> false));
        boolean haveNoDefault = switchEntries.stream().allMatch(SwitchEntry::isNotDefault);
        builder.setNoBlockMayBeExecuted(haveNoDefault);
        return builder.build();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SwitchStatement(translationMap.translateExpression(structure.expression),
                switchEntries.stream().map(se -> (SwitchEntry) se.translate(translationMap)).collect(Collectors.toList()));
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("switch(");
        sb.append(structure.expression.expressionString(0));
        sb.append(") {\n");
        int i = 0;
        for (SwitchEntry switchEntry : switchEntries) {
            sb.append(switchEntry.statementString(indent + 4, NumberedStatement.startOfBlock(numberedStatement, i)));
            i++;
        }
        StringUtil.indent(sb, indent);
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(List.of(structure.expression), switchEntries);
    }
}
