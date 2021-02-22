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

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class Basics_12 {

    @NotNull
    public static String test1(@NotNull String in) {
        String a = in;
        String b = in;
        if (a.startsWith("0")) {
            return "0";
        }
        return b;
    }

    @Nullable
    public static String test2(@Nullable String in1, @NotNull String in2) {
        String a = in1;
        String b = a;
        a = in2;
        if (a.startsWith("0")) {
            // do nothing for a change
        }
        return b;
    }
}
