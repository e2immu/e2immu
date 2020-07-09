package org.e2immu.analyser.objectflow;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class MethodCalls implements Origin {

    final Set<ObjectFlow> objectFlows = new HashSet<>();

    @Override
    public String toString() {
        return "method calls " + objectFlows;
    }

    @Override
    public Stream<ObjectFlow> sources() {
        return objectFlows.stream();
    }

    // mainly used for testing
    public boolean contains(ObjectFlow objectFlow) {
        return objectFlows.contains(objectFlow);
    }
}
