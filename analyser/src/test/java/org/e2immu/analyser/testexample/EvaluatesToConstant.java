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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

// NOTE: this one relies on String.toLowerCase() being annotated @NotNull

@E2Container
public class EvaluatesToConstant {

    @NotNull
    private static String someMethod(String a) {
        return a.toLowerCase();
    }

    private static String method2(String param) {
        String b = someMethod(param);
        // ERROR 9: if statement evaluates to constant
        if (b == null) {
            return "a";
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