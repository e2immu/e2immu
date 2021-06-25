package org.e2immu.analyser.testexample;

// semantic nonsense, trying to catch a CNN change from 1 to 5 on node

import java.util.Set;

public class Loops_10 {

    private final String in = "abc";

    public String add(Set<String> strings) {
        String node = in;
        for (String s : strings) {
            node = null;
        }
        return node;
    }
}
