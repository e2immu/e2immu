package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SMapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SwitchStatementOldStyle extends StatementWithExpression {

    public record SwitchLabel(Expression expression, int from) implements Comparable<SwitchLabel> {

        public SwitchLabel translate(TranslationMap translationMap) {
            return new SwitchLabel(expression.translate(translationMap), from);
        }

        @Override
        public int compareTo(SwitchLabel o) {
            return from - o.from;
        }

        public OutputBuilder output() {
            OutputBuilder outputBuilder = new OutputBuilder();
            if (expression == EmptyExpression.DEFAULT_EXPRESSION) {
                outputBuilder.add(new Text("default"));
            } else {
                outputBuilder.add(new Text("case"))
                        .add(Space.ONE)
                        .add(expression.output());
            }
            return outputBuilder.add(Symbol.COLON_LABEL);
        }
    }

    public final List<SwitchLabel> switchLabels;
    public final List<Expression> labelExpressions;

    public SwitchStatementOldStyle(Expression selector, Block block, List<SwitchLabel> switchLabels) {
        super(new Structure.Builder()
                .setExpression(selector)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatementExecution(StatementExecution.ALWAYS)
                .setBlock(block).build(), selector);
        this.switchLabels = switchLabels.stream().sorted().collect(Collectors.toUnmodifiableList());
        labelExpressions = this.switchLabels.stream().map(SwitchLabel::expression).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SwitchStatementOldStyle(translationMap.translateExpression(expression),
                (Block) structure.block().translate(translationMap),
                switchLabels.stream().map(sl -> sl.translate(translationMap)).collect(Collectors.toList()));
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS).add(expression.output()).add(Symbol.RIGHT_PARENTHESIS);
        outputBuilder.add(Symbol.LEFT_BRACE);
        if (statementAnalysis.navigationData.hasSubBlocks() &&
                statementAnalysis.navigationData.blocks.get().get(0).isPresent()) {
            Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
            outputBuilder.add(guideGenerator.start());
            StatementAnalysis firstStatement = statementAnalysis.navigationData.blocks.get().get(0).orElseThrow();
            Block.outputSwitchOldStyle(outputBuilder, guideGenerator, firstStatement, switchLabelMap(firstStatement));
            outputBuilder.add(guideGenerator.end());
        }
        return outputBuilder.add(Symbol.RIGHT_BRACE);
    }

    // IMPROVE doesn't take replacements into account
    private Map<String, List<SwitchLabel>> switchLabelMap(StatementAnalysis firstStatement) {
        Map<String, List<SwitchLabel>> res = new HashMap<>();
        StatementAnalysis sa = firstStatement;
        int statementCnt = 0;
        int labelIndex = 0;
        do {
            while (labelIndex < switchLabels.size() && switchLabels.get(labelIndex).from == statementCnt) {
                SMapList.add(res, sa.index, switchLabels.get(labelIndex));
                labelIndex++;
            }
            sa = sa.navigationData.next.get().orElse(null);
            statementCnt++;
        } while (sa != null && labelIndex < switchLabels.size());
        return res;
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(List.of(expression, structure.block()), labelExpressions);
    }
}