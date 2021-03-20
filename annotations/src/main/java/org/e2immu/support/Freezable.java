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

package org.e2immu.support;

import org.e2immu.annotation.Final;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.TestMark;

/**
 * Super-class for eventually immutable types.
 * The life cycle of the class has two states: an initial one, and a final one.
 * The transition is irrevocable.
 * Freezable classes start in mutable form, and once frozen, become immutable.
 * <p>
 * Methods that make modifications to the content of fields, should call <code>ensureNotFrozen</code>
 * as their first statement.
 * <p>
 * Methods that can only be called when the class is in its immutable state should call
 * <code>ensureFrozen</code> as their first statement.
 */

public abstract class Freezable {

    @Final(after = "frozen")
    private volatile boolean frozen;

    @Mark("frozen")
    public void freeze() {
        ensureNotFrozen();
        frozen = true;
    }

    @TestMark("frozen")
    public boolean isFrozen() {
        return frozen;
    }

    private boolean ensureNotFrozen$Precondition() { return !frozen; }
    public void ensureNotFrozen() {
        if (frozen) throw new IllegalStateException("Already frozen!");
    }

    private boolean ensureFrozen$Precondition() { return frozen; }
    public void ensureFrozen() {
        if (!frozen) throw new IllegalStateException("Not yet frozen!");
    }

}
