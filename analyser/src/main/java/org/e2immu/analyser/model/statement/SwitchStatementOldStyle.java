/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.Or;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SMapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SwitchStatementOldStyle extends StatementWithExpression implements SwitchStatement {

    public record SwitchLabel(Expression expression, int from) implements Comparable<SwitchLabel> {

        public SwitchLabel translate(TranslationMap translationMap) {
            return new SwitchLabel(expression.translate(translationMap), from);
        }

        @Override
        public int compareTo(SwitchLabel o) {
            return from - o.from;
        }

        public OutputBuilder output(Qualification qualification) {
            OutputBuilder outputBuilder = new OutputBuilder();
            if (expression == EmptyExpression.DEFAULT_EXPRESSION) {
                outputBuilder.add(new Text("default"));
            } else {
                outputBuilder.add(new Text("case"))
                        .add(Space.ONE)
                        .add(expression.output(qualification));
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
    public Stream<Expression> labels() {
        return labelExpressions.stream();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SwitchStatementOldStyle(translationMap.translateExpression(expression),
                (Block) structure.block().translate(translationMap),
                switchLabels.stream().map(sl -> sl.translate(translationMap)).collect(Collectors.toList()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
        outputBuilder.add(Symbol.LEFT_BRACE);
        if (statementAnalysis.navigationData.hasSubBlocks() &&
                statementAnalysis.navigationData.blocks.get().get(0).isPresent()) {
            Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
            outputBuilder.add(guideGenerator.start());
            StatementAnalysis firstStatement = statementAnalysis.navigationData.blocks.get().get(0).orElseThrow();
            Block.outputSwitchOldStyle(qualification, outputBuilder, guideGenerator, firstStatement, switchLabelMap(firstStatement));
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

    public Map<String, Expression> startingPointToLabels(EvaluationContext evaluationContext, StatementAnalysis firstStatement) {
        return switchLabelMap(firstStatement).entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> new Or(evaluationContext.getPrimitives()).append(evaluationContext,
                        e.getValue().stream()
                                .map(switchLabel ->
                                        switchLabel.expression == EmptyExpression.DEFAULT_EXPRESSION ? switchLabel.expression :
                                                Equals.equals(evaluationContext, expression, switchLabel.expression))
                                .collect(Collectors.toList()))));
    }

    public boolean atLeastOneBlockExecuted() {
        if (switchLabels.isEmpty()) return false;
        if (switchLabels.get(switchLabels.size() - 1).expression == EmptyExpression.DEFAULT_EXPRESSION) return true;
        if (expression.returnType().typeInfo.typeInspection.get().typeNature() == TypeNature.ENUM) {
            return switchLabels.size() == expression.returnType().typeInfo.countEnumConstants();
        }
        return false;
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(List.of(expression, structure.block()), labelExpressions);
    }
}
