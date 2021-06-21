package org.e2immu.analyser.testexample;

import java.util.HashSet;
import java.util.Set;

// small variant on CI_1 to test a bad assignment

public class ConditionalInitialization_3 {
    private Set<String> set = new HashSet<>();

    public ConditionalInitialization_3(boolean b) {
        if (b) {
            set = Set.of("a", "b"); // @NotNull1
        } else {
            // here, the CI copy should not exist
            System.out.println("Set is " + set);
        }
        set = null;
    }

    public Set<String> getSet() {
        return set;
    }
}
