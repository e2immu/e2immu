package org.e2immu.analyser.objectflow;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.Fluent;

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
            methodCall = new MethodCall(methodInfo, parameters.stream().map(Value::getObjectFlow).collect(Collectors.toList()));
        }

        @Override
        public String toString() {
            return "new " + methodCall;
        }
    }

    public static final Literal LITERAL = new Literal();

    public static class Literal implements Origin {
        private Literal() {
        }

        @Override
        public String toString() {
            return "literal";
        }
    }

    public static final Operator OPERATOR = new Operator();

    public static class Operator implements Origin {
        private Operator() {
        }

        @Override
        public String toString() {
            return "operator";
        }
    }

    public static class Access {
        public final Either<FieldAccess, MethodCall> fieldAccessOrMethodCall;

        public Access(FieldAccess fieldAccess) {
            fieldAccessOrMethodCall = Either.left(fieldAccess);
        }

        public Access(MethodCall methodCall) {
            fieldAccessOrMethodCall = Either.right(methodCall);
        }
    }

    public static class FieldAccess {
        public final FieldInfo fieldInfo;
        public final Access accessOnField;

        public FieldAccess(FieldInfo fieldInfo, Access accessOnField) {
            this.fieldInfo = fieldInfo;
            this.accessOnField = accessOnField;
        }
    }

    public static class MethodCall {

        public final MethodInfo methodInfo;
        public final List<ObjectFlow> objectFlowsOfArguments;

        public MethodCall(MethodInfo methodInfo, List<ObjectFlow> objectFlowsOfArguments) {
            this.methodInfo = methodInfo;
            this.objectFlowsOfArguments = ImmutableList.copyOf(objectFlowsOfArguments);
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

    List<Access> objectAccesses;
    int immutableAfterObjectAccess;
    boolean objectModifiedByObjectAccess; // either via methods called, or assignments to fields

    final Set<FieldInfo> localAssignments = new HashSet<>();

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

    public void addSource(ObjectFlow source) {
        if (!(origin instanceof MethodCalls)) throw new UnsupportedOperationException();
        ((MethodCalls) origin).objectFlows.add(source);
    }

    public void assignTo(FieldInfo fieldInfo) {
        localAssignments.add(fieldInfo);
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

    public Stream<FieldInfo> getLocalAssignments() {
        return localAssignments.stream();
    }

    public String detailed() {
        return toString() + ": origin " + origin + ", fields: " + localAssignments + ", call-outs: " + nonModifyingCallOuts;
    }

    @Override
    public String toString() {
        return location + ", " + type.detailedString();
    }
}
