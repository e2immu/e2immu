package org.e2immu.analyser.objectflow.origin;

import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParentFlows implements Origin {
    final Set<ObjectFlow> objectFlows = new HashSet<>();

    public ParentFlows(ObjectFlow objectFlow) {
        this.objectFlows.add(objectFlow);
    }

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

    @Override
    public void addBiDirectionalLink(ObjectFlow destination) {
        for(ObjectFlow objectFlow: objectFlows) {
            objectFlow.addNext(destination);
        }
    }

    @Override
    public boolean permanentFromStart() {
        return false;
    }
}
