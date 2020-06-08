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
        Assert.assertFalse(Analysis.haveEquals(-1));
        Assert.assertFalse(Analysis.haveEquals(0));

        Assert.assertEquals(0, Analysis.decodeSizeMin(1));
        Assert.assertFalse(Analysis.haveEquals(1));

        Assert.assertEquals(0, Analysis.decodeSizeEquals(2));
        Assert.assertTrue(Analysis.haveEquals(2));

        Assert.assertEquals(1, Analysis.decodeSizeMin(3));
        Assert.assertFalse(Analysis.haveEquals(3));

        Assert.assertEquals(1, Analysis.decodeSizeEquals(4));
        Assert.assertTrue(Analysis.haveEquals(4));

        Assert.assertEquals(2, Analysis.decodeSizeMin(5));
        Assert.assertFalse(Analysis.haveEquals(5));

        Assert.assertEquals(2, Analysis.decodeSizeEquals(6));
        Assert.assertTrue(Analysis.haveEquals(6));
    }

    @Test
    public void testJoin1() {
        int min1 = Analysis.encodeSizeMin(1);
        Assert.assertEquals(3, min1);
        int eq1 = Analysis.encodeSizeEquals(1);
        Assert.assertEquals(4, eq1);
        int eq2 = Analysis.encodeSizeEquals(2);
        Assert.assertEquals(6, eq2);
        Assert.assertEquals(3, Analysis.joinSizeRestrictions(4, 6));
        Assert.assertEquals(3, Analysis.joinSizeRestrictions(6, 4));
    }

    @Test
    public void testJoin2() {
        int min1 = Analysis.encodeSizeMin(1);
        Assert.assertEquals(3, min1);
        int eq2 = Analysis.encodeSizeEquals(2);
        Assert.assertEquals(6, eq2);
        Assert.assertEquals(3, Analysis.joinSizeRestrictions(3, 6));
        Assert.assertEquals(3, Analysis.joinSizeRestrictions(6, 3));
    }
}
