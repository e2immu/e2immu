package org.e2immu.analyser.objectflow;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodCalls implements Origin {

    final Set<ObjectFlow> objectFlows = new HashSet<>();

    @Override
    public String toString() {
        return objectFlows.size() + " method calls";
    }

    @Override
    public Stream<ObjectFlow> sources() {
        return objectFlows.stream();
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        if (detailed) {
            return "method calls: [" +
                    objectFlows.stream()
                            .map(of -> visited.contains(of) ? of.visited() : of.safeToString(visited, false))
                            .collect(Collectors.joining(", ")) + "]";
        }
        return toString();
    }

    // mainly used for testing
    public boolean contains(ObjectFlow objectFlow) {
        return objectFlows.contains(objectFlow);
    }
}
