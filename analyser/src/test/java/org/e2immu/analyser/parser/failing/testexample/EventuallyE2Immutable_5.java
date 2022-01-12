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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;

/*
Two fields
 */
@E2Container(after = "b,t")
public class EventuallyE2Immutable_5<T> {

    private T t;
    private boolean b;

    @Mark("b")
    public void setB() {
        if (this.b) throw new UnsupportedOperationException();
        this.b = true;
    }

    @TestMark("b")
    public boolean isB() {
        return b;
    }

    @Only(after = "b")
    public void goAfter() {
        if (!b) throw new UnsupportedOperationException();
        // do something
    }

    @Only(before = "b")
    public void goBefore() {
        if (b) throw new UnsupportedOperationException();
        // do something
    }

    @Mark("t")
    public void setT(T t) {
        if (t == null) throw new NullPointerException();
        if (this.t != null) throw new UnsupportedOperationException();
        this.t = t;
    }

    @Only(after = "t")
    public T getT() {
        if (t == null) throw new UnsupportedOperationException();
        return t;
    }

    @TestMark("t")
    public boolean isSetT() {
        return t != null;
    }

    @TestMark("b,t")
    public boolean isReady() {
        return b && t != null;
    }
}
