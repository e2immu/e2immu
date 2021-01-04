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

import com.google.common.collect.ImmutableMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Linked;

import java.util.HashMap;
import java.util.Map;

@E2Container
public class E2Immutable_5<T> {

    @Linked(absent = true)
    private final Map<String, T> map5;

    public E2Immutable_5(Map<String, T> map5Param) {
        map5 = new HashMap<>(map5Param); // not linked
    }

    public T get5(String input) {
        return map5.get(input);
    }

    @E2Container
    public Map<String, T> getMap5() {
        return ImmutableMap.copyOf(map5);
    }
}
