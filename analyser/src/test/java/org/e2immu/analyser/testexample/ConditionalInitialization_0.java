package org.e2immu.analyser.testexample;

import java.util.HashSet;
import java.util.Set;

public class ConditionalInitialization_0 {
    private static Set<String> set = new HashSet<>(); // @NotNull

    public ConditionalInitialization_0() {
        if (set.isEmpty()) {
            set = Set.of("a", "b"); // @NotNull1
        }
    }

    public static boolean contains(String c) {
        return set.contains(c);
    }
}
