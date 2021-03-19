package org.e2immu.analyser.model;

import org.junit.jupiter.api.Test;

import static org.e2immu.analyser.model.MultiLevel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(FALSE, MultiLevel.delayToFalse(0));
    }

    @Test
    public void testEventual() {
        assertEquals(DELAY, MultiLevel.eventual(Level.DELAY, true));
        assertEquals(DELAY, MultiLevel.eventual(Level.DELAY, false));
        assertEquals(DELAY, MultiLevel.eventual(DELAY, true));
        assertEquals(DELAY, MultiLevel.eventual(DELAY, false));
    }
}
