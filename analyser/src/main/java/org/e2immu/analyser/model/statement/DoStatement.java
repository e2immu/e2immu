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
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;

public class DoStatement extends LoopStatement {

    public DoStatement(Identifier identifier,
                       String label,
                       Expression expression,
                       Block block) {
        super(identifier, new Structure.Builder()
                .setStatementExecution(StatementExecution.ALWAYS)
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
    public Statement translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression tex = expression.translate(inspectionProvider, translationMap);
        Block block = translationMap.translateBlock(inspectionProvider, structure.block());
        if (tex == expression && block == structure.block()) return this;
        return new DoStatement(identifier, label, tex, block);
    }


    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("do"))
                .add(structure.block().output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 0)))
                .add(new Text("while"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS);
    }
}
