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

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.E2Container;

@E2Container
public class FieldNotRead {

    // ERROR 1: private field b is not read outside constructors
    private boolean b;

    public FieldNotRead() {
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
        // WARNING 1: ignoring result of method call
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
}
