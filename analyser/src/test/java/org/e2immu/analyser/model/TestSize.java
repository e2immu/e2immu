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

        Assert.assertEquals(0, Analysis.sizeMin(0));
        Assert.assertFalse(Analysis.haveEquals(0));

        Assert.assertEquals(0, Analysis.sizeEquals(1));
        Assert.assertTrue(Analysis.haveEquals(1));

        Assert.assertEquals(1, Analysis.sizeMin(2));
        Assert.assertFalse(Analysis.haveEquals(2));

        Assert.assertEquals(1, Analysis.sizeEquals(3));
        Assert.assertTrue(Analysis.haveEquals(3));

        Assert.assertEquals(2, Analysis.sizeMin(4));
        Assert.assertFalse(Analysis.haveEquals(4));

        Assert.assertEquals(2, Analysis.sizeEquals(5));
        Assert.assertTrue(Analysis.haveEquals(5));
    }
}
