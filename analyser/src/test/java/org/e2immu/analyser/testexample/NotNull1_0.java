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
import org.e2immu.annotation.NotNull1;

import java.util.Map;

public class NotNull1_0 {

    public static boolean methodWithWarnings(@NotNull Map<String, Integer> map) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getKey().startsWith("a")) {
                return true;
            }
            if (entry.getValue() < 1) {
                return true;
            }
        }
        return false;
    }

    public static boolean method(@NotNull1(contract = true) Map<String, Integer> map) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getKey().startsWith("a")) {
                return true;
            }
            if (entry.getValue() < 1) {
                return true;
            }
        }
        return false;
    }
}
