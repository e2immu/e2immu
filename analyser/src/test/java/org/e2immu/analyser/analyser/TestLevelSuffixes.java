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

package org.e2immu.analyser.analyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLevelSuffixes {

    @Test
    public void test() {
        String[] order = {"-", "0", "0-C", "0-E", "0.0.0", "0.0.0-E", "0.0.0.0.0", "0.0.0.1.0", "0.0.0:M", "0:M"};
        for (int i = 0; i < order.length - 1; i++) {
            for (int j = i + 1; j < order.length; j++) {
                assertTrue(order[i].compareTo(order[j]) < 0);
            }
        }
    }
}
