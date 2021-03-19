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
import org.e2immu.annotation.Linked;

import java.util.HashMap;
import java.util.Map;

@E2Container
public class E2Immutable_4 {

    @Linked(absent = true)
    private final Map<String, String> map4;

    public E2Immutable_4(Map<String, String> map4Param) {
        map4 = new HashMap<>(map4Param); // not linked
    }

    public String get4(String input) {
        return map4.get(input);
    }

    @E2Container
    public Map<String, String> getMap4() {
        return Map.copyOf(map4);
    }
}
