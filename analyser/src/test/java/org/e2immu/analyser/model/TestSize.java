package org.e2immu.analyser.model;

import org.junit.Assert;
import org.junit.Test;

/**
 * -1 = absent
 * 0 = min 0 = no size spec present
 * 1 = eq to 0
 * 2 = min 1
 * 3 = eq to 1
 * 4 = min 2
 * 5 = eq to 2
 */
public class TestSize {


    @Test
    public void testSize() {
        Assert.assertFalse(Analysis.haveEquals(-1));

        Assert.assertEquals(0, Analysis.decodeSizeMin(0));
        Assert.assertFalse(Analysis.haveEquals(0));

        Assert.assertEquals(0, Analysis.decodeSizeEquals(1));
        Assert.assertTrue(Analysis.haveEquals(1));

        Assert.assertEquals(1, Analysis.decodeSizeMin(2));
        Assert.assertFalse(Analysis.haveEquals(2));

        Assert.assertEquals(1, Analysis.decodeSizeEquals(3));
        Assert.assertTrue(Analysis.haveEquals(3));

        Assert.assertEquals(2, Analysis.decodeSizeMin(4));
        Assert.assertFalse(Analysis.haveEquals(4));

        Assert.assertEquals(2, Analysis.decodeSizeEquals(5));
        Assert.assertTrue(Analysis.haveEquals(5));
    }

    @Test
    public void testJoin1() {
        int min1 = Analysis.encodeSizeMin(1);
        Assert.assertEquals(2, min1);
        int eq1 = Analysis.encodeSizeEquals(1);
        Assert.assertEquals(3, eq1);
        int eq2 = Analysis.encodeSizeEquals(2);
        Assert.assertEquals(5, eq2);
        Assert.assertEquals(2, Analysis.joinSizeRestrictions(3, 5));
        Assert.assertEquals(2, Analysis.joinSizeRestrictions(5, 3));
    }

    @Test
    public void testJoin2() {
        int min1 = Analysis.encodeSizeMin(1);
        Assert.assertEquals(2, min1);
        int eq2 = Analysis.encodeSizeEquals(2);
        Assert.assertEquals(5, eq2);
        Assert.assertEquals(2, Analysis.joinSizeRestrictions(2, 5));
        Assert.assertEquals(2, Analysis.joinSizeRestrictions(5, 2));
    }
}
