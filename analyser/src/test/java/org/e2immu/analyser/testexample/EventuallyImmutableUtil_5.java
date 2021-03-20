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

package org.e2immu.analyser.testexample;

import org.e2immu.support.SetOnce;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.TestMark;

/*
Use types in util to become an eventually immutable type

 */
@E2Container(after = "t")
public class EventuallyImmutableUtil_5 {

    static class S {
        public final SetOnce<String> string = new SetOnce<>();
        public final SetOnce<Boolean> bool = new SetOnce<>();


        @TestMark("bool,string")
        public boolean isReady() {
            return string.isSet() && bool.isSet();
        }
    }

    static class T {
        private final S s1 = new S();
        private final S s2 = new S();

        public boolean isTReady() {
            return s1.isReady() && s2.isReady();
        }
    }

    private final T t = new T();


    @TestMark("t")
    public boolean isReady1() {
        return t.s1.isReady() && t.s2.isReady();
    }

    @TestMark("t")
    public boolean isReady2() {
        return t.isTReady();
    }
}
