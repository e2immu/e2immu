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

package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.Constant;

// expansion of constant

public class InlinedMethod_5 {

    public final int i;

    public InlinedMethod_5(int p) {
        i = p;
    }

    public int sum(int j) {
        return i + j;
    }

    public int sum5() {
        return sum(5);
    }

    @Constant("11")
    public static int expand1() {
        InlinedMethod_5 i5 = new InlinedMethod_5(6);
        return i5.sum5();
    }

    @Constant("7")
    public static int expand2() {
        InlinedMethod_5 i5 = new InlinedMethod_5(5);
        return i5.sum(2);
    }

    public static int expand3(int a, int b) {
        InlinedMethod_5 il5 = new InlinedMethod_5(a);
        return il5.sum(b);
    }
}
