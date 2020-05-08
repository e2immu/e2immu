package org.e2immu.analyser.model;

import org.junit.Assert;
import org.junit.Test;

import static org.e2immu.analyser.model.Level.*;

public class TestLevel {

    @Test
    public void testValue() {
        Assert.assertEquals(DELAY, Level.value(DELAY, 0));
        Assert.assertEquals(FALSE, Level.value(FALSE, 0));
        Assert.assertEquals(TRUE, Level.value(TRUE, 0));

        Assert.assertEquals(FALSE, Level.value(2, 1));
        Assert.assertEquals(TRUE, Level.value(3, 1));
        Assert.assertEquals(DELAY, Level.value(0, 1));
        Assert.assertEquals(DELAY, Level.value(1, 1));

    }
}
