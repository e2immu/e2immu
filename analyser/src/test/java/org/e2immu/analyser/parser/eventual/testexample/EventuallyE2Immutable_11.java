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

import org.e2immu.annotation.*;

/*
Like EventuallyE2Immutable_3, with different types of errors.
The two "error" methods currently do not stop the type from becoming eventually E2Container.
 */
@E2Container(after = "t")
public class EventuallyE2Immutable_11<T> {

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

    @TestMark("t")
    public boolean isSet() {
        return t != null;
    }

    @TestMark(value = "t", before = true)
    public boolean isNotYetSet() {
        return t == null;
    }

    @Modified
    public void error1(EventuallyE2Immutable_11<T> other) {
        if (!other.isSet()) {
            setT(other.getT()); // should cause an error!
        }
    }

    /*
    other.getT() requires the precondition null!=other.t
    while isNotYetSet() provides null==other.t
    */
    @Modified
    public void error2(EventuallyE2Immutable_11<T> other) {
        if (other.isNotYetSet()) {
            setT(other.getT()); // should cause an error!
        }
    }
}
