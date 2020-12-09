package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.output.Guide;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class SwitchStatement extends StatementWithExpression {
    public final List<SwitchEntry> switchEntries;

    public SwitchStatement(Expression selector, List<SwitchEntry> switchEntries) {
        super(codeOrganization(selector, switchEntries), selector);
        this.switchEntries = ImmutableList.copyOf(switchEntries);
    }

    private static Structure codeOrganization(Expression expression, List<SwitchEntry> switchEntries) {
        Structure.Builder builder = new Structure.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL);
        switchEntries.forEach(se -> builder.addSubStatement(se.getStructure()).setStatementExecution(se::statementExecution));
        return builder.build();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SwitchStatement(translationMap.translateExpression(expression),
                switchEntries.stream().map(se -> (SwitchEntry) se.translate(translationMap)).collect(Collectors.toList()));
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS).add(expression.output()).add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE);
        Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
        outputBuilder.add(guideGenerator.start());
        int i = 0;
        for (SwitchEntry switchEntry : switchEntries) {
            outputBuilder.add(switchEntry.output(guideGenerator, StatementAnalysis.startOfBlock(statementAnalysis, i)));
            i++;
        }
        return outputBuilder.add(guideGenerator.end()).add(Symbol.RIGHT_BRACE);
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(List.of(expression), switchEntries);
    }
}
