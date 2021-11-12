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

package org.e2immu.analyser.model;

import org.junit.jupiter.api.Test;

import static org.e2immu.analyser.model.MultiLevel.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMultiLevel {

    @Test
    public void testLookup() {
        assertEquals(1, DEPENDENT);
        assertEquals(5, EFFECTIVELY_E1IMMUTABLE);
        assertEquals(5, INDEPENDENT_1);

        assertEquals(10, EVENTUALLY_E2IMMUTABLE_DV.value());
        assertEquals(11, EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV.value());
        assertEquals(12, EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV.value());
        assertEquals(13, EFFECTIVELY_E2IMMUTABLE);
        assertEquals(805, EFFECTIVELY_RECURSIVELY_IMMUTABLE);
        assertEquals(805, INDEPENDENT);

        assertEquals(13, EFFECTIVELY_CONTENT_NOT_NULL_DV.value());
        assertEquals(2, compose(EVENTUAL, 0));
        assertEquals(2, EVENTUALLY_E1IMMUTABLE_DV.value());
    }

    @Test
    public void testLevel() {
        assertEquals(LEVEL_1_IMMUTABLE, level(EFFECTIVELY_E1IMMUTABLE));
        assertEquals(NOT_NULL_1, level(EFFECTIVELY_CONTENT_NOT_NULL_DV));
        assertEquals(NOT_NULL_2, level(EFFECTIVELY_CONTENT2_NOT_NULL_DV));
        assertEquals(LEVEL_2_IMMUTABLE, level(EVENTUALLY_E2IMMUTABLE_DV));
        assertEquals(LEVEL_2_IMMUTABLE, level(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV));
        assertEquals(LEVEL_1_IMMUTABLE, level(EVENTUALLY_E1IMMUTABLE_DV)); // we know about E2IMMUTABLE: FALSE
    }

    @Test
    public void testValue() {
        assertEquals(EFFECTIVE, effectiveAtLevel(EFFECTIVELY_CONTENT2_NOT_NULL_DV, NOT_NULL));
        assertEquals(EFFECTIVE, effectiveAtLevel(EFFECTIVELY_CONTENT2_NOT_NULL_DV, NOT_NULL_1));
        assertEquals(EFFECTIVE, effectiveAtLevel(EFFECTIVELY_CONTENT2_NOT_NULL_DV, NOT_NULL_2));
        assertEquals(FALSE, effectiveAtLevel(EFFECTIVELY_CONTENT2_NOT_NULL_DV, NOT_NULL_3));

        assertEquals(EFFECTIVE, effectiveAtLevel(EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL));
        assertEquals(EFFECTIVE, effectiveAtLevel(EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL_1));
        assertEquals(FALSE, effectiveAtLevel(EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL_2));

        assertEquals(EFFECTIVE, effectiveAtLevel(EFFECTIVELY_NOT_NULL_DV, NOT_NULL));
        assertEquals(FALSE, effectiveAtLevel(EFFECTIVELY_NOT_NULL_DV, NOT_NULL_1));
    }

    @Test
    public void testIsBefore() {
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_E2IMMUTABLE_DV));
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_E1IMMUTABLE_DV));
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV));
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV));

        assertThrows(UnsupportedOperationException.class,
                () -> isBeforeThrowWhenNotEventual(EFFECTIVELY_E2IMMUTABLE_DV));
        assertThrows(UnsupportedOperationException.class,
                () -> isBeforeThrowWhenNotEventual(EFFECTIVELY_E1IMMUTABLE_DV));
        assertFalse(isBeforeThrowWhenNotEventual(EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV));
        assertFalse(isBeforeThrowWhenNotEventual(EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV));
    }

    @Test
    public void testIsAfter() {
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_E2IMMUTABLE_DV));
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_E1IMMUTABLE_DV));
        assertFalse(isAfterThrowWhenNotEventual(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV));
        assertFalse(isAfterThrowWhenNotEventual(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV));

        /*IMPROVE ! assertThrows(UnsupportedOperationException.class,
                () -> isAfterThrowWhenNotEventual(EFFECTIVELY_E2IMMUTABLE_DV));
        assertThrows(UnsupportedOperationException.class,
                () -> isAfterThrowWhenNotEventual(EFFECTIVELY_E1IMMUTABLE_DV));*/
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV));
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV));
    }

    @Test
    public void testOneLevelLess() {
        assertEquals(EFFECTIVELY_CONTENT_NOT_NULL_DV, composeOneLevelLess(EFFECTIVELY_CONTENT2_NOT_NULL_DV));
        assertEquals(EFFECTIVELY_NOT_NULL_DV, composeOneLevelLess(EFFECTIVELY_CONTENT_NOT_NULL_DV));
        assertEquals(EFFECTIVELY_E1IMMUTABLE_DV, composeOneLevelLess(EFFECTIVELY_E2IMMUTABLE_DV));
    }
}
