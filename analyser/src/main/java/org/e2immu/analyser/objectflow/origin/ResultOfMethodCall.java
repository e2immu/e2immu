package org.e2immu.analyser.objectflow.origin;

import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// internal origin of a flow as a result (return value) of a method call

public class ResultOfMethodCall implements Origin {

    final Set<ObjectFlow> objectFlows = new HashSet<>();

    public ResultOfMethodCall() {
        // perfectly possible not to have a flow (e.g., for operators at the moment)
    }
    public ResultOfMethodCall(ObjectFlow objectFlow) {
        this.objectFlows.add(objectFlow);
    }

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
            return "parameter: [" +
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

    @Override
    public void addBiDirectionalLink(ObjectFlow destination) {
        for (ObjectFlow objectFlow : objectFlows) {
            objectFlow.addNext(destination);
        }
    }

    @Override
    public boolean permanentFromStart() {
        return false;
    }
}
