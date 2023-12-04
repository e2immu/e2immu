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

import java.util.List;
import java.util.function.Predicate;

public abstract class LoopStatement extends StatementWithExpression {

    protected LoopStatement(Identifier identifier, String label, Structure structure) {
        super(identifier, label, structure, structure.expression());
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression, structure.block());
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
            structure.block().visit(predicate);
        }
    }

    /**
     * Do we need to enrich the next statement's state with the exit condition of the loop?
     * A forEach has no exit, do-while and while do have one; the for statement has one if it does not define
     * its own loop variables.
     *
     * @return whether the loop statement has an exit condition
     */
    public abstract boolean hasExitCondition();
}
