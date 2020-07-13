package org.e2immu.analyser.objectflow.origin;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectCreation implements Origin {
    public final MethodAccess methodCall;

    public ObjectCreation(MethodInfo methodInfo, List<Value> parameters) {
        methodCall = new MethodAccess(methodInfo, parameters.stream()
                .filter(p -> p.getObjectFlow() != null)
                .map(Value::getObjectFlow).collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return "new " + methodCall;
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        return "new " + methodCall.safeToString(visited, detailed);
    }

    @Override
    public Stream<ObjectFlow> sources() {
        return Stream.of();
    }

    @Override
    public void addBiDirectionalLink(ObjectFlow destination) {
        // there is no bi-directional link here; it is the start of a flow
    }
}
