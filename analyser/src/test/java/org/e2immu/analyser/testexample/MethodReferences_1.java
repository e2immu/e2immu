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

import org.e2immu.annotation.Dependent;
import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Modified;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@E1Immutable
public class MethodReferences_1 {

    private final Map<String, Integer> map = new HashMap<>();

    @Modified
    public void put(List<Integer> input) {
        input.forEach(this::put);
    }

    @Modified
    private void put(int i) {
        map.put("" + i, i);
    }

    @Dependent
    public Map<String, Integer> getMap() {
        return map;
    }
}
