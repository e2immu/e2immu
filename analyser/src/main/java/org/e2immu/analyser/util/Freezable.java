/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

import org.e2immu.annotation.Final;
import org.e2immu.annotation.Mark;

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

    public boolean isFrozen() {
        return frozen;
    }

    private boolean ensureNotFrozen$Precondition() { return !frozen; }
    protected void ensureNotFrozen() {
        if (frozen) throw new UnsupportedOperationException("Already frozen!");
    }

    private boolean ensureFrozen$Precondition() { return frozen; }
    protected void ensureFrozen() {
        if (!frozen) throw new UnsupportedOperationException("Not yet frozen!");
    }

}
