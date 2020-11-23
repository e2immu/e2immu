/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class BasicCompanionMethods_3 {

    @Constant("true")
    static boolean test() {
        StringBuilder sb = new StringBuilder("abc").append(3).append("-");
        if (sb.length() == 3) { //evaluates to constant
            throw new UnsupportedOperationException(); // unreachable code
        }
        return sb.toString().length() == 5; // constant true
    }

}