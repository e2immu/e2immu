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
import org.e2immu.analyser.output.*;

import java.util.List;

public class SynchronizedStatement extends StatementWithExpression {

    public SynchronizedStatement(Expression expression,
                                 Block block) {
        super(new Structure.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatementExecution(StatementExecution.ALWAYS)
                .setBlock(block).build(), expression);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SynchronizedStatement(translationMap.translateExpression(structure.expression()),
                translationMap.translateBlock(structure.block()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("synchronized"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(structure.block().output(qualification, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression(), structure.block());
    }
}
