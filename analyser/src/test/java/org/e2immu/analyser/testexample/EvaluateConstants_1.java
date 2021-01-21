/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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