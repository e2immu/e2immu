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
        Assert.assertEquals(EVENTUAL_BEFORE, Level.value(EVENTUAL_BEFORE, 0));
        Assert.assertEquals(EVENTUAL, Level.value(EVENTUAL, 0));
        Assert.assertEquals(EVENTUAL_AFTER, Level.value(EVENTUAL_AFTER, 0));
        Assert.assertEquals(EFFECTIVE, Level.value(EFFECTIVE, 0));

        Assert.assertEquals(DELAY, Level.value(-1, 1));
        Assert.assertEquals(FALSE, Level.value(0, 1));
        Assert.assertEquals(FALSE, Level.value(1, 1));
        Assert.assertEquals(FALSE, Level.value(2, 1));
        Assert.assertEquals(FALSE, Level.value(3, 1));
        Assert.assertEquals(FALSE, Level.value(4, 1));
        Assert.assertEquals(EVENTUAL_BEFORE, Level.value(5, 1));
        Assert.assertEquals(EVENTUAL, Level.value(6, 1));
        Assert.assertEquals(EVENTUAL_AFTER, Level.value(7, 1));
        Assert.assertEquals(EFFECTIVE, Level.value(8, 1));
        Assert.assertEquals(5, Level.value(9, 1));

        Assert.assertEquals(DELAY, Level.value(-1, 2));
        Assert.assertEquals(FALSE, Level.value(0, 2));
        Assert.assertEquals(FALSE, Level.value(8, 2));
        Assert.assertEquals(EVENTUAL_BEFORE, Level.value(9, 2));
        Assert.assertEquals(EVENTUAL, Level.value(10, 2));
        Assert.assertEquals(EVENTUAL_AFTER, Level.value(11, 2));
        Assert.assertEquals(EFFECTIVE, Level.value(12, 2));
        Assert.assertEquals(5, Level.value(13, 1));
    }

    @Test
    public void testCompose() {
        Assert.assertEquals(-1, Level.compose(DELAY, 0));
        Assert.assertEquals(FALSE, Level.compose(FALSE, 0));
        Assert.assertEquals(TRUE, Level.compose(TRUE, 0));
        Assert.assertEquals(EVENTUAL_BEFORE, Level.compose(EVENTUAL_BEFORE, 0));
        Assert.assertEquals(EVENTUAL, Level.compose(EVENTUAL, 0));
        Assert.assertEquals(EVENTUAL_AFTER, Level.compose(EVENTUAL_AFTER, 0));
        Assert.assertEquals(EFFECTIVE, Level.compose(EFFECTIVE, 0));

        Assert.assertEquals(5, Level.compose(EVENTUAL_BEFORE, 0));
        Assert.assertEquals(6, Level.compose(EVENTUAL, 0));
        Assert.assertEquals(7, Level.compose(EVENTUAL_AFTER, 0));
        Assert.assertEquals(8, Level.compose(EFFECTIVE, 0));
    }

    @Test
    public void testLevel() {
        Assert.assertEquals(0, Level.level(-1));
        Assert.assertEquals(0, Level.level(0));
        Assert.assertEquals(0, Level.level(1));
        Assert.assertEquals(0, Level.level(4));
        Assert.assertEquals(1, Level.level(5));
        Assert.assertEquals(1, Level.level(8));
        Assert.assertEquals(2, Level.level(9));
    }

}
