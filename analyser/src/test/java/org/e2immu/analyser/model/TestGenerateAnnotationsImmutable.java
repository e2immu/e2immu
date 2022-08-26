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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Immutable;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.eventual.BeforeMark;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutableAndContainer.NO_PARAMS;
import static org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutableAndContainer.generate;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestGenerateAnnotationsImmutable {

    @Test
    public void testBefore() {
        assertEquals(Map.of(BeforeMark.class, NO_PARAMS),
                generate(EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV, MultiLevel.NOT_CONTAINER_DV, false));
        assertEquals(Map.of(BeforeMark.class, NO_PARAMS),
                generate(EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV, MultiLevel.CONTAINER_DV, false));
        assertEquals(Map.of(BeforeMark.class, NO_PARAMS),
                generate(EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV, MultiLevel.NOT_CONTAINER_DV, false));
        assertEquals(Map.of(BeforeMark.class, NO_PARAMS),
                generate(EVENTUALLY_IMMUTABLE_HC_BEFORE_MARK_DV, MultiLevel.CONTAINER_DV, false));

        try {
            assertEquals(Map.of(BeforeMark.class, NO_PARAMS),
                    generate(EVENTUALLY_FINAL_FIELDS_BEFORE_MARK_DV, MultiLevel.NOT_CONTAINER_DV, true));
            fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testAfter() {
        assertEquals(Map.of(FinalFields.class, NO_PARAMS),
                generate(EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV, MultiLevel.NOT_CONTAINER_DV, false));
        assertEquals(Map.of(FinalFields.class, NO_PARAMS, Container.class, NO_PARAMS),
                generate(EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV, MultiLevel.CONTAINER_DV, false));
        assertEquals(Map.of(Immutable.class, Map.of(HIDDEN_CONTENT, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV, MultiLevel.NOT_CONTAINER_DV, false));
        assertEquals(Map.of(ImmutableContainer.class, Map.of(HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV, MultiLevel.CONTAINER_DV, false));
        try {
            assertEquals(Map.of(FinalFields.class, NO_PARAMS),
                    generate(EVENTUALLY_FINAL_FIELDS_AFTER_MARK_DV, MultiLevel.NOT_CONTAINER_DV, true));
            fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }


    @Test
    public void testEventual() {
        // @FinalFields(after="mark")
        assertEquals(Map.of(FinalFields.class, Map.of(AFTER, "mark")),
                generate(EVENTUALLY_FINAL_FIELDS_DV, MultiLevel.NOT_CONTAINER_DV, true, "mark", true, true));

        // @Immutable(after="mark", hc=true)
        assertEquals(Map.of(Immutable.class, Map.of(AFTER, "mark", HIDDEN_CONTENT, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EVENTUALLY_IMMUTABLE_HC_DV, MultiLevel.NOT_CONTAINER_DV, true, "mark", true, true));

        // @FinalFields(after="mark") @Container
        assertEquals(Map.of(FinalFields.class, Map.of(AFTER, "mark"), Container.class, NO_PARAMS),
                generate(EVENTUALLY_FINAL_FIELDS_DV, MultiLevel.CONTAINER_DV, true, "mark", true, true));

        // @ImmutableContainer(after="mark", hc=true) @Container(implied=true) @Immutable(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(AFTER, "mark2", HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EVENTUALLY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, true, "mark2", true, true));

        // not better than formal has no effect, because of the eventual
        // @ImmutableContainer(after="mark", hc=true) @Container(implied=true) @Immutable(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(AFTER, "mark2", HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EVENTUALLY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, true, "mark2", false, false));
    }

    @Test
    public void testEffective() {
        // @FinalFields
        assertEquals(Map.of(FinalFields.class, NO_PARAMS),
                generate(EFFECTIVELY_FINAL_FIELDS_DV, MultiLevel.NOT_CONTAINER_DV, true, "mark", true, true));

        // @Immutable(hc=true)
        assertEquals(Map.of(Immutable.class, Map.of(HIDDEN_CONTENT, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_IMMUTABLE_HC_DV, MultiLevel.NOT_CONTAINER_DV, true, "mark", true, true));

        // @FinalFields @Container
        assertEquals(Map.of(FinalFields.class, NO_PARAMS, Container.class, NO_PARAMS),
                generate(EFFECTIVELY_FINAL_FIELDS_DV, MultiLevel.CONTAINER_DV, true, "mark", true, true));

        // @FinalFields @Container(implied=true)
        assertEquals(Map.of(FinalFields.class, NO_PARAMS, Container.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_FINAL_FIELDS_DV, MultiLevel.CONTAINER_DV, true, "mark", true, false));

        // @FinalFields(implied=true) @Container(implied=true)
        assertEquals(Map.of(FinalFields.class, Map.of(IMPLIED, true), Container.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_FINAL_FIELDS_DV, MultiLevel.CONTAINER_DV, false, "mark", false, false));

        // @ImmutableContainer(after="mark", hc=true) @Container(implied=true) @Immutable(implied=true) @FinalFields(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, true, "mark2", true, true));

        // not better than formal has no effect on a type
        // @ImmutableContainer(after="mark", hc=true) @Container(implied=true) @Immutable(implied=true) @FinalFields(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, true, "mark2", false, false));

        // but it has when not on a type
        // @ImmutableContainer(after="mark", hc=true) @Container(implied=true) @Immutable(implied=true) @FinalFields(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(IMPLIED, true, HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, false, "mark2", false, false));

        // container is better than formal, so implied=true on Container, but not on ImmutableContainer!
        // @ImmutableContainer(hc=true) @Container(implied=true) @Immutable(implied=true) @FinalFields(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, false, "mark2", false, true));

        // @ImmutableContainer(hc=true) @Container(implied=true) @Immutable(implied=true) @FinalFields(implied=true)
        assertEquals(Map.of(ImmutableContainer.class, Map.of(HIDDEN_CONTENT, true),
                        Container.class, Map.of(IMPLIED, true),
                        Immutable.class, Map.of(IMPLIED, true),
                        FinalFields.class, Map.of(IMPLIED, true)),
                generate(EFFECTIVELY_IMMUTABLE_HC_DV, MultiLevel.CONTAINER_DV, false, "mark2", true, false));
    }

    @Test
    public void testOnlyContainer() {
        assertTrue(generate(MUTABLE_DV, MultiLevel.NOT_CONTAINER_DV, false).isEmpty());
        assertEquals(Map.of(Container.class, NO_PARAMS), generate(MUTABLE_DV, MultiLevel.CONTAINER_DV, true));
    }
}
