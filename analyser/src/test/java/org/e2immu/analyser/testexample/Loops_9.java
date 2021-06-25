package org.e2immu.analyser.testexample;

// semantic nonsense, trying to catch a CNN change from 1 to 5 on node

public class Loops_9 {

    private final String in = "abc";

    public String add(String[] strings) {
        String node = in;
        for (String s : strings) {
            node = null;
        }
        return node;
    }
}
