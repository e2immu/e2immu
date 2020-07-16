package org.e2immu.analyser.model;

import org.junit.Assert;
import org.junit.Test;

import static org.e2immu.analyser.model.Level.*;

public class TestLevel {

    @Test
    public void testCompose() {
        Assert.assertEquals(EVENTUAL_BEFORE + 8 * EVENTUAL_BEFORE, EVENTUALLY_E1_E2IMMUTABLE_BEFORE_MARK);
        Assert.assertEquals(EFFECTIVE + 8 * EVENTUAL_BEFORE, EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK);
        Assert.assertEquals(EFFECTIVE + 8 * EFFECTIVE, EFFECTIVELY_E2IMMUTABLE);
        Assert.assertEquals(EFFECTIVE + 8 * EFFECTIVE, EFFECTIVELY_E2IMMUTABLE + 64 * EFFECTIVE, EFFECTIVELY_CONTENT2_NOT_NULL);
    }

    @Test
    public void testLevel() {
        Assert.assertEquals(0, level(EFFECTIVELY_E1IMMUTABLE));
        Assert.assertEquals(1, level(EFFECTIVELY_CONTENT_NOT_NULL));
        Assert.assertEquals(2, level(EFFECTIVELY_CONTENT2_NOT_NULL));
        Assert.assertEquals(1, level(EVENTUALLY_E1_E2IMMUTABLE));
        Assert.assertEquals(1, level(EVENTUALLY_E1_E2IMMUTABLE_BEFORE_MARK));
        Assert.assertEquals(0, level(EVENTUALLY_E1IMMUTABLE));
    }

    @Test
    public void testValue() {
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, 0));
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, 1));
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT2_NOT_NULL, 2));
        Assert.assertEquals(FALSE, value(EFFECTIVELY_CONTENT2_NOT_NULL, 3));

        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT_NOT_NULL, 0));
        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_CONTENT_NOT_NULL, 1));
        Assert.assertEquals(FALSE, value(EFFECTIVELY_CONTENT_NOT_NULL, 2));

        Assert.assertEquals(EFFECTIVE, value(EFFECTIVELY_NOT_NULL, 0));
        Assert.assertEquals(NULLABLE, value(EFFECTIVELY_NOT_NULL, 1));
    }
}
