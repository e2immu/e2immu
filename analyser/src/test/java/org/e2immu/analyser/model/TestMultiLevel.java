package org.e2immu.analyser.model;

import org.junit.Assert;
import org.junit.Test;

import static org.e2immu.analyser.model.MultiLevel.*;

public class TestMultiLevel {

    @Test
    public void testLookup() {
        Assert.assertEquals(5, EFFECTIVELY_E1IMMUTABLE);
        Assert.assertEquals(18, EVENTUALLY_E2IMMUTABLE);
        Assert.assertEquals(27, EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        Assert.assertEquals(36, EVENTUALLY_E2IMMUTABLE_AFTER_MARK);
        Assert.assertEquals(45, EFFECTIVELY_E2IMMUTABLE);

        Assert.assertEquals(45, EFFECTIVELY_CONTENT_NOT_NULL);
        Assert.assertEquals(10, compose(EVENTUAL, FALSE));
        Assert.assertEquals(10, EVENTUALLY_E1IMMUTABLE);
    }

    @Test
    public void testCompose() {
        Assert.assertEquals(EVENTUAL_BEFORE + FACTOR * EVENTUAL_BEFORE, EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        Assert.assertEquals(EFFECTIVE + FACTOR * EVENTUAL_BEFORE, EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        Assert.assertEquals(EFFECTIVE + FACTOR * EFFECTIVE, EFFECTIVELY_E2IMMUTABLE);
        Assert.assertEquals(EFFECTIVE + FACTOR * EFFECTIVE, EFFECTIVELY_E2IMMUTABLE + FACTOR * FACTOR * EFFECTIVE, EFFECTIVELY_CONTENT2_NOT_NULL);
    }

    @Test
    public void testLevel() {
        Assert.assertEquals(E1IMMUTABLE, level(EFFECTIVELY_E1IMMUTABLE));
        Assert.assertEquals(NOT_NULL_1, level(EFFECTIVELY_CONTENT_NOT_NULL));
        Assert.assertEquals(NOT_NULL_2, level(EFFECTIVELY_CONTENT2_NOT_NULL));
        Assert.assertEquals(E2IMMUTABLE, level(EVENTUALLY_E2IMMUTABLE));
        Assert.assertEquals(E2IMMUTABLE, level(EVENTUALLY_E2IMMUTABLE_BEFORE_MARK));
        Assert.assertEquals(E1IMMUTABLE, level(EVENTUALLY_E1IMMUTABLE));
    }

    @Test
    public void testValue() {
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL));
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL_1));
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL_2));
        Assert.assertEquals(DELAY, value(EFFECTIVELY_CONTENT2_NOT_NULL, NOT_NULL_3));

        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT_NOT_NULL, NOT_NULL));
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT_NOT_NULL, NOT_NULL_1));
        Assert.assertEquals(DELAY, value(EFFECTIVELY_CONTENT_NOT_NULL, NOT_NULL_2));

        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_NOT_NULL, NOT_NULL));
        Assert.assertEquals(DELAY, value(EFFECTIVELY_NOT_NULL, NOT_NULL_1));
    }

    @Test
    public void testDelayToFalse() {
        Assert.assertEquals(FALSE, MultiLevel.delayToFalse(0));
    }

    @Test
    public void testEventual() {
        Assert.assertEquals(DELAY, MultiLevel.eventual(Level.DELAY, true));
        Assert.assertEquals(DELAY, MultiLevel.eventual(Level.DELAY, false));
        Assert.assertEquals(DELAY, MultiLevel.eventual(DELAY, true));
        Assert.assertEquals(DELAY, MultiLevel.eventual(DELAY, false));
    }
}
