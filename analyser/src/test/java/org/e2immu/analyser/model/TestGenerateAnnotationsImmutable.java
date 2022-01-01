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

import org.e2immu.analyser.analyser.DV;
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
                generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV, DV.FALSE_DV, false));
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV, DV.TRUE_DV, false));
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV, DV.FALSE_DV, false));
        assertEquals(Map.of(BeforeMark.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV, DV.TRUE_DV, false));
        
        try {
            assertEquals(Map.of(BeforeMark.class, TRUE),
                    generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV, DV.FALSE_DV, true));
            fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testAfter() {
        assertEquals(Map.of(E1Immutable.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV, DV.FALSE_DV, false));
        assertEquals(Map.of(E1Container.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV, DV.TRUE_DV, false));
        assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV, DV.FALSE_DV, false));
        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV, DV.TRUE_DV, false));
        try {
            assertEquals(Map.of(E1Immutable.class, TRUE),
                    generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK_DV, DV.FALSE_DV, true));
            fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testEventual() {
        assertEquals(Map.of(E1Immutable.class, Map.of("after", "mark")),
                generate(EVENTUALLY_E1IMMUTABLE_DV, DV.FALSE_DV, true, false, "mark", false));
        assertEquals(Map.of(E2Immutable.class, Map.of("after", "mark")),
                generate(EVENTUALLY_E2IMMUTABLE_DV, DV.FALSE_DV, true, false, "mark", false));
        assertEquals(Map.of(E1Container.class, Map.of("after", "mark")),
                generate(EVENTUALLY_E1IMMUTABLE_DV, DV.TRUE_DV, true, true, "mark", true));
        assertEquals(Map.of(E2Container.class, Map.of("after", "mark2")),
                generate(EVENTUALLY_E2IMMUTABLE_DV, DV.TRUE_DV, true, true, "mark2", true));

        assertTrue(generate(EVENTUALLY_E1IMMUTABLE_DV, DV.FALSE_DV, false, false, "mark", false).isEmpty());
        assertTrue(generate(EVENTUALLY_E2IMMUTABLE_DV, DV.TRUE_DV, false, false, "mark", false).isEmpty());

        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_DV, DV.TRUE_DV, false, false, "mark2", true));
        assertEquals(Map.of(ERContainer.class, TRUE),
                generate(EVENTUALLY_RECURSIVELY_IMMUTABLE_DV, DV.TRUE_DV, false, false, "mark2", true));
    }

    @Test
    public void testEffective() {
        assertTrue(generate(EFFECTIVELY_E1IMMUTABLE_DV, DV.FALSE_DV, false).isEmpty());
        assertTrue(generate(EFFECTIVELY_E1IMMUTABLE_DV, DV.TRUE_DV, false).isEmpty());

        assertEquals(Map.of(E1Immutable.class, TRUE),
                generate(EFFECTIVELY_E1IMMUTABLE_DV, DV.FALSE_DV, true));
        assertEquals(Map.of(E1Container.class, TRUE),
                generate(EFFECTIVELY_E1IMMUTABLE_DV, DV.TRUE_DV, true));

        assertTrue(generate(EFFECTIVELY_E2IMMUTABLE_DV, DV.FALSE_DV, false).isEmpty());
        assertTrue(generate(EFFECTIVELY_E2IMMUTABLE_DV, DV.TRUE_DV, false).isEmpty());

        assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE_DV, DV.FALSE_DV, false, false, null, true));
        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE_DV, DV.TRUE_DV, false, false, "abc", true));

        assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE_DV, DV.FALSE_DV, true));
        assertEquals(Map.of(E2Container.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE_DV, DV.TRUE_DV, true, false, "xxx", true));

        assertEquals(Map.of(E2Immutable.class, Map.of("level", "3")),
                generate(EFFECTIVELY_E3IMMUTABLE_DV, DV.FALSE_DV, true));
        assertEquals(Map.of(E2Container.class, Map.of("level", "3")),
                generate(EFFECTIVELY_E3IMMUTABLE_DV, DV.TRUE_DV, true, false, "xxx", true));

        assertEquals(Map.of(ERContainer.class, TRUE),
                generate(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV.TRUE_DV, true, false, "xxx", true));

        /*
        don't understand why the objects themselves are not equal (it's supposed to be value based
        java.util.ImmutableCollections$Map1@759c5a06<{interface org.e2immu.annotation.E2Immutable={recursive=true}}> but was:
        java.util.ImmutableCollections$Map1@244d9db0<{interface org.e2immu.annotation.E2Immutable={recursive=true}}>
        */
        assertEquals(Map.of(E2Immutable.class, Map.of("recursive", "true")).toString(),
                generate(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV.FALSE_DV, true).toString());
    }


    @Test
    public void testOnlyContainer() {
        assertTrue(generate(MUTABLE_DV, DV.FALSE_DV, false).isEmpty());
        assertTrue(generate(MUTABLE_DV, DV.TRUE_DV, false).isEmpty());
        assertEquals(Map.of(MutableModifiesArguments.class, TRUE), generate(MUTABLE_DV, DV.FALSE_DV, true));
    }
}
