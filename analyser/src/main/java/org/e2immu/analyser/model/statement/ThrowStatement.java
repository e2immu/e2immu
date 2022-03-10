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
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;

public class ThrowStatement extends StatementWithExpression {

    public ThrowStatement(Identifier identifier, Expression expression) {
        super(identifier, new Structure.Builder().setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL).build(), expression);
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression());
    }

    @Override
    public Statement translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return new ThrowStatement(identifier, translationMap.translateExpression(expression));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("throw"))
                .add(Space.ONE).add(expression.output(qualification))
                .add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
    }
}
