package org.e2immu.analyser.objectflow;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ObjectFlow {

    public interface Origin {

    }

    public static class FieldOrigin implements Origin {
        public final FieldInfo fieldInfo;

        public FieldOrigin(FieldInfo fieldInfo) {
            this.fieldInfo = fieldInfo;
        }
    }

    public static class ParentFlows implements Origin {
        public final Set<ObjectFlow> objectFlows = new HashSet<>();
    }

    public static class ObjectCreation implements Origin {
        public final MethodCall methodCall;

        public ObjectCreation(MethodCall methodCall) {
            this.methodCall = methodCall;
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
        public final SetOnceMap<Integer, ObjectFlow> objectFlowsOfArguments = new SetOnceMap<>();

        public MethodCall(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }
    }


    // where does this take place?
    public final Location location;

    // the type being created
    public final TypeInfo typeInfo;

    public ObjectFlow(Location location, TypeInfo typeInfo) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.location = Objects.requireNonNull(location);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectFlow that = (ObjectFlow) o;
        return location.equals(that.location) &&
                typeInfo.equals(that.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, typeInfo);
    }

    // denotes where the object comes from
    Origin origin;

    List<Access> objectAccesses;
    int immutableAfterObjectAccess;
    boolean objectModifiedByObjectAccess; // either via methods called, or assignments to fields

    List<FieldInfo> localAssignments;

    // either all recipients of return, or
    Set<ObjectFlow> nextViaReturn;

    // is null when this object is not used as an argument
    ObjectFlow callOut;

    // the next flow objects; can be empty
    // must be empty when the location is a field
    Set<ObjectFlow> next;
}
