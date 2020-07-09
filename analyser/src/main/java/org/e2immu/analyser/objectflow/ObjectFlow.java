package org.e2immu.analyser.objectflow;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Mark;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectFlow {

    public static final ObjectFlow NO_FLOW = new ObjectFlow(Location.NO_LOCATION, ParameterizedType.TYPE_OF_NO_FLOW, StaticOrigin.NO_ORIGIN);

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

    public static String typeLetter(WithInspectionAndAnalysis info) {
        return Character.toString(info.getClass().getSimpleName().charAt(0));
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

    // non-modifying access

    private final Set<Access> nonModifyingAccesses = new HashSet<>();

    public Stream<Access> getNonModifyingAccesses() {
        return nonModifyingAccesses.stream();
    }

    public void addNonModifyingAccess(Access access) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        nonModifyingAccesses.add(access);
    }

    // modifying access

    private MethodAccess modifyingAccess;

    public MethodAccess getModifyingAccess() {
        return modifyingAccess;
    }

    public void setModifyingAccess(MethodAccess modifyingAccess) {
        this.modifyingAccess = modifyingAccess;
    }

    // local assignments

    private final Set<FieldInfo> localAssignments = new HashSet<>();

    public void assignTo(FieldInfo fieldInfo) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        localAssignments.add(fieldInfo);
    }

    public Stream<FieldInfo> getLocalAssignments() {
        return localAssignments.stream();
    }

    // non-modifying callouts / use as argument in other method calls

    final Set<ObjectFlow> nonModifyingCallOuts = new HashSet<>();


    public void addNonModifyingCallOut(ObjectFlow destination) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        nonModifyingCallOuts.add(destination);
    }

    public Stream<ObjectFlow> getNonModifyingCallouts() {
        return nonModifyingCallOuts.stream();
    }

    // modifying call-out

    ObjectFlow modifyingCallOut;

    // the next flow objects

    private Set<ObjectFlow> next = new HashSet<>();

    public void addNext(ObjectFlow objectFlow) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        this.next.add(objectFlow);
    }

    public Stream<ObjectFlow> getNext() {
        return next.stream();
    }


    // origin

    public void addParentOrigin(ObjectFlow source) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        if (!(origin instanceof ParentFlows)) throw new UnsupportedOperationException();
        ((ParentFlows) origin).objectFlows.add(source);
    }

    public void addMethodCallOrigin(ObjectFlow source) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        if (!(origin instanceof MethodCalls)) throw new UnsupportedOperationException();
        ((MethodCalls) origin).objectFlows.add(source);
    }

    public Origin getOrigin() {
        return origin;
    }

    // other

    public ObjectFlow merge(ObjectFlow objectFlow) {
        if (this == NO_FLOW) return objectFlow;
        if (objectFlow == NO_FLOW) return this;

        this.next.addAll(objectFlow.next);
        // TODO study merging
        return this;
    }


    static <X> String detail(String what, X objectFlow) {
        return objectFlow == null ? "" : "\n    " + what + ": " + objectFlow;
    }

    static <X> String detail(String what, Collection<X> collection) {
        if (collection.size() == 1) return detail(what, collection.stream().findFirst().orElseThrow());
        if (collection.isEmpty()) return "";
        return "\n    " + what + ": " + collection.stream().map(X::toString).collect(Collectors.joining(", "));
    }

    public String detailed() {
        return "\n<flow of type " + type.stream() + ": " + origin + " @" + location +
                detail("@NM access", nonModifyingAccesses) +
                detail("@M access", modifyingAccess) +
                detail("@NM call-outs", nonModifyingCallOuts) +
                detail("@M call-out", modifyingCallOut) +
                detail("fields", localAssignments) +
                detail("next", next) +
                ">";
    }

    @Override
    public String toString() {
        return "<flow of type " + type.stream() + ": " + origin + " @" + location + ">";
    }


    public Set<String> marks() {
        Set<String> marksSources = origin.sources().flatMap(of -> of.marks().stream()).collect(Collectors.toSet());
        if (modifyingAccess != null) {
            MethodInfo methodInfo = modifyingAccess.methodInfo;
            Optional<AnnotationExpression> oMark = methodInfo.methodInspection.get().annotations.stream()
                    .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Mark.class.getName())).findFirst();
            if (oMark.isPresent()) {
                AnnotationExpression ae = oMark.get();
                String mark = ae.extract("value", "");
                if (!mark.isEmpty()) {
                    return SetUtil.immutableUnion(marksSources, Set.of(mark));
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
