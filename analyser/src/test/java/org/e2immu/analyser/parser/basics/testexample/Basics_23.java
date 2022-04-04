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

/*
non-trivial field scopes, scope variables
 */
public class Basics_23 {

    record A(int i) {
    }

    public static void method() {
        A a = new A(1);
        A c = a;
        assert a.i == c.i;
    }

    public static void method0(int k) {
        A a = new A(1);
        A b = new A(2);
        int j = (k < 3 ? a : b).i;
        if (k < 3) {
            assert j == 1;
        } else {
            assert j == 2;
        }
    }

    public static void method1(int k) {
        A a = new A(1);
        A c = new A(2);
        A b = k < 3 ? a : c;
        if (k >= 3) {
            assert b.i == c.i;
        } else {
            assert b.i == a.i;
        }
    }

    public static void method2(int k, A a, A c) {
        A b = k < 3 ? a : c;
        if (k >= 3) {
            assert b.i == c.i;
        } else {
            assert b.i == a.i;
        }
    }
}
