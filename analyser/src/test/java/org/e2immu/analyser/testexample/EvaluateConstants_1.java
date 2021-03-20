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

import org.e2immu.annotation.*;

public class EvaluateConstants_1 {

    final static boolean a = true;
    final static boolean b = false;

    @Constant("false")
    final static boolean c = !a;
    final static boolean d = a || b;

    @Constant("false")
    final static boolean e = c && !d;

    @NotNull
    @NotModified
    @Constant("false")
    public static Boolean ee() {
        return e;
    }

    @NotNull
    @NotModified
    @Constant("b")
    public static String print() {
        // ERROR: if statement evaluates to constant
        if (ee()) return "a";
        return "b";
    }

    @NotNull
    @NotModified
    @Constant("b")
    // ERROR: ee() evaluates to constant
    public static String print2() {
        return ee() ? "a" : "b";
    }

    @Constant("3")
    final int i = 3;
    final int j = 233;

    @Constant("699")
    final int k = i * j;

    @Constant("true")
    final boolean l = k > 400;

    @NotModified
    @Constant("162870")
    public int allThree() {
        return i + j * k;
    }

    @NotNull
    @Constant("hello")
    final static String s = "hello";

    @NotNull
    @Constant("world")
    final static String w = "world";

    @NotNull
    @Constant("hello world")
    final static String t = s + " " + w;

    @Constant("0")
    public int someCalculation(int p) {
        int q = 1 * p + p + 0 * i; // this should be evaluated as 2*p
        return q - p * 2;
    }
}
