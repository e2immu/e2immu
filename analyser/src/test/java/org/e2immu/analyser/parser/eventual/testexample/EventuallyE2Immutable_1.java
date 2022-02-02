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

package org.e2immu.analyser.parser.eventual.testexample;

/*
similar to setOnce, to detect errors
 */
//@E2Container(after = "t")
public class EventuallyE2Immutable_1<T> {

    private T t;

    //@Mark("t")  missing: because of error, there's a modifying method without PC
    public void setT(T t) {
        if (t == null) throw new NullPointerException();
        if (this.t != null) throw new UnsupportedOperationException();
        this.t = t;
    }

    //@Only(after = "t") missing: because of error, there's a modifying method without PC
    public T getT() {
        if (t == null) throw new UnsupportedOperationException();
        return t;
    }

    /*
    getT() requires null!=this.t as precondition,
    while setT() requires null==this.t
     */
    public void error() {
        setT(getT()); // should cause an error
    }
}
