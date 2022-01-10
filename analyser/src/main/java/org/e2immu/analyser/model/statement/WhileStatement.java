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
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

public class WhileStatement extends LoopStatement {

    public WhileStatement(Identifier identifier,
                          String label,
                          Expression expression,
                          Block block) {
        super(identifier, new Structure.Builder()
                .setStatementExecution((v, ec) -> {
                    if (v.isDelayed()) return v.causesOfDelay();
                    if (v.isBoolValueFalse()) return FlowData.NEVER;
                    if (v.isBoolValueTrue()) return FlowData.ALWAYS;
                    return FlowData.CONDITIONALLY;
                })
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setExpression(expression)
                .setExpressionIsCondition(true)
                .setBlock(block).build(), label);
    }

    @Override
    public boolean hasExitCondition() {
        return true;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new WhileStatement(identifier, label, translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block()));
    }


    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("while"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .addIfNotNull(messageComment(statementAnalysis))
                .add(structure.block().output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }
}
