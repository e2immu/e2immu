package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.LV;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestShortestPathImpl extends CommonWG {

    @Test
    public void testDelay() {
        long l = ShortestPathImpl.toDistanceComponent(delay);
        long h = ShortestPathImpl.toDistanceComponentHigh(delay);
        assertTrue(l < h);
        long lh = ShortestPathImpl.fromHighToLow(h);
        assertEquals(l, lh);
    }

    @Test
    public void testAssigned() {
        long l = ShortestPathImpl.toDistanceComponent(LV.LINK_ASSIGNED);
        assertEquals(ShortestPathImpl.ASSIGNED, l);
        long h = ShortestPathImpl.toDistanceComponentHigh(LV.LINK_ASSIGNED);
        assertEquals(ShortestPathImpl.ASSIGNED_H, h);
        long lh = ShortestPathImpl.fromHighToLow(h);
        assertEquals(l, lh);
    }

    @Test
    public void testCommonHC() {
        long l = ShortestPathImpl.toDistanceComponent(LV.LINK_COMMON_HC);
        assertEquals(ShortestPathImpl.INDEPENDENT_HC, l);
        long h = ShortestPathImpl.toDistanceComponentHigh(LV.LINK_COMMON_HC);
        assertEquals(ShortestPathImpl.INDEPENDENT_HC_H, h);
        long lh = ShortestPathImpl.fromHighToLow(h);
        assertEquals(l, lh);
    }
}
