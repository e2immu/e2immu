package org.e2immu.analyser.testexample.withannotatedapi;

import java.util.HashSet;
import java.util.Set;

public class TestSkeleton {

    private final Set<String> strings = new HashSet<>();
    public final String finalString;
    public Set<Integer> set;

    public TestSkeleton() {
        // empty
        finalString = "will be ignored";
    }

    public int method(int param) {
        return 0;
    }
}
