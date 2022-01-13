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
similar to setOnce, to detect errors: with ObjectFlows
 */
@E2Immutable(after = "t")
public class EventuallyE2Immutable_4<T> {

    private T t;

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

    /*
    the first statement requires null==this.t, but leaves null!=this.t as
    a state. The second statement requires null==this.t again.
     */
    public void error3(T t) {
        setT(t);
        setT(t); // error
    }

    /*
    Same, but now with other.
    */
    public void error4(@Modified EventuallyE2Immutable_4<T> other) {
        other.setT(getT());
        other.setT(getT()); // error
    }
}