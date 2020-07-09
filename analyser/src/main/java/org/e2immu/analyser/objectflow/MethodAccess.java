package org.e2immu.analyser.objectflow;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.MethodInfo;

import java.util.List;
import java.util.Objects;

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
        return methodInfo.name + "(" + objectFlowsOfArguments + ")";
    }
}
