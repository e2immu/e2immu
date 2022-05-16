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
import static org.e2immu.analyser.model.MultiLevel.Effective.*;
import static org.e2immu.analyser.model.MultiLevel.Level.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMultiLevel {

    @Test
    public void testLookup() {
        assertEquals(1, DEPENDENT_DV.value());
        assertEquals(5, EFFECTIVELY_E1IMMUTABLE_DV.value());
        assertEquals(5, INDEPENDENT_1_DV.value());

        assertEquals(10, EVENTUALLY_E2IMMUTABLE_DV.value());
        assertEquals(11, EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV.value());
        assertEquals(12, EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV.value());
        assertEquals(13, EFFECTIVELY_E2IMMUTABLE_DV.value());
        assertEquals(805, EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV.value());
        assertEquals(805, INDEPENDENT_DV.value());

        assertEquals(13, EFFECTIVELY_CONTENT_NOT_NULL_DV.value());
        assertEquals(2, composeImmutable(EVENTUAL, 0).value());
        assertEquals(2, EVENTUALLY_E1IMMUTABLE_DV.value());
    }

    @Test
    public void testLevel() {
        assertEquals(IMMUTABLE_1.level, level(EFFECTIVELY_E1IMMUTABLE_DV));
        assertEquals(NOT_NULL_1.level, level(EFFECTIVELY_CONTENT_NOT_NULL_DV));
        assertEquals(NOT_NULL_2.level, level(EFFECTIVELY_CONTENT2_NOT_NULL_DV));
        assertEquals(IMMUTABLE_2.level, level(EVENTUALLY_E2IMMUTABLE_DV));
        assertEquals(IMMUTABLE_2.level, level(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV));
        assertEquals(IMMUTABLE_1.level, level(EVENTUALLY_E1IMMUTABLE_DV)); // we know about E2IMMUTABLE: FALSE

        assertEquals(INDEPENDENT_1.level, level(INDEPENDENT_1_DV));
    }

    @Test
    public void testEffectiveL1() {
        assertEquals(EFFECTIVE, effectiveAtLevel1Immutable(EFFECTIVELY_E2IMMUTABLE_DV));
        assertEquals(EFFECTIVE, effectiveAtLevel1Immutable(EFFECTIVELY_E1IMMUTABLE_DV));
        assertEquals(EFFECTIVE, effectiveAtLevel1Immutable(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV));
        assertEquals(EVENTUAL, effectiveAtLevel1Immutable(EVENTUALLY_E1IMMUTABLE_DV));
        assertEquals(EVENTUAL, effectiveAtLevel1Immutable(EVENTUALLY_E2IMMUTABLE_DV));
        assertEquals(FALSE, effectiveAtLevel1Immutable(MUTABLE_DV));
    }

    @Test
    public void testEffectiveL2() {
        assertEquals(EFFECTIVE, effectiveAtLevel2PlusImmutable(EFFECTIVELY_E2IMMUTABLE_DV));
        assertEquals(FALSE, effectiveAtLevel2PlusImmutable(EFFECTIVELY_E1IMMUTABLE_DV));
        assertEquals(EFFECTIVE, effectiveAtLevel2PlusImmutable(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV));
        assertEquals(FALSE, effectiveAtLevel2PlusImmutable(EVENTUALLY_E1IMMUTABLE_DV));
        assertEquals(EVENTUAL, effectiveAtLevel2PlusImmutable(EVENTUALLY_E2IMMUTABLE_DV));
        assertEquals(FALSE, effectiveAtLevel2PlusImmutable(MUTABLE_DV));
        assertEquals(EVENTUAL, effectiveAtLevel2PlusImmutable(EVENTUALLY_RECURSIVELY_IMMUTABLE_DV));
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
        assertEquals(EFFECTIVELY_CONTENT_NOT_NULL_DV, composeOneLevelLessNotNull(EFFECTIVELY_CONTENT2_NOT_NULL_DV));
        assertEquals(EFFECTIVELY_NOT_NULL_DV, composeOneLevelLessNotNull(EFFECTIVELY_CONTENT_NOT_NULL_DV));
        assertEquals(INDEPENDENT_1_DV, composeOneLevelLessIndependent(INDEPENDENT_2_DV));
    }

    @Test
    public void testDvMin() {
        assertEquals(NOT_CONTAINER_DV, NOT_CONTAINER_DV.minIgnoreNotInvolved(NOT_INVOLVED_DV));
        assertEquals(NOT_CONTAINER_DV, NOT_INVOLVED_DV.minIgnoreNotInvolved(NOT_CONTAINER_DV));
        assertEquals(NOT_CONTAINER_DV, NOT_CONTAINER_DV.minIgnoreNotInvolved(CONTAINER_DV));
        assertEquals(CONTAINER_DV, NOT_INVOLVED_DV.minIgnoreNotInvolved(CONTAINER_DV));
    }

    @Test
    public void testComposeOneLevelMoreImmutable() {
        assertEquals(EFFECTIVELY_E2IMMUTABLE_DV, composeOneLevelMoreImmutable(MUTABLE_DV));
    }
}
