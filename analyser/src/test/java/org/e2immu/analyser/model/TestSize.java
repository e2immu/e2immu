package org.e2immu.analyser.model;

import org.junit.Assert;
import org.junit.Test;

/**
 * -1 = absent
 * 0 = not a size
 * 1 = min 0 = we have a size
 * 2 = eq to 0
 * 3 = min 1
 * 4 = eq to 1
 * 5 = min 2
 * 6 = eq to 2
 */
public class TestSize {


    @Test
    public void testSize() {
        Assert.assertFalse(Level.haveEquals(-1));
        Assert.assertFalse(Level.haveEquals(0));

        Assert.assertEquals(0, Level.decodeSizeMin(1));
        Assert.assertFalse(Level.haveEquals(1));

        Assert.assertEquals(0, Level.decodeSizeEquals(2));
        Assert.assertTrue(Level.haveEquals(2));

        Assert.assertEquals(1, Level.decodeSizeMin(3));
        Assert.assertFalse(Level.haveEquals(3));

        Assert.assertEquals(1, Level.decodeSizeEquals(4));
        Assert.assertTrue(Level.haveEquals(4));

        Assert.assertEquals(2, Level.decodeSizeMin(5));
        Assert.assertFalse(Level.haveEquals(5));

        Assert.assertEquals(2, Level.decodeSizeEquals(6));
        Assert.assertTrue(Level.haveEquals(6));
    }

    @Test
    public void testJoin1() {
        int min1 = Level.encodeSizeMin(1);
        Assert.assertEquals(3, min1);
        int eq1 = Level.encodeSizeEquals(1);
        Assert.assertEquals(4, eq1);
        int eq2 = Level.encodeSizeEquals(2);
        Assert.assertEquals(6, eq2);
        Assert.assertEquals(3, Level.joinSizeRestrictions(4, 6));
        Assert.assertEquals(3, Level.joinSizeRestrictions(6, 4));
    }

    @Test
    public void testJoin2() {
        int min1 = Level.encodeSizeMin(1);
        Assert.assertEquals(3, min1);
        int eq2 = Level.encodeSizeEquals(2);
        Assert.assertEquals(6, eq2);
        Assert.assertEquals(3, Level.joinSizeRestrictions(3, 6));
        Assert.assertEquals(3, Level.joinSizeRestrictions(6, 3));
    }
}
