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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Independent;

import java.util.HashMap;
import java.util.Map;

@E2Container
public class E2Immutable_6 {

    // SimpleContainer can be replaced by an unbound parameter type in this example

    @Independent
    @Container
    static class SimpleContainer {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    private final Map<String, SimpleContainer> map6;

    public E2Immutable_6(Map<String, SimpleContainer> map6Param) {
        map6 = new HashMap<>(map6Param); // not linked
    }

    public SimpleContainer get6(String input) {
        return map6.get(input);
    }

    @E2Container
    public Map<String, SimpleContainer> getMap6() {
        return Map.copyOf(map6);
    }
}
