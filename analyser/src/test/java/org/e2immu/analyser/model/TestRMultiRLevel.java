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

public class TestRMultiRLevel {

    @Test
    public void testLookup() {
        assertEquals(1, DEPENDENT_DV.value());
        assertEquals(5, EFFECTIVELY_FINAL_FIELDS_DV.value());
        assertEquals(5, INDEPENDENT_HC_DV.value());

        assertEquals(10, EVENTUALLY_IMMUTABLE_HC_DV.value());
        assertEquals(11, EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV.value());
        assertEquals(12, EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV.value());
        assertEquals(13, EFFECTIVELY_IMMUTABLE_HC_DV.value());
        assertEquals(21, EFFECTIVELY_IMMUTABLE_DV.value());
        assertEquals(21, INDEPENDENT_DV.value());

        assertEquals(13, EFFECTIVELY_CONTENT_NOT_NULL_DV.value());
        assertEquals(2, composeImmutable(EVENTUAL, 0).value());
        assertEquals(2, EVENTUALLY_FINAL_FIELDS_DV.value());
    }

    @Test
    public void testLevel() {
        assertEquals(MUTABLE.level, level(EFFECTIVELY_FINAL_FIELDS_DV));
        assertEquals(NOT_NULL_1.level, level(EFFECTIVELY_CONTENT_NOT_NULL_DV));
        assertEquals(IMMUTABLE_HC.level, level(EVENTUALLY_IMMUTABLE_HC_DV));
        assertEquals(IMMUTABLE_HC.level, level(EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV));
        assertEquals(MUTABLE.level, level(EVENTUALLY_FINAL_FIELDS_DV)); // we know about E2IMMUTABLE: FALSE

        assertEquals(INDEPENDENT_HC.level, level(INDEPENDENT_HC_DV));
    }

    @Test
    public void testEffectiveL1() {
        assertEquals(EFFECTIVE, effectiveAtFinalFields(EFFECTIVELY_IMMUTABLE_HC_DV));
        assertEquals(EFFECTIVE, effectiveAtFinalFields(EFFECTIVELY_FINAL_FIELDS_DV));
        assertEquals(EFFECTIVE, effectiveAtFinalFields(EFFECTIVELY_IMMUTABLE_DV));
        assertEquals(EVENTUAL, effectiveAtFinalFields(EVENTUALLY_FINAL_FIELDS_DV));
        assertEquals(EVENTUAL, effectiveAtFinalFields(EVENTUALLY_IMMUTABLE_HC_DV));
        assertEquals(FALSE, effectiveAtFinalFields(MUTABLE_DV));
    }

    @Test
    public void testEffectiveL2() {
        assertEquals(EFFECTIVE, effectiveAtImmutableLevel(EFFECTIVELY_IMMUTABLE_HC_DV));
        assertEquals(FALSE, effectiveAtImmutableLevel(EFFECTIVELY_FINAL_FIELDS_DV));
        assertEquals(EFFECTIVE, effectiveAtImmutableLevel(EFFECTIVELY_IMMUTABLE_DV));
        assertEquals(FALSE, effectiveAtImmutableLevel(EVENTUALLY_FINAL_FIELDS_DV));
        assertEquals(EVENTUAL, effectiveAtImmutableLevel(EVENTUALLY_IMMUTABLE_HC_DV));
        assertEquals(FALSE, effectiveAtImmutableLevel(MUTABLE_DV));
        assertEquals(EVENTUAL, effectiveAtImmutableLevel(EVENTUALLY_IMMUTABLE_DV));
    }

    @Test
    public void testIsBefore() {
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_IMMUTABLE_HC_DV));
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_FINAL_FIELDS_DV));
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV));
        assertTrue(isBeforeThrowWhenNotEventual(EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV));

        assertThrows(UnsupportedOperationException.class,
                () -> isBeforeThrowWhenNotEventual(EFFECTIVELY_IMMUTABLE_HC_DV));
        assertThrows(UnsupportedOperationException.class,
                () -> isBeforeThrowWhenNotEventual(EFFECTIVELY_FINAL_FIELDS_DV));
        assertFalse(isBeforeThrowWhenNotEventual(EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV));
        assertFalse(isBeforeThrowWhenNotEventual(EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV));
    }

    @Test
    public void testIsAfter() {
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_IMMUTABLE_HC_DV));
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_FINAL_FIELDS_DV));
        assertFalse(isAfterThrowWhenNotEventual(EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV));
        assertFalse(isAfterThrowWhenNotEventual(EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV));
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV));
        assertTrue(isAfterThrowWhenNotEventual(EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV));
    }

    @Test
    public void testOneLevelLess() {
        assertEquals(EFFECTIVELY_NOT_NULL_DV, composeOneLevelLessNotNull(EFFECTIVELY_CONTENT_NOT_NULL_DV));
    }

    @Test
    public void testDvMin() {
        assertEquals(NOT_CONTAINER_DV, NOT_CONTAINER_DV.minIgnoreNotInvolved(NOT_INVOLVED_DV));
        assertEquals(NOT_CONTAINER_DV, NOT_INVOLVED_DV.minIgnoreNotInvolved(NOT_CONTAINER_DV));
        assertEquals(NOT_CONTAINER_DV, NOT_CONTAINER_DV.minIgnoreNotInvolved(CONTAINER_DV));
        assertEquals(CONTAINER_DV, NOT_INVOLVED_DV.minIgnoreNotInvolved(CONTAINER_DV));
    }
}
