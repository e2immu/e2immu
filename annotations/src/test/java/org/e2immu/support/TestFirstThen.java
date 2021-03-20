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

package org.e2immu.support;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestFirstThen {

    @Test
    public void test1() {
        FirstThen<String, Integer> a = new FirstThen<>("Hello");
        assertEquals("Hello", a.getFirst());
        try {
            a.get();
            fail();
        } catch (IllegalStateException e) {
            // normal behaviour
        }
        assertTrue(a.isFirst());
        assertFalse(a.isSet());
        a.set(34);
        assertEquals((Integer)34, a.get());
        try {
            a.getFirst();
            fail();
        } catch (IllegalStateException e) {
            // normal behaviour
        }
        assertFalse(a.isFirst());
        assertTrue(a.isSet());
        try {
            a.set(42);
            fail();
        } catch (IllegalStateException e) {
            // normal behaviour
        }
        assertNotEquals(null, a);
        FirstThen<String, Integer> b = new FirstThen<>("Hello");
        assertNotEquals(b, a);
        b.set(34);
        assertEquals(b, a);
    }
}
