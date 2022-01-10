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

public abstract class LoopStatement extends StatementWithExpression {
    public final String label;

    protected LoopStatement(Identifier identifier, Structure structure, String label) {
        super(identifier, structure, structure.expression());
        this.label = label;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression, structure.block());
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
