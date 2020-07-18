package org.e2immu.analyser.model;

import org.e2immu.annotation.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.e2immu.analyser.model.GenerateAnnotationsImmutable.TRUE;
import static org.e2immu.analyser.model.GenerateAnnotationsImmutable.generate;
import static org.e2immu.analyser.model.MultiLevel.*;

public class TestGenerateAnnotationsImmutable {

    @Test
    public void testBefore() {
        Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK, 0, false));
        Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK, 1, false));
        Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 0, false));
        Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 1, false));
        Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE, E1Immutable.class, TRUE),
                generate(EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 0, false));
        Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE, E1Container.class, TRUE),
                generate(EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, 1, false));
        try {
            Assert.assertEquals(Map.of(BeforeImmutableMark.class, TRUE),
                    generate(EVENTUALLY_E1IMMUTABLE_BEFORE_MARK, 0, true));
            Assert.fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testAfter() {
        Assert.assertEquals(Map.of(E1Immutable.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK, 0, false));
        Assert.assertEquals(Map.of(E1Container.class, TRUE),
                generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK, 1, false));
        Assert.assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_AFTER_MARK, 0, false));
        Assert.assertEquals(Map.of(E2Container.class, TRUE),
                generate(EVENTUALLY_E2IMMUTABLE_AFTER_MARK, 1, false));
        try {
            Assert.assertEquals(Map.of(E1Immutable.class, TRUE),
                    generate(EVENTUALLY_E1IMMUTABLE_AFTER_MARK, 0, true));
            Assert.fail();
        } catch (RuntimeException rte) {
            // OK!
        }
    }

    @Test
    public void testEventual() {

    }

    @Test
    public void testEffective() {
        Assert.assertTrue(generate(EFFECTIVELY_E1IMMUTABLE, 0, false).isEmpty());
        Assert.assertTrue(generate(EFFECTIVELY_E1IMMUTABLE, 1, false).isEmpty());

        Assert.assertEquals(Map.of(E1Immutable.class, TRUE),
                generate(EFFECTIVELY_E1IMMUTABLE, 0, true));
        Assert.assertEquals(Map.of(E1Container.class, TRUE),
                generate(EFFECTIVELY_E1IMMUTABLE, 1, true));

        Assert.assertTrue(generate(EFFECTIVELY_E2IMMUTABLE, 0, false).isEmpty());
        Assert.assertTrue(generate(EFFECTIVELY_E2IMMUTABLE, 1, false).isEmpty());

        Assert.assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 0, false, null, true));
        Assert.assertEquals(Map.of(E2Container.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 1, false, "abc", true));

        Assert.assertEquals(Map.of(E2Immutable.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 0, true));
        Assert.assertEquals(Map.of(E2Container.class, TRUE),
                generate(EFFECTIVELY_E2IMMUTABLE, 1, true, "xxx", true));
    }

    @Test
    public void testOnlyContainer() {
        Assert.assertTrue(generate(MUTABLE, 0, false).isEmpty());
        Assert.assertTrue(generate(MUTABLE, 1, false).isEmpty());
        Assert.assertTrue(generate(MUTABLE, 0, true).isEmpty());
        Assert.assertEquals(Map.of(Container.class, TRUE), generate(MUTABLE, 1, true));
    }
}
