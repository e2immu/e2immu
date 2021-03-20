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

public class TestEither {

    @Test
    public void test1() {
        Either<String, Integer> a = Either.left("Hello");
        assertEquals("Hello", a.getLeft());
        assertEquals("Hello", a.getLeftOrElse("There"));
        assertEquals((Integer)34, a.getRightOrElse(34));
        try {
            a.getRight();
            fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
        assertTrue(a.isLeft());
        assertFalse(a.isRight());

        Either<String, Integer> b = Either.right(42);
        assertEquals((Integer)42, b.getRight());
        assertEquals("There", b.getLeftOrElse("There"));
        assertEquals((Integer)42, b.getRightOrElse(34));
        try {
            b.getLeft();
            fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
        assertFalse(b.isLeft());
        assertTrue(b.isRight());

        assertNotEquals(null, b);
        assertNotEquals("string", b);

        assertNotEquals(a, b);
        Either<String, Integer> b2 = Either.right(42);
        assertEquals(b, b2);
        assertEquals(b.hashCode(), b2.hashCode());
    }
}
