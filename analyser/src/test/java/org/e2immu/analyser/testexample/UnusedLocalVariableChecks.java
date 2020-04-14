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

public class UnusedLocalVariableChecks {

    // ERROR 3: private field b is not read outside constructors
    private boolean b;

    public UnusedLocalVariableChecks() {
        int a = 0;
        // ERROR 4: private variable a is not used
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
}
