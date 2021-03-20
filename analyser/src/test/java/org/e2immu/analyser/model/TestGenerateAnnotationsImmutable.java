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

import org.e2immu.annotation.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutable.TRUE;
import static org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutable.generate;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestGenerateAnnotationsImmutable {

    @Test
    public void testBefore() {
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK, 0, false));
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK, 1, false));
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 0, false));
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 1, false));
        assertEquals(Map.of(BeforeMark.class, TRUE, E1Immutable.class, TRUE),
                generate(EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 0, false));
        assertEquals(Map.of(BeforeMark.class, TRUE, E1Container.class, TRUE),
                generate(EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 1, false));
        try {
            assertEquals(Map.of(BeforeMark.class, TRUE),
                    generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK, 0, true));
            fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testAfter() {
        assertEquals(Map.of(E1Immutable.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK, 0, false));
        assertEquals(Map.of(E1Container.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK, 1, false));
        assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_AFTER_MARK, 0, false));
        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_AFTER_MARK, 1, false));
        try {
            assertEquals(Map.of(E1Immutable.class, TRUE),
                    generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK, 0, true));
            fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testEventual() {
        assertEquals(Map.of(E1Immutable.class, Map.of("after", "mark")),
                generate(EVENTUALLY_E1IMMUTABLE, 0, true, false, "mark", false));
        assertEquals(Map.of(E2Immutable.class, Map.of("after", "mark")),
                generate(EVENTUALLY_E2IMMUTABLE, 0, true, false, "mark", false));
        assertEquals(Map.of(E1Container.class, Map.of("after", "mark")),
                generate(EVENTUALLY_E1IMMUTABLE, 1, true, true, "mark", true));
        assertEquals(Map.of(E2Container.class, Map.of("after", "mark2")),
                generate(EVENTUALLY_E2IMMUTABLE, 1, true, true, "mark2", true));

        assertTrue(generate(EVENTUALLY_E1IMMUTABLE, 0, false, false, "mark", false).isEmpty());
        assertTrue(generate(EVENTUALLY_E2IMMUTABLE, 1, false, false, "mark", false).isEmpty());

        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE, 1, false, false, "mark2", true));
    }

    @Test
    public void testEffective() {
        assertTrue(generate(EFFECTIVELY_E1IMMUTABLE, 0, false).isEmpty());
        assertTrue(generate(EFFECTIVELY_E1IMMUTABLE, 1, false).isEmpty());

        assertEquals(Map.of(E1Immutable.class, TRUE),
                generate(EFFECTIVELY_E1IMMUTABLE, 0, true));
        assertEquals(Map.of(E1Container.class, TRUE),
                generate(EFFECTIVELY_E1IMMUTABLE, 1, true));

        assertTrue(generate(EFFECTIVELY_E2IMMUTABLE, 0, false).isEmpty());
        assertTrue(generate(EFFECTIVELY_E2IMMUTABLE, 1, false).isEmpty());

        assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 0, false, false, null, true));
        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 1, false, false, "abc", true));

        assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 0, true));
        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 1, true, false,  "xxx", true));
    }

    @Test
    public void testOnlyContainer() {
        assertTrue(generate(MUTABLE, 0, false).isEmpty());
        assertTrue(generate(MUTABLE, 1, false).isEmpty());
        assertEquals(Map.of(MutableModifiesArguments.class, TRUE), generate(MUTABLE, 0, true));
    }
}
