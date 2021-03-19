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

import org.e2immu.annotation.E2Container;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@E2Container
public class ModificationGraph {

    @Test
    public void useC1AndC2() {
        ModificationGraphC1 c1 = new ModificationGraphC1();
        ModificationGraphC2 c2 = new ModificationGraphC2(2, c1);
        assertEquals(3, c2.incrementAndGetWithI());
        assertEquals(1, c1.getI());
        assertEquals(5, c1.useC2(c2));
        assertEquals(2, c1.getI());
        assertEquals(5, c2.incrementAndGetWithI());
        assertEquals(3, c1.getI());
        assertEquals(9, c1.useC2(c2));
        assertEquals(4, c1.getI());
    }
}
