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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SMapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SwitchStatementOldStyle extends StatementWithExpression implements HasSwitchLabels {

    public record SwitchLabel(Expression expression, int from) implements Comparable<SwitchLabel> {

        public SwitchLabel translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
            return new SwitchLabel(expression.translate(inspectionProvider, translationMap), from);
        }

        @Override
        public int compareTo(SwitchLabel o) {
            return from - o.from;
        }

        public OutputBuilder output(Qualification qualification) {
            OutputBuilder outputBuilder = new OutputBuilder();
            if (expression == EmptyExpression.DEFAULT_EXPRESSION) {
                outputBuilder.add(Keyword.DEFAULT);
            } else {
                outputBuilder.add(Keyword.CASE)
                        .add(Space.ONE)
                        .add(expression.output(qualification));
            }
            return outputBuilder.add(Symbol.COLON_LABEL);
        }
    }

    public final List<SwitchLabel> switchLabels;
    public final List<Expression> labelExpressions;

    public SwitchStatementOldStyle(Identifier identifier, String label,
                                   Expression selector, Block block, List<SwitchLabel> switchLabels,
                                   Comment comment) {
        super(identifier, label, new Structure.Builder()
                .setExpression(selector)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatementExecution(StatementExecution.ALWAYS)
                .setBlock(block)
                .setComment(comment)
                .build(), selector);
        this.switchLabels = switchLabels.stream().sorted().toList();
        labelExpressions = this.switchLabels.stream().map(SwitchLabel::expression).toList();
    }

    @Override
    public Stream<Expression> labels() {
        return labelExpressions.stream();
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        Expression translatedExpression = expression.translate(inspectionProvider, translationMap);
        List<SwitchLabel> translatedLabels = switchLabels.stream()
                .map(l -> l.translate(inspectionProvider, translationMap))
                .collect(Collectors.toList());
        return List.of(new SwitchStatementOldStyle(identifier, label, translatedExpression,
                ensureBlock(structure.block().identifier, structure.block().translate(inspectionProvider, translationMap)),
                translatedLabels, structure.comment()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(Keyword.SWITCH)
                .add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
        outputBuilder.add(Symbol.LEFT_BRACE);
        if (statementAnalysis.navigationHasSubBlocks() &&
                statementAnalysis.navigationBlock0IsPresent()) {
            Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
            outputBuilder.add(guideGenerator.start());
            LimitedStatementAnalysis firstStatement = statementAnalysis.navigationBlock0OrElseNull();
            assert firstStatement != null;
            Block.outputSwitchOldStyle(qualification, outputBuilder, guideGenerator, firstStatement, switchLabelMap(firstStatement));
            outputBuilder.add(guideGenerator.end());
        }
        return outputBuilder.add(Symbol.RIGHT_BRACE);
    }

    private Map<String, List<SwitchLabel>> switchLabelMap(LimitedStatementAnalysis firstStatement) {
        Map<String, List<SwitchLabel>> res = new HashMap<>();
        LimitedStatementAnalysis sa = firstStatement;
        int statementCnt = 0;
        int labelIndex = 0;
        do {
            while (labelIndex < switchLabels.size() && switchLabels.get(labelIndex).from == statementCnt) {
                SMapList.add(res, sa.index(), switchLabels.get(labelIndex));
                labelIndex++;
            }
            sa = sa.navigationNextGetOrElseNull();
            statementCnt++;
        } while (sa != null && labelIndex < switchLabels.size());
        return res;
    }

    public Map<String, Expression> startingPointToLabels(EvaluationResult context,
                                                         LimitedStatementAnalysis firstStatement) {
        Map<String, Expression> result = new HashMap<>();
        Map<String, List<SwitchLabel>> switchLabelMap = switchLabelMap(firstStatement);
        String indexOfDefault = null;
        for (Map.Entry<String, List<SwitchLabel>> entry : switchLabelMap.entrySet()) {
            boolean containsDefault = entry.getValue().get(entry.getValue().size() - 1).expression.isEmpty();
            if (containsDefault) {
                indexOfDefault = entry.getKey();
            } else {
                Expression value = Or.or(context, entry.getValue().stream()
                        .map(switchLabel -> Equals.equals(context, expression, switchLabel.expression)).toList());
                result.put(entry.getKey(), value);
            }
        }
        if (indexOfDefault != null) {
            Expression[] expressions = result.values().stream()
                    .map(v -> Negation.negate(context, v)).toArray(Expression[]::new);
            result.put(indexOfDefault, And.and(context, expressions));
        }
        return Map.copyOf(result);
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

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
            structure.block().visit(predicate);
            labelExpressions.forEach(e -> e.visit(predicate));
        }
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeStatement(this)) {
            expression.visit(visitor);
            visitor.startSubBlock(0);
            labelExpressions.forEach(e -> e.visit(visitor));
            structure.block().visit(visitor);
            visitor.endSubBlock(0);
        }
        visitor.afterStatement(this);
    }

    @Override
    public int getComplexity() {
        return 100; // TODO
    }
}
