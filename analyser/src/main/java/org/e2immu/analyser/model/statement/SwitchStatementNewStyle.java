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
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.Guide;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SwitchStatementNewStyle extends StatementWithExpression implements HasSwitchLabels {
    public final List<SwitchEntry> switchEntries;

    public SwitchStatementNewStyle(Identifier identifier, Expression selector, List<SwitchEntry> switchEntries) {
        super(identifier, codeOrganization(selector, switchEntries), selector);
        this.switchEntries = List.copyOf(switchEntries);
    }

    @Override
    public Stream<Expression> labels() {
        return switchEntries.stream().flatMap(e -> e.labels.stream());
    }

    private static Structure codeOrganization(Expression expression, List<SwitchEntry> switchEntries) {
        Structure.Builder builder = new Structure.Builder()
                .setExpression(expression)
                .setStatementExecution(StatementExecution.NEVER) // will be ignored
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL);
        switchEntries.forEach(se -> builder.addSubStatement(se.getStructure()));
        return builder.build();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SwitchStatementNewStyle(identifier, translationMap.translateExpression(expression),
                switchEntries.stream().map(se -> (SwitchEntry) se.translate(translationMap)).collect(Collectors.toList()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE);
        Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
        outputBuilder.add(guideGenerator.start());
        int i = 0;
        for (SwitchEntry switchEntry : switchEntries) {
            outputBuilder.add(switchEntry.output(qualification, guideGenerator, StatementAnalysis.startOfBlock(statementAnalysis, i)));
            i++;
        }
        return outputBuilder.add(guideGenerator.end()).add(Symbol.RIGHT_BRACE);
    }

    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(List.of(expression), switchEntries);
    }
}
