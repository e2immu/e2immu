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

import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SwitchStatementNewStyle extends StatementWithExpression implements HasSwitchLabels {
    public final List<SwitchEntry> switchEntries;

    public SwitchStatementNewStyle(Identifier identifier,
                                   Expression selector,
                                   List<SwitchEntry> switchEntries,
                                   Comment comment) {
        super(identifier, codeOrganization(selector, switchEntries, comment), selector);
        this.switchEntries = List.copyOf(switchEntries);
    }

    @Override
    public Stream<Expression> labels() {
        return switchEntries.stream().flatMap(e -> e.labels.stream());
    }

    @Override
    public int getComplexity() {
        return 1 + switchEntries.stream().mapToInt(SwitchEntry::getComplexity).sum() + expression.getComplexity();
    }

    private static Structure codeOrganization(Expression expression,
                                              List<SwitchEntry> switchEntries,
                                              Comment comment) {
        Structure.Builder builder = new Structure.Builder()
                .setExpression(expression)
                .setStatementExecution(StatementExecution.NEVER) // will be ignored
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL);
        switchEntries.forEach(se -> builder.addSubStatement(se.getStructure()));
        return builder.setComment(comment).build();
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        Expression translatedVariable = expression.translate(inspectionProvider, translationMap);
        List<SwitchEntry> translatedEntries = switchEntries.stream()
                .map(l -> (SwitchEntry) l.translate(inspectionProvider, translationMap).get(0)).toList();

        return List.of(new SwitchStatementNewStyle(identifier, translatedVariable, translatedEntries,
                structure.comment()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(Keyword.SWITCH)
                .add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE);
        Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
        outputBuilder.add(guideGenerator.start());
        int i = 0;
        for (SwitchEntry switchEntry : switchEntries) {
            if (i > 0) outputBuilder.add(guideGenerator.mid());
            outputBuilder.add(switchEntry.output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, i)));
            i++;
        }
        return outputBuilder.add(guideGenerator.end()).add(Symbol.RIGHT_BRACE);
    }

    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(List.of(expression), switchEntries);
    }


    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
            switchEntries.forEach(switchEntry -> switchEntry.visit(predicate));
        }
    }
}
