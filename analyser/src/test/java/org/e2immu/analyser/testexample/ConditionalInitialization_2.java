package org.e2immu.analyser.testexample;

import java.util.HashSet;
import java.util.Set;

// small variant on CI_1 to test a bad assignment

public class ConditionalInitialization_2 {
    private Set<String> set = new HashSet<>();

    public ConditionalInitialization_2(boolean b) {
        if (b) {
            set = Set.of("a", "b"); // @NotNull1
        } else {
            // here, the CI copy should not exist
            System.out.println("Set is " + set);
        }
    }

    public void setSet(Set<String> setParam, boolean c) {
        if (c) {
            this.set = set;
        }
    }

    public Set<String> getSet() {
        return set;
    }
}
