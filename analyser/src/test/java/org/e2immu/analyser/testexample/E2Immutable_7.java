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

import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;

// here, SimpleContainer cannot be replaced by T or Object, but there are no fields linked to parameters
@E2Container
public class E2Immutable_7 {

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

    @NotModified
    private final Map<String, SimpleContainer> map7;

    @Independent
    public E2Immutable_7(Map<String, SimpleContainer> map7Param) {
        map7 = new HashMap<>(map7Param); // not linked
    }

    @Independent
    public SimpleContainer get7(String input) {
        return map7.get(input);
    }

    @Independent
    public Map<String, SimpleContainer> getMap7() {
        Map<String, SimpleContainer> incremented = new HashMap<>(map7);
        incremented.values().forEach(sc -> sc.setI(sc.getI() + 1));
        return incremented;
    }
}
