package org.e2immu.analyser.testexample;

import java.util.Set;

public class ConditionalInitialization_1 {
    private static Set<String> set; // null

    public ConditionalInitialization_1() {
        if (set == null) {
            set = Set.of("a", "b"); // @NotNull1
        }
    }

    public static boolean contains(String c) {
        return set.contains(c);
    }
}
