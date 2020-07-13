package org.e2immu.analyser.objectflow;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParentFlows implements Origin {
    final Set<ObjectFlow> objectFlows = new HashSet<>();

    @Override
    public String toString() {
        return objectFlows.size() + " parent flows";
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        if (detailed) {
            return "parent flows: [" +
                    objectFlows.stream()
                            .map(of -> visited.contains(of) ? of.visited() : of.safeToString(visited, false))
                            .collect(Collectors.joining(", ")) + "]";
        }
        return toString();
    }

    @Override
    public Stream<ObjectFlow> sources() {
        return objectFlows.stream();
    }
}
