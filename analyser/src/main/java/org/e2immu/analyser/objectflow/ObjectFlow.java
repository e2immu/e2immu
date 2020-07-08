package org.e2immu.analyser.objectflow;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectFlow {

    public interface Origin {
        Stream<ObjectFlow> sources();
    }

    public static class MethodCalls implements Origin {
        public final Set<ObjectFlow> objectFlows = new HashSet<>();

        @Override
        public String toString() {
            return "method calls " + objectFlows;
        }

        @Override
        public Stream<ObjectFlow> sources() {
            return objectFlows.stream();
        }
    }

    public static class ParentFlows implements Origin {
        public final Set<ObjectFlow> objectFlows = new HashSet<>();

        @Override
        public String toString() {
            return "parent flows " + objectFlows;
        }

        @Override
        public Stream<ObjectFlow> sources() {
            return objectFlows.stream();
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

        @Override
        public Stream<ObjectFlow> sources() {
            return Stream.of();
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

        @Override
        public Stream<ObjectFlow> sources() {
            return Stream.of();
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

    // denotes where the object comes from
    public final Origin origin;

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


    private final Set<Access> nonModifyingObjectAccesses = new HashSet<>();

    public Stream<Access> getNonModifyingObjectAccesses() {
        return nonModifyingObjectAccesses.stream();
    }

    public void addNonModifyingObjectAccess(Access access) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        nonModifyingObjectAccesses.add(access);
    }

    private final Set<FieldInfo> localAssignments = new HashSet<>();

    public void assignTo(FieldInfo fieldInfo) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        localAssignments.add(fieldInfo);
    }

    public Stream<FieldInfo> getLocalAssignments() {
        return localAssignments.stream();
    }

    final Set<ObjectFlow> nonModifyingCallOuts = new HashSet<>();

    private MethodCall modifyingAccess;

    public MethodCall getModifyingAccess() {
        return modifyingAccess;
    }

    public void setModifyingAccess(MethodCall modifyingAccess) {
        this.modifyingAccess = modifyingAccess;
    }

    ObjectFlow modifyingCallOut;

    // the next flow objects; only possibly non-empty when modifying access or modifying call-out

    private Set<ObjectFlow> next = new HashSet<>();

    public void addNext(ObjectFlow objectFlow) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        this.next.add(objectFlow);
    }

    public Stream<ObjectFlow> getNext() {
        return next.stream();
    }

    public void addNonModifyingCallOut(ObjectFlow destination) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        nonModifyingCallOuts.add(destination);
    }

    public Stream<ObjectFlow> getNonModifyingCallouts() {
        return nonModifyingCallOuts.stream();
    }

    public void addSource(ObjectFlow source) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        if (!(origin instanceof MethodCalls)) throw new UnsupportedOperationException();
        ((MethodCalls) origin).objectFlows.add(source);
    }

    public ObjectFlow merge(ObjectFlow objectFlow) {
        if (this == NO_FLOW) return objectFlow;
        if (objectFlow == NO_FLOW) return this;

        this.next.addAll(objectFlow.next);
        // TODO study merging
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


    public Set<String> marks() {
        Set<String> marksSources = origin.sources().flatMap(of -> of.marks().stream()).collect(Collectors.toSet());
        if (modifyingAccess != null) {
            MethodInfo methodInfo = ((MethodCall) modifyingAccess).methodInfo;
            Optional<AnnotationExpression> oMark = methodInfo.methodInspection.get().annotations.stream()
                    .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Mark.class.getName())).findFirst();
            if (oMark.isPresent()) {
                AnnotationExpression ae = oMark.get();
                String[] strings = ae.extract("after", new String[0]);
                if (strings.length > 0) {
                    return SetUtil.immutableUnion(marksSources, new HashSet<>(Arrays.asList(strings)));
                }
            }
        }
        return marksSources;
    }

    public void moveNextTo(ObjectFlow second) {
        second.next.addAll(next);
        next.clear();
    }
}
