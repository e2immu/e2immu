package org.e2immu.analyser.objectflow;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;

import java.util.List;
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
    public Stream<ObjectFlow> sources() {
        return Stream.of();
    }
}
