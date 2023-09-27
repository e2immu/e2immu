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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

public class EvaluateConstants_1 {

    final static boolean a = true;
    final static boolean b = false;

    @ImmutableContainer("false")
    final static boolean c = !a;
    final static boolean d = a || b;

    @ImmutableContainer("false")
    final static boolean e = c && !d;

    @NotNull
    @NotModified
    @ImmutableContainer("false")
    public static Boolean ee() {
        return e;
    }

    @NotNull
    @NotModified
    @ImmutableContainer("b")
    public static String print() {
        // ERROR: if statement evaluates to constant
        if (ee()) return "a";
        return "b";
    }

    @NotNull
    @NotModified
    @ImmutableContainer("b")
    // ERROR: ee() evaluates to constant
    public static String print2() {
        return ee() ? "a" : "b";
    }

    @ImmutableContainer("3")
    final int i = 3;
    final int j = 233;

    @ImmutableContainer("699")
    final int k = i * j;

    @ImmutableContainer("true")
    final boolean l = k > 400;

    @NotModified
    @ImmutableContainer("162870")
    public int allThree() {
        return i + j * k;
    }

    @NotNull
    @ImmutableContainer("hello")
    final static String s = "hello";

    @NotNull
    @ImmutableContainer("world")
    final static String w = "world";

    @NotNull
    @ImmutableContainer("hello world")
    final static String t = s + " " + w;

    @ImmutableContainer("0")
    public int someCalculation(int p) {
        int q = 1 * p + p + 0 * i; // this should be evaluated as 2*p
        return q - p * 2;
    }
}
