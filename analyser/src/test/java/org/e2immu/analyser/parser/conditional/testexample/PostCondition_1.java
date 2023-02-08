package org.e2immu.analyser.parser.conditional.testexample;

import java.util.Set;

public class PostCondition_1 {

    public static void addConstant(Set<String> set) {
        set.add("b");
        if (set.size() <= 4) throw new UnsupportedOperationException();
    }

    public static void addConstant2(Set<String> set) {
        set.add("b");
        if (set.size() <= 4) throw new UnsupportedOperationException();
        set.add("c");
        if (set.size() <= 5) throw new IllegalArgumentException();
    }
}
