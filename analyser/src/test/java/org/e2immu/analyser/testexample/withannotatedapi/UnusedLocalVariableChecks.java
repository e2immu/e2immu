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

package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

// NOTE: this one relies on String.toLowerCase() being annotated @NotNull

public class UnusedLocalVariableChecks {

    // ERROR 3: private field b is not read outside constructors
    private boolean b;

    public UnusedLocalVariableChecks() {
        int a = 0;
        // ERROR 5: private variable a is not used
        b = true;
    }

    // ERROR 1: method1 should be marked static
    private void method1(String t) {
        String s;
        // ERROR 2: local variable s is not used
        if (t.length() < 19) {
            return;
        }
        t.trim();
    }

    @Constant(intValue = 1)
    private static int checkArray() {
        int[] integers = {1, 2, 3};
        int i = 0;
        return integers[i];
    }

    private static void checkArray2() {
        int[] integers = {1, 2, 3};
        int i = 0;
        integers[i] = 3;
        // ERROR: assignment is not used
    }

    private static void checkForEach() {
        int[] integers = {1, 2, 3};
        for (int loopVar : integers) {
            // ERROR 4: loopVar is not used
            System.out.println("hello!");
        }
    }

    @NotNull
    private static String someMethod(String a) {
        return a.toLowerCase();
    }

    private static String checkDoubleMethod(String param) {
        String b = someMethod(param);
        // ERROR: if statement evaluates to constant
        if (b == null) {
            if (param.contains("a")) { // the fact that this one evaluates to constant is caused by the previous error
                String a = someMethod("xzy").toString();
                // ERROR: if statement evaluates to constant; error hidden by unnecessary method call toString()
                if (a == null) {
                    return b + "c";
                }
            }
        }
        return "c";
    }

    private static String checkDoubleMethod2(String param) {
        String b = someMethod(param);
        if (param.contains("a")) {
            String a = someMethod("xzy");
            // ERROR: if statement evaluates to constant
            if (a == null) {
                return b + "c";
            }
        }
        return "c";
    }
}
