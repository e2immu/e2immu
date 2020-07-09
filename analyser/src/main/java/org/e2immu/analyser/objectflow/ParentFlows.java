package org.e2immu.analyser.objectflow;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class ParentFlows implements Origin {
    final Set<ObjectFlow> objectFlows = new HashSet<>();

    @Override
    public String toString() {
        return "parent flows " + objectFlows;
    }

    @Override
    public Stream<ObjectFlow> sources() {
        return objectFlows.stream();
    }
}
