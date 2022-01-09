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

package org.e2immu.analyser.resolver.testexample;

public class Basics_5 {

    public int method1() {
        int i, j;
        i = 3;
        j = i + 8;
        return i + j;
    }

    public int method2() {
        int i = 4, j;
        j = i + 8;
        return i + j;
    }

    public int method3() {
        // int i = 4 + j, j = 2; does not compile!
        int i = 4, j = i + 2;
        assert j == 6;
        return j;
    }
}
