package org.e2immu.analyser.testexample;

import java.util.HashSet;
import java.util.Set;

public class ConditionalInitialization_1 {
    private Set<String> set = new HashSet<>(); // @NotNull

    public ConditionalInitialization_1(boolean b) {
        if (b) {
            set = Set.of("a", "b"); // @NotNull1
        } else {
            // here, the CI copy should not exist
            System.out.println("Set is " + set);
        }
    }

    public void setSet(Set<String> setParam, boolean c) {
        if (c) {
            this.set = setParam;
        }
    }

    public Set<String> getSet() {
        return set;
    }
}
