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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.TestMark;
import org.e2immu.support.SetOnce;

/*
Use types in util to become an eventually immutable type

 */
@ERContainer(after = "t")
public class EventuallyImmutableUtil_5 {

    @ERContainer(after = "bool,string")
    static class S {
        public final SetOnce<String> string = new SetOnce<>();
        public final SetOnce<Boolean> bool = new SetOnce<>();


        @TestMark("bool,string")
        public boolean isReady() {
            return string.isSet() && bool.isSet();
        }
    }

    @ERContainer(after = "s1,s2")
    static class T {
        private final S s1 = new S();
        private final S s2 = new S();

        // IMPROVE should be @TestMark("s1,s2")
        public boolean isTReady() {
            return s1.isReady() && s2.isReady();
        }
    }

    private final T t = new T();


    // IMPROVE should be @TestMark("t")
    public boolean isReady1() {
        return t.s1.isReady() && t.s2.isReady();
    }

    // IMPROVE should be @TestMark("t")
    public boolean isReady2() {
        return t.isTReady();
    }
}
