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

import org.e2immu.analyser.model.Comment;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Visitor;

import java.util.function.Predicate;

public abstract class BreakOrContinueStatement extends StatementWithStructure {
    public final String goToLabel;

    public BreakOrContinueStatement(Identifier identifier, String labelOfStatement, String goToLabel, Comment comment) {
        super(identifier, labelOfStatement, comment);
        this.goToLabel = goToLabel;
    }

    public String goToLabel() {
        return goToLabel;
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        predicate.test(this);
    }

    @Override
    public void visit(Visitor visitor) {
        visitor.beforeStatement(this);
        visitor.afterStatement(this);
    }
}
