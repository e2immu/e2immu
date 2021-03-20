/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
