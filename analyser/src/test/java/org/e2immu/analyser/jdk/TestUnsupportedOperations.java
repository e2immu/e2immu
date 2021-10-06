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

package org.e2immu.analyser.jdk;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestUnsupportedOperations {

    // the iterator of Stream is not modifying
    @Test
    public void test1() {
        List<Integer> list = new ArrayList<>();
        Collections.addAll(list, 1, 2, 3, 4, 5);
        assertEquals(5, list.size());
        Iterator<Integer> it = list.stream().iterator();
        assertTrue(it.hasNext());
        assertThrows(UnsupportedOperationException.class, it::remove);
    }

    // we can edit entries in HashMap
    @Test
    public void test2() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "b");
        for (Map.Entry<String, String> e : map.entrySet()) {
            if ("a".equals(e.getKey())) e.setValue("c");
        }
        assertEquals("c", map.get("a"));
    }

    // ... and TreeMap's entrySet, but not in TreeMap's floorEntry, higherEntry etc.
    @Test
    public void test3() {
        TreeMap<String, String> map = new TreeMap<>();
        map.put("a", "b");
        for (Map.Entry<String, String> e : map.entrySet()) {
            if ("a".equals(e.getKey())) e.setValue("c");
        }
        assertEquals("c", map.get("a"));

        Map.Entry<String, String> floor = map.floorEntry("a");
        assertEquals("c", floor.getValue());
        assertThrows(UnsupportedOperationException.class, () -> floor.setValue("d"));
        assertEquals("c", map.get("a"));
    }
}
