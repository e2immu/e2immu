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

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestLazy {

    @Test
    public void test1() {
        AtomicInteger counter = new AtomicInteger();
        Lazy<String> lazy = new Lazy<>(() -> {
            counter.getAndIncrement();
            return "abc";
        });
        assertFalse(lazy.hasBeenEvaluated());

        String content = lazy.get();
        assertEquals("abc", content);
        assertEquals(1, counter.get());
        assertTrue(lazy.hasBeenEvaluated());

        // 2nd evaluation
        content = lazy.get();
        assertEquals("abc", content);
        assertEquals(1, counter.get());
        assertTrue(lazy.hasBeenEvaluated());
    }

    @Test
    public void test2() {
        try {
            new Lazy<String>(null);
            fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
    }

    @Test
    public void test3() {
        Lazy<String> lazy = new Lazy<>(() -> null);
        try {
            lazy.get();
            fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
    }
}
