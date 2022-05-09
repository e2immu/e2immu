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

import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.LimitedStatementAnalysis;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;

import java.util.function.Predicate;

public class EmptyStatement extends StatementWithStructure {

    public EmptyStatement(Identifier identifier) {
        super(identifier);
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(Symbol.SEMICOLON);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        // do nothing
    }
}

