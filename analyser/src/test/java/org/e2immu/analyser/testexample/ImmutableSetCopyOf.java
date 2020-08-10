package org.e2immu.analyser.testexample;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class ImmutableSetCopyOf {

    private final Set<String> set;

    public ImmutableSetCopyOf(Set<String> input) {
        set = ImmutableSet.copyOf(input);
    }

    public Set<String> getSet() {
        return set;
    }
}
