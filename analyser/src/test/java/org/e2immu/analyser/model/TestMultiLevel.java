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
        assertEquals(5, EFFECTIVELY_E1IMMUTABLE);
        assertEquals(18, EVENTUALLY_E2IMMUTABLE);
        assertEquals(27, EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        assertEquals(36, EVENTUALLY_E2IMMUTABLE_AFTER_MARK);
        assertEquals(45, EFFECTIVELY_E2IMMUTABLE);

        assertEquals(45, EFFECTIVELY_CONTENT_NOT_NULL);
        assertEquals(10, compose(EVENTUAL, FALSE));
        assertEquals(10, EVENTUALLY_E1IMMUTABLE);
    }

    @Test
    public void testCompose() {
        assertEquals(EVENTUAL_BEFORE + FACTOR * EVENTUAL_BEFORE, EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        assertEquals(EFFECTIVE + FACTOR * EVENTUAL_BEFORE, EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        assertEquals(EFFECTIVE + FACTOR * EFFECTIVE, EFFECTIVELY_E2IMMUTABLE);
        assertEquals(EFFECTIVE + FACTOR * EFFECTIVE, EFFECTIVELY_E2IMMUTABLE + FACTOR * FACTOR * EFFECTIVE, EFFECTIVELY_CONTENT2_NOT_NULL);
    }

    @Test
    public void testLevel() {
        assertEquals(E1IMMUTABLE, level(EFFECTIVELY_E1IMMUTABLE));
        assertEquals(NOT_NULL_1, level(EFFECTIVELY_CONTENT_NOT_NULL));
        assertEquals(NOT_NULL_2, level(EFFECTIVELY_CONTENT2_NOT_NULL));
        assertEquals(E2IMMUTABLE, level(EVENTUALLY_E2IMMUTABLE));
        assertEquals(E2IMMUTABLE, level(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK));
        assertEquals(E2IMMUTABLE, level(EVENTUALLY_E1IMMUTABLE)); // we know about E2IMMUTABLE: FALSE
    }

    @Test
    public void testLevelBetterThanFalse() {
        assertEquals(E1IMMUTABLE, levelBetterThanFalse(EFFECTIVELY_E1IMMUTABLE));
        assertEquals(NOT_NULL_1, levelBetterThanFalse(EFFECTIVELY_CONTENT_NOT_NULL));
        assertEquals(NOT_NULL_2, levelBetterThanFalse(EFFECTIVELY_CONTENT2_NOT_NULL));
        assertEquals(E2IMMUTABLE, levelBetterThanFalse(EVENTUALLY_E2IMMUTABLE));
        assertEquals(E2IMMUTABLE, levelBetterThanFalse(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK));
        assertEquals(E1IMMUTABLE, levelBetterThanFalse(EVENTUALLY_E1IMMUTABLE));
    }

    @Test
    public void testValue() {
        assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL));
        assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL_1));
        assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL_2));
        assertEquals(DELAY, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL_3));

        assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT_NOT_NULL, NOT_NULL));
        assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT_NOT_NULL, NOT_NULL_1));
        assertEquals(DELAY, value(EFFECTIVELY_CONTENT_NOT_NULL, NOT_NULL_2));

        assertEquals(EFFECTIVE, value(EFFECTIVELY_NOT_NULL, NOT_NULL));
        assertEquals(DELAY, value(EFFECTIVELY_NOT_NULL, NOT_NULL_1));
    }

    @Test
    public void testDelayToFalse() {
        assertEquals(FALSE, delayToFalse(0));
    }

    @Test
    public void testEventual() {
        assertEquals(DELAY, eventual(Level.DELAY, true));
        assertEquals(DELAY, eventual(Level.DELAY, false));
        assertEquals(DELAY, eventual(DELAY, true));
        assertEquals(DELAY, eventual(DELAY, false));
    }

    @Test
    public void testIsBefore() {
        assertTrue(isBefore(EVENTUALLY_E2IMMUTABLE));
        assertTrue(isBefore(EVENTUALLY_E1IMMUTABLE));
        assertTrue(isBefore(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK));
        assertTrue(isBefore(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK));

        assertFalse(isBefore(EFFECTIVELY_E2IMMUTABLE));
        assertFalse(isBefore(EFFECTIVELY_E1IMMUTABLE));
        assertFalse(isBefore(EVENTUALLY_E1IMMUTABLE_AFTER_MARK));
        assertFalse(isBefore(EVENTUALLY_E2IMMUTABLE_AFTER_MARK));
    }

    @Test
    public void testIsAfter() {
        assertFalse(isAfter(EVENTUALLY_E2IMMUTABLE));
        assertFalse(isAfter(EVENTUALLY_E1IMMUTABLE));
        assertFalse(isAfter(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK));
        assertFalse(isAfter(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK));

        assertTrue(isAfter(EFFECTIVELY_E2IMMUTABLE));
        assertTrue(isAfter(EFFECTIVELY_E1IMMUTABLE));
        assertTrue(isAfter(EVENTUALLY_E1IMMUTABLE_AFTER_MARK));
        assertTrue(isAfter(EVENTUALLY_E2IMMUTABLE_AFTER_MARK));
    }

    @Test
    public void testOneLevelLess() {
        assertEquals(EFFECTIVELY_CONTENT_NOT_NULL, oneLevelLess(EFFECTIVELY_CONTENT2_NOT_NULL));
        assertEquals(EFFECTIVELY_NOT_NULL, oneLevelLess(EFFECTIVELY_CONTENT_NOT_NULL));
        assertEquals(EFFECTIVELY_E1IMMUTABLE, oneLevelLess(EFFECTIVELY_E2IMMUTABLE));
    }
}
