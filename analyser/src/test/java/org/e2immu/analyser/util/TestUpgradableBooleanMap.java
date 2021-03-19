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

package org.e2immu.analyser.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestUpgradableBooleanMap {

    @Test
    public void test() {
        List<String> list = List.of("a", "b", "c", "d", "e");
        UpgradableBooleanMap<String> map = list.stream().map(s -> Map.of(s, "a".equals(s)))
                .flatMap(m -> m.entrySet().stream()).collect(UpgradableBooleanMap.collector());
        assertTrue(map.get("a"));
        assertFalse(map.get("b"));
        assertEquals(5L, map.stream().count());
    }

    @Test
    public void test2() {
        List<UpgradableBooleanMap<String>> list = List.of(UpgradableBooleanMap.of("a", true),
                UpgradableBooleanMap.of("b", false));
        UpgradableBooleanMap<String> map = list.stream().flatMap(UpgradableBooleanMap::stream).collect(UpgradableBooleanMap.collector());
        assertTrue(map.get("a"));
        assertFalse(map.get("b"));
        assertEquals(2L, map.stream().count());
    }
}
