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

import org.e2immu.annotation.*;

@E2Container(after = "t")
public class FlipSwitch {

    @Final(after = "t")
    private volatile boolean t;

    private boolean set$Precondition() { return !t; }
    @Mark("t")
    public void set() {
        synchronized (this) {
            if (t) {
                throw new IllegalStateException("Already set");
            }
            t = true;
        }
    }

    @NotModified
    @TestMark("t")
    public boolean isSet() {
        return t;
    }

    private boolean copy$Precondition() { return !t; }
    @Mark("t") // but conditionally
    public void copy(FlipSwitch other) {
        if (other.isSet()) set();
    }

    @Override
    public String toString() {
        return "FlipSwitch{" + t + '}';
    }
}
