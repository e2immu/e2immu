package org.e2immu.analyser.parser.conditional.testexample;

import java.util.Set;

public class PostCondition_0 {

    public static void addConstant(Set<String> set) {
        set.add("b");
        assert set.size() > 4;
    }

    public static void addConstant2(Set<String> set) {
        set.add("b");
        assert set.size() > 4;
        set.add("c");
        assert set.size() > 5;
    }
}
