package org.e2immu.analyser.testexample;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/*
Test that list is @NotNull1 rather than the obvious @NotNull
 */
public class InstanceOf_3 {

    private final Set<String> base = new HashSet<>();

    public String add(Collection<String> collection) {
        base.addAll(collection);
        if (collection instanceof List<String> list) {
            return list.isEmpty() ? "Empty" : list.get(0);
        }
        return null;
    }

    public Stream<String> getBase() {
        return base.stream();
    }
}
