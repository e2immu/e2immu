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

import org.e2immu.analyser.model.impl.ElementImpl;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Statement;

public abstract class StatementWithStructure extends ElementImpl implements Statement {
    public final Structure structure;
    public static final Structure EMPTY_CODE_ORGANIZATION = new Structure.Builder().build();

    public StatementWithStructure(Identifier identifier) {
        super(identifier);
        structure = EMPTY_CODE_ORGANIZATION;
    }

    public StatementWithStructure(Identifier identifier, Structure structure) {
        super(identifier);
        this.structure = structure;
    }

    @Override
    public Structure getStructure() {
        return structure;
    }

}
