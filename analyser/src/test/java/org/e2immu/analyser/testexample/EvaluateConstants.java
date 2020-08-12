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

public class EvaluateConstants {

    final static boolean a = true;
    final static boolean b = false;

    @Constant(boolValue = false, test = true)
    final static boolean c = !a;
    final static boolean d = a || b;

    @Constant(boolValue = false, test = true)
    final static boolean e = c && !d;

    @NotNull
    @NotModified
    @Constant(boolValue = false)
    public static Boolean ee() {
        return e;
    }

    @NotNull
    @NotModified
    @Constant(stringValue = "b")
    public static String print() {
        // ERROR: if statement evaluates to constant
        if (ee()) return "a";
        return "b";
    }

    @NotNull
    @NotModified
    @Constant
    // ERROR: ee() evaluates to constant
    public static String print2() {
        return ee() ? "a" : "b";
    }

    @Constant(intValue = 3)
    final int i = 3;
    final int j = 233;

    @Constant(intValue = 699)
    final int k = i * j;

    @Constant(boolValue = true)
    final boolean l = k > 400;

    @NotModified
    @Constant(intValue = 162870)
    public int allThree() {
        return i + j * k;
    }

    @NotNull
    @Constant(stringValue = "hello")
    final static String s = "hello";

    @NotNull
    @Constant(stringValue = "world")
    final static String w = "world";

    @NotNull
    @Constant(stringValue = "hello world")
    final static String t = s + " " + w;

    @Constant(intValue = 0, test = true)
    public int someCalculation(int p) {
        int q = 1 * p + p + 0 * i; // this should be evaluated as 2*p
        return q - p * 2;
    }

    @NotNull
    @Final
    @Linked(type = AnnotationType.VERIFY_ABSENT)
    private String effectivelyFinal;

    @NotNull
    @Final
    @Constant(stringValue = "abc")
    private String constant;

    public EvaluateConstants(@NotNull String in) {
        if (in == null) throw new UnsupportedOperationException();
        effectivelyFinal = in;
        constant = "abc";
    }

    @NotNull
    public String getEffectivelyFinal() {
        return effectivelyFinal;
    }

    @NotNull
    @Constant(stringValue = "abc")
    public String getConstant() {
        return constant;
    }
}
