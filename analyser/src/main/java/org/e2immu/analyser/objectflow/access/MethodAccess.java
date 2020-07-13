package org.e2immu.analyser.objectflow.access;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodAccess implements Access {

    public final MethodInfo methodInfo;
    public final List<ObjectFlow> objectFlowsOfArguments;

    public MethodAccess(MethodInfo methodInfo, List<ObjectFlow> objectFlowsOfArguments) {
        this.methodInfo = methodInfo;
        this.objectFlowsOfArguments = ImmutableList.copyOf(objectFlowsOfArguments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodAccess that = (MethodAccess) o;
        return methodInfo.equals(that.methodInfo) &&
                objectFlowsOfArguments.equals(that.objectFlowsOfArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, objectFlowsOfArguments);
    }

    @Override
    public String toString() {
        return (methodInfo == null ? "<lambda>" : methodInfo.name) + "(" + objectFlowsOfArguments.size() + " object flows)";
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        if (detailed) {
            return (methodInfo == null ? "<lambda>" : methodInfo.name) + "(" +
                    objectFlowsOfArguments.stream()
                            .map(of -> visited.contains(of) ? "visited object flow @" + of.location : of.safeToString(visited, false))
                            .collect(Collectors.joining(", ")) + ")";
        }
        return toString();
    }
}
