package org.e2immu.analyser.objectflow.origin;

import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// methodCalls origin: call-outs from object flow as argument to input object flow in parameter
public class CallOutsArgumentToParameter implements Origin {

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

    public void replaceSource(ObjectFlow oldObjectFlow, ObjectFlow newObjectFlow) {
        objectFlows.remove(oldObjectFlow);
        objectFlows.add(newObjectFlow);
    }

    @Override
    public void addBiDirectionalLink(ObjectFlow destination) {
        // parameter flows are permanent, we don't have add links in this direction
        throw new UnsupportedOperationException();
    }

    public void addSource(ObjectFlow source) {
        objectFlows.add(source);
    }
}
