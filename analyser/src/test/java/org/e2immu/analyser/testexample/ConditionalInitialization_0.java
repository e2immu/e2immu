package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;

import java.util.HashSet;
import java.util.Set;

// The type is MUTABLE! each construction potentially overwrites set (agreed, with the same value,
// but that's going too far)
@Container
public class ConditionalInitialization_0 {
    private static Set<String> set = new HashSet<>(); // @NotNull

    public ConditionalInitialization_0(boolean b) {
        if (set.isEmpty()) {
            set = Set.of("a", "b"); // @NotNull1
        }
        if (b) {
            // here, the CI copy should not exist
            System.out.println("Set is " + set);
        }
    }

    public static boolean contains(String c) {
        return set.contains(c);
    }
}
