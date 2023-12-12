package org.e2immu.analyser.analyser.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReverseStringComparator {
    @Test
    public void test1() {
        assertTrue(WeightedGraphImpl.REVERSE_STRING_COMPARATOR.compare("cba", "cda") < 0);
        assertTrue(WeightedGraphImpl.REVERSE_STRING_COMPARATOR.compare("cda", "cba") > 0);
        assertTrue(WeightedGraphImpl.REVERSE_STRING_COMPARATOR.compare("cba", "cba") == 0);
        assertTrue(WeightedGraphImpl.REVERSE_STRING_COMPARATOR.compare("dcba", "cba") > 0);
        assertTrue(WeightedGraphImpl.REVERSE_STRING_COMPARATOR.compare("cba", "dcba") < 0);
    }
}
