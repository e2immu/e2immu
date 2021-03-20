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

package org.e2immu.annotatedapi;

public class AnnotatedAPI {

    /**
     * The method analyser replaces the return value of this method
     * to a special value that it recognises; it returns true when
     * the clause in its parameter is present in the current state.
     * <p>
     * The method does NOT return identity; it is not modifying.
     *
     * @param b the clause
     * @return specialised inline method
     */

    public static boolean isFact(boolean b) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param test true when testing, false when generating a clause for the state
     * @return true when absence of information means knowing the negation
     */
    public static boolean isKnown(boolean test) {
        throw new UnsupportedOperationException();
    }
}
