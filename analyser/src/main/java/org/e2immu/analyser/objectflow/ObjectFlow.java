package org.e2immu.analyser.objectflow;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectFlow {

    public interface Origin {

    }

    public static class MethodCalls implements Origin {
        public final Set<ObjectFlow> objectFlows = new HashSet<>();

        @Override
        public String toString() {
            return "method calls " + objectFlows;
        }
    }

    public static class ParentFlows implements Origin {
        public final Set<ObjectFlow> objectFlows = new HashSet<>();

        @Override
        public String toString() {
            return "parent flows " + objectFlows;
        }
    }

    public static class ObjectCreation implements Origin {
        public final MethodCall methodCall;

        public ObjectCreation(MethodInfo methodInfo, List<Value> parameters) {
            methodCall = new MethodCall(methodInfo, parameters.stream()
                    .filter(p -> p.getObjectFlow() != null)
                    .map(Value::getObjectFlow).collect(Collectors.toList()));
        }

        @Override
        public String toString() {
            return "new " + methodCall;
        }
    }

    public static final StaticOrigin LITERAL = new StaticOrigin("literal");
    public static final StaticOrigin OPERATOR = new StaticOrigin("operator");
    public static final StaticOrigin NO_ORIGIN = new StaticOrigin("<no origin>");

    public static final ObjectFlow NO_FLOW = new ObjectFlow(Location.NO_LOCATION, ParameterizedType.TYPE_OF_NO_FLOW, NO_ORIGIN);

    public static class StaticOrigin implements Origin {
        private final String reason;

        private StaticOrigin(String reason) {
            this.reason = reason;
        }

        @Override
        public String toString() {
            return reason;
        }

    }

    public interface Access {
    }

    public static class FieldAccess implements Access {
        public final FieldInfo fieldInfo;
        public final Access accessOnField;

        public FieldAccess(FieldInfo fieldInfo, @Nullable Access accessOnField) {
            this.fieldInfo = Objects.requireNonNull(fieldInfo);
            this.accessOnField = accessOnField;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldAccess that = (FieldAccess) o;
            return fieldInfo.equals(that.fieldInfo) &&
                    Objects.equals(accessOnField, that.accessOnField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldInfo, accessOnField);
        }
    }

    public static class MethodCall implements Access {

        public final MethodInfo methodInfo;
        public final List<ObjectFlow> objectFlowsOfArguments;

        public MethodCall(MethodInfo methodInfo, List<ObjectFlow> objectFlowsOfArguments) {
            this.methodInfo = methodInfo;
            this.objectFlowsOfArguments = ImmutableList.copyOf(objectFlowsOfArguments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodCall that = (MethodCall) o;
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


    // where does this take place?
    public final Location location;

    // the type being created
    public final ParameterizedType type;

    public ObjectFlow(Location location, ParameterizedType type, Origin origin) {
        this.location = Objects.requireNonNull(location);
        this.type = Objects.requireNonNull(type);
        this.origin = Objects.requireNonNull(origin);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectFlow that = (ObjectFlow) o;
        return location.equals(that.location) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, type);
    }

    // denotes where the object comes from
    public final Origin origin;

    private final Set<Access> objectAccesses = new HashSet<>();

    public Stream<Access> getObjectAccesses() {
        return objectAccesses.stream();
    }

    public void addObjectAccess(Access access) {
        objectAccesses.add(access);
    }

    int immutableAfterObjectAccess;
    boolean objectModifiedByObjectAccess; // either via methods called, or assignments to fields

    private final Set<FieldInfo> localAssignments = new HashSet<>();

    public void assignTo(FieldInfo fieldInfo) {
        localAssignments.add(fieldInfo);
    }

    public Stream<FieldInfo> getLocalAssignments() {
        return localAssignments.stream();
    }

    // either all recipients of return, or

    Set<ObjectFlow> nextViaReturnOrFieldAccess = new HashSet<>();
    // is empty when this object is not used as an argument
    // can only contain one value if the call is @Modified
    // if the object is immutable, all callouts can be joined (order does not matter)

    final Set<ObjectFlow> nonModifyingCallOuts = new HashSet<>();

    ObjectFlow modifyingCallOut;
    // the next flow objects; can be empty
    // must be empty when the location is a field

    Set<ObjectFlow> next;

    public void addCallOut(ObjectFlow destination) {
        nonModifyingCallOuts.add(destination);
    }

    public Stream<ObjectFlow> getNonModifyingCallouts() {
        return nonModifyingCallOuts.stream();
    }

    public void addSource(ObjectFlow source) {
        if (!(origin instanceof MethodCalls)) throw new UnsupportedOperationException();
        ((MethodCalls) origin).objectFlows.add(source);
    }

    public int importance() {
        if (location.info instanceof ParameterInfo) return 1;
        return 0;
    }

    @Fluent
    public ObjectFlow merge(ObjectFlow objectFlow) {
        this.nextViaReturnOrFieldAccess.addAll(objectFlow.nextViaReturnOrFieldAccess);
        return this;
    }

    public Origin getOrigin() {
        return origin;
    }

    public String detailed() {
        return toString() + ": origin " + origin + ", fields: " + localAssignments + ", call-outs: " + nonModifyingCallOuts;
    }

    @Override
    public String toString() {
        return location + ", " + type.detailedString();
    }
}
