package org.e2immu.analyser.testexample;

import java.util.Set;

public class Warnings_4 {

    private final Set<String> set;

    public Warnings_4(Set<String> input) {
        set = Set.copyOf(input);
    }

    public Set<String> getSet() {
        return set;
    }

    public void add(String s) {
        set.add(s);
    }
}
