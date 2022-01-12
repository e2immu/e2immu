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

package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.NotModified;

/*
Test is here to catch certain delays in computing modifications.

 */
public class Basics_9 {

    @NotModified
    public static boolean isFact(boolean b) {
        throw new UnsupportedOperationException();
    }

    @NotModified
    public static boolean isKnown(boolean test) {
        throw new UnsupportedOperationException();
    }

    @NotModified
    static boolean setContainsValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? containsE : !isKnown(true) && size > 0 && retVal;
    }

    @NotModified
    public static boolean test1(boolean contains, boolean isEmpty) {
        return setContainsValueHelper(1, contains, isEmpty);
    }
}
