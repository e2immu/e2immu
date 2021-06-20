package org.e2immu.analyser.testexample;

import java.util.HashSet;
import java.util.Set;

public class ConditionalInitialization_2 {
    private static Set<String> set = new HashSet<>(); // @NotNull

    public ConditionalInitialization_2() {
        if (set == null) { // should not happen in this code
            set = Set.of("a", "b"); // @NotNull1
        }
    }

    public static boolean contains(String c) {
        return set.contains(c);
    }
}
