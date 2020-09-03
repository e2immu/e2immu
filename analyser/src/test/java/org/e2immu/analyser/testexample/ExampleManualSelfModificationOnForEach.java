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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

public class ExampleManualSelfModificationOnForEach {

    @Test
    public void testDangerous() {
        List<String> l1 = new ArrayList<>();
        Collections.addAll(l1, "a", "c", "e");
        try {
            print(l1);
            Assert.fail();
        } catch(ConcurrentModificationException cme) {
            // OK
        }
    }

    static void print(List<String> list) {
        list.forEach(l -> {
            System.out.println(l);
            if (l.startsWith("a")) {
                list.add("b");
            }
        });
    }
}
