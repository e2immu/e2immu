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
        Assert.assertEquals(TRUE, Level.value(2, 0));
        Assert.assertEquals(TRUE, Level.value(3, 0));
        Assert.assertEquals(TRUE, Level.value(4, 0));

        Assert.assertEquals(DELAY, Level.value(-1, 1));
        Assert.assertEquals(FALSE, Level.value(0, 1)); // even ones are final decisions
        Assert.assertEquals(DELAY, Level.value(1, 1));
        Assert.assertEquals(FALSE, Level.value(2, 1));
        Assert.assertEquals(TRUE, Level.value(3, 1));
        Assert.assertEquals(TRUE, Level.value(4, 1)); // false at level 2, but true at level 1
        Assert.assertEquals(TRUE, Level.value(5, 1));

        Assert.assertEquals(DELAY, Level.value(-1, 2));
        Assert.assertEquals(FALSE, Level.value(0, 2)); // even ones are final decisions
        Assert.assertEquals(DELAY, Level.value(1, 2));
        Assert.assertEquals(FALSE, Level.value(2, 2));
        Assert.assertEquals(DELAY, Level.value(3, 2));
        Assert.assertEquals(FALSE, Level.value(4, 2));
        Assert.assertEquals(TRUE, Level.value(5, 2));
    }

    @Test
    public void testCompose() {
        Assert.assertEquals(-1, Level.compose(DELAY, 0));
        Assert.assertEquals(0, Level.compose(FALSE, 0));
        Assert.assertEquals(1, Level.compose(TRUE, 0));

        Assert.assertEquals(1, Level.compose(DELAY, 1));
        Assert.assertEquals(2, Level.compose(FALSE, 1));
        Assert.assertEquals(3, Level.compose(TRUE, 1));

        Assert.assertEquals(3, Level.compose(DELAY, 2));
        Assert.assertEquals(4, Level.compose(FALSE, 2));
        Assert.assertEquals(5, Level.compose(TRUE, 2));
    }

    @Test
    public void testHaveTrueAt() {
        Assert.assertFalse(Level.haveTrueAt(-1, E1IMMUTABLE));
        Assert.assertFalse(Level.haveTrueAt(0, E1IMMUTABLE));
        Assert.assertTrue(Level.haveTrueAt(1, E1IMMUTABLE));
        Assert.assertTrue(Level.haveTrueAt(2, E1IMMUTABLE));
        Assert.assertTrue(Level.haveTrueAt(3, E1IMMUTABLE));

        Assert.assertFalse(Level.haveTrueAt(-1, E2IMMUTABLE));
        Assert.assertFalse(Level.haveTrueAt(0, E2IMMUTABLE));
        Assert.assertFalse(Level.haveTrueAt(1, E2IMMUTABLE));
        Assert.assertFalse(Level.haveTrueAt(2, E2IMMUTABLE));
        Assert.assertTrue(Level.haveTrueAt(3, E2IMMUTABLE));
        Assert.assertTrue(Level.haveTrueAt(4, E2IMMUTABLE));
    }

    @Test
    public void testLevel() {
        Assert.assertEquals(-1, Level.level(-1));
        Assert.assertEquals(-1, Level.level(0));
        Assert.assertEquals(0, Level.level(1));
        Assert.assertEquals(0, Level.level(2));
        Assert.assertEquals(1, Level.level(3));
        Assert.assertEquals(1, Level.level(4));
        Assert.assertEquals(2, Level.level(5));
    }
}
