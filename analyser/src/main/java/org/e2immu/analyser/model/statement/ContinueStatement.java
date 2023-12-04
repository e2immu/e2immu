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
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Objects;

public class ContinueStatement extends BreakOrContinueStatement {

    public ContinueStatement(Identifier identifier, String labelOfStatement, String goToLabel, Comment comment) {
        super(identifier, labelOfStatement, goToLabel, comment);
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(Keyword.CONTINUE);
        if (goToLabel != null) {
            outputBuilder.add(Space.ONE).add(new Text(goToLabel));
        }
        outputBuilder.add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
        return outputBuilder;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof ContinueStatement other) {
            return identifier.equals(other.identifier) && Objects.equals(goToLabel, other.goToLabel);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, goToLabel);
    }

    @Override
    public int getComplexity() {
        return 1;
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;
        return List.of(this);
    }
}
