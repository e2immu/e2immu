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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.Keyword;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class AssertStatement extends StatementWithStructure {

    public final Expression message; // can be null

    public AssertStatement(Identifier identifier, Expression check, Expression message) {
        // IMPORTANT NOTE: we're currently NOT adding message!
        // we regard it as external to the code
        super(identifier, new Structure.Builder()
                .setExpression(check)
                .setExpressionIsCondition(true)
                .build());
        this.message = message;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof AssertStatement other) {
            return identifier.equals(other.identifier) && structure.expression().equals(other.structure.expression())
                    && message.equals(other.message);
        }
        return false;
    }

    @Override
    public int getComplexity() {
        return 1 + structure.expression().getComplexity() + (message == null ? 0 : message.getComplexity());
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, structure.expression(), message);
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;
        Expression tex = structure.expression().translate(inspectionProvider, translationMap);
        Expression msg = message == null ? null : message.translate(inspectionProvider, translationMap);
        if (tex == structure.expression() && msg == message) return List.of(this);
        return List.of(new AssertStatement(identifier, tex, msg));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder()
                .add(Keyword.ASSERT)
                .add(Space.ONE)
                .add(structure.expression().output(qualification))
                .add(message != null ? new OutputBuilder().add(Symbol.COMMA).add(message.output(qualification)) : new OutputBuilder())
                .add(Symbol.SEMICOLON)
                .addIfNotNull(messageComment(statementAnalysis));
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
}
