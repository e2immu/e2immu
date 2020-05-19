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
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

// NOTE: this one relies on String.toLowerCase() being annotated @NotNull

@E2Container
public class UnusedLocalVariableChecks {

    // ERROR 1: private field b is not read outside constructors
    private boolean b;

    public UnusedLocalVariableChecks() {
        int a = 0;
        // ERROR 2+3: private variable a is not used, useless assignment
        b = true;
    }

    // ERROR 4: method1 should be marked static
    private void method1(String t) {
        String s;
        // ERROR 5: local variable s is not used
        if (t.length() < 19) {
            return;
        }
        // ERROR 6: ignoring result of method call
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
        // ERROR 7: assignment is not used
    }

    private static void checkForEach() {
        int[] integers = {1, 2, 3};
        for (int loopVar : integers) {
            // ERROR 8: loopVar is not used
            System.out.println("hello!");
        }
    }

    @NotNull
    private static String someMethod(String a) {
        return a.toLowerCase();
    }

    private static String method2(String param) {
        String b = someMethod(param);
        // ERROR 9: if statement evaluates to constant
        if (b == null) {
            if (param.contains("a")) { // the fact that this one evaluates to constant is caused by the previous error
                // ERROR 10: Unnecessary method call
                String a = someMethod("xzy").toString();
                // ERROR 11: if statement evaluates to constant; error is not hidden by unnecessary method call toString()
                // because toString() is @NotNull
                if (a == null) {
                    return b + "c";
                }
            }
        }
        return "c";
    }

    private static String method3(String param) {
        String b = someMethod(param);
        if (param.contains("a")) {
            String a = someMethod("xzy");
            // ERROR 12: if statement evaluates to constant
            if (a == null) {
                return b + "c";
            }
        }
        return "c";
    }
}
