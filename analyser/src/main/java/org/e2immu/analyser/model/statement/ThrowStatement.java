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
import java.util.Objects;
import java.util.function.Predicate;

public class ThrowStatement extends StatementWithExpression {

    public ThrowStatement(Identifier identifier, Expression expression, Comment comment) {
        super(identifier, new Structure.Builder().setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setComment(comment)
                .build(), expression);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof ThrowStatement other) {
            return identifier.equals(other.identifier) && expression.equals(other.expression);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, expression);
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression());
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            structure.expression().visit(predicate);
        }
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        Expression tex = expression.translate(inspectionProvider, translationMap);
        if (tex == expression) return List.of(this);
        return List.of(new ThrowStatement(identifier, translationMap.translateExpression(expression),
                structure.comment()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("throw"))
                .add(Space.ONE).add(expression.output(qualification))
                .add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
    }
}
