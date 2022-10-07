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

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class SynchronizedStatement extends StatementWithExpression {

    public SynchronizedStatement(Identifier identifier,
                                 Expression expression,
                                 Block block,
                                 Comment comment) {
        super(identifier, new Structure.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatementExecution(StatementExecution.ALWAYS)
                .setBlock(block)
                .setComment(comment)
                .build(), expression);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof SynchronizedStatement other) {
            return identifier.equals(other.identifier) && expression.equals(other.expression)
                    && structure.block().equals(other.structure.block());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, expression, structure.block());
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        Expression tex = translationMap.translateExpression(structure.expression());
        List<Statement> translatedBlock = structure.block().translate(inspectionProvider, translationMap);
        if (tex == structure.expression() && !haveDirectTranslation(translatedBlock, structure.block())) {
            return List.of(this);
        }
        if (translatedBlock.size() == 1 && translatedBlock.get(0) instanceof Block block) {
            return List.of(new SynchronizedStatement(identifier, tex, block, structure.comment()));
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("synchronized"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(structure.block().output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression(), structure.block());
    }


    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            structure.expression().visit(predicate);
            structure.block().visit(predicate);
        }
    }
}
