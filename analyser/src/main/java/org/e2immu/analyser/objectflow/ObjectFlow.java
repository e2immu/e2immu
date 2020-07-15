package org.e2immu.analyser.objectflow;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Mark;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectFlow {

    public static final ObjectFlow NO_FLOW = new ObjectFlow(Location.NO_LOCATION, ParameterizedType.TYPE_OF_NO_FLOW, Origin.NO_ORIGIN);

    // where does this take place?
    public final Location location;

    // the type being created
    public final ParameterizedType type;

    // denotes where the object comes from
    public final Origin origin;

    private boolean finalized;

    public ObjectFlow(Location location, ParameterizedType type, Origin origin) {
        this.location = Objects.requireNonNull(location);
        this.type = Objects.requireNonNull(type);
        this.origin = Objects.requireNonNull(origin);
    }

    public static String typeLetter(WithInspectionAndAnalysis info) {
        return Character.toString(info.getClass().getSimpleName().charAt(0));
    }

    private final Set<ObjectFlow> previous = new HashSet<>();

    public void addPrevious(ObjectFlow objectFlow) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        this.previous.add(objectFlow);
    }

    public Stream<ObjectFlow> getPrevious() {
        return previous.stream();
    }

    public void replaceSource(ObjectFlow oldSource, ObjectFlow newSource) {
        previous.remove(oldSource);
        previous.add(newSource);
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
        Objects.requireNonNull(modifyingAccess);
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

    public ObjectFlow getModifyingCallOut() {
        return modifyingCallOut;
    }

    public void setModifyingCallOut(ObjectFlow modifyingCallOut) {
        Objects.requireNonNull(modifyingCallOut);
        this.modifyingCallOut = modifyingCallOut;
    }

    public void replaceCallOut(ObjectFlow oldCallOut, ObjectFlow newCallOut) {
        if (oldCallOut == modifyingCallOut) {
            modifyingCallOut = newCallOut;
        } else {
            nonModifyingCallOuts.remove(oldCallOut);
            nonModifyingCallOuts.add(newCallOut);
        }
    }

    public boolean haveModifying() {
        return modifyingCallOut != null || modifyingAccess != null;
    }

    // the next flow objects

    private final Set<ObjectFlow> next = new HashSet<>();

    public void addNext(ObjectFlow objectFlow) {
        if (this == NO_FLOW) throw new UnsupportedOperationException();
        this.next.add(objectFlow);
    }

    public Stream<ObjectFlow> getNext() {
        return next.stream();
    }


    /*
    add inverse links, but does not change the value in ParameterAnalysis, FieldAnalysis, etc.
    because that has to happen in bulk for internalObjectFlows
     */
    public void finalize(ObjectFlow initialObjectFlow) {
        if (finalized) return; // is possible, because the same object flow can sit on a parameter and a method result, for example
        finalized = true;
        switch (origin) {
            case PARAMETER:
                initialObjectFlow.getPrevious().forEach(callout -> {
                    callout.replaceCallOut(initialObjectFlow, this);
                    addPrevious(callout);
                });
                break;
            case RESULT_OF_METHOD:
            case FIELD_ACCESS:
                initialObjectFlow.next.forEach(prev -> prev.replaceSource(initialObjectFlow, this));
                next.addAll(initialObjectFlow.next);
                break;

            default:
        }
        if (modifyingCallOut != null) {
            modifyingCallOut.previous.add(this);
        }
        for (ObjectFlow nonModifyingCallOut : nonModifyingCallOuts) {
            nonModifyingCallOut.previous.add(this);
        }
    }

    private boolean delayed;

    public void delay() {
        this.delayed = true;
    }

    public boolean isDelayed() {
        return delayed;
    }

    // origin

    public Origin getOrigin() {
        return origin;
    }

    // other

    public String detailed() {
        return safeToString(new HashSet<>(), true);
    }

    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        visited.add(this);
        if (detailed) {
            return "\n<flow of type " + type.stream() + ": " + origin + "@" + location +
                    (previous.isEmpty() ? "" : "; previous " + getPrevious().map(of -> of.safeToString(visited, false)).collect(Collectors.joining(", "))) +
                    (nonModifyingAccesses.isEmpty() ? "" : "; @NM access" + getNonModifyingAccesses().map(access -> access.safeToString(visited, false)).collect(Collectors.joining(", "))) +
                    (modifyingAccess == null ? "" : "; @M access" + modifyingAccess.safeToString(visited, false)) +
                    (nonModifyingCallOuts.isEmpty() ? "" : "; @NM call-outs " + getNonModifyingCallouts().map(of -> of.safeToString(visited, false)).collect(Collectors.joining(", "))) +
                    (modifyingCallOut == null ? "" : "; @M call-out " + modifyingCallOut.safeToString(visited, false)) +
                    (localAssignments.isEmpty() ? "" : "; fields " + getLocalAssignments().map(FieldInfo::toString).collect(Collectors.joining(", "))) +
                    (next.isEmpty() ? "" : "; next " + getNext().map(of -> of.safeToString(visited, false)).collect(Collectors.joining(", "))) +
                    ">";
        }
        return "<flow of type " + type.stream() + ": " + origin + "@" + location + ">";
    }

    public String visited() {
        return "visited object flow @" + location;
    }

    @Override
    public String toString() {
        return safeToString(new HashSet<>(), false);
    }


    public Set<String> marks() {
        Set<String> marksSources = getPrevious().flatMap(of -> of.marks().stream()).collect(Collectors.toSet());
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

    // used in case of splitting an internal flow
    // the object flows in next are linked to "this" in the origin
    // (it can't be ObjectCreation, nor StaticFlow, nor can it be ParentFlow because we split going forward)
    public void moveNextTo(ObjectFlow newObjectFlow) {
        //for (ObjectFlow objectFlow : next) {
        //    if (objectFlow.origin == Origin.RESULT_OF_METHOD) {
        //        ((CallOutsArgumentToParameter) objectFlow.origin).replaceSource(this, newObjectFlow);
        //   }
        //} TODO
        newObjectFlow.next.addAll(next);
        next.clear();
    }


    /**
     * @param typeInfo the type to check for
     * @return also returns true if effectively immutable (not eventually!)
     */
    public boolean conditionsMetForEventual(TypeInfo typeInfo) {
        Set<String> set = typeInfo.typeAnalysis.get().marksRequiredForImmutable();
        // set.isEmpty() is a speed-up, marks() could be expensive && not necessary
        return set.isEmpty() || marks().containsAll(set);
    }

    /**
     * convenience method
     *
     * @param returnType te type to check for, uses bestTypeInfo
     * @return also returns true if effectively immutable (not eventually!)
     */
    public boolean conditionsMetForEventual(ParameterizedType returnType) {
        TypeInfo typeInfo = returnType.bestTypeInfo();
        return typeInfo != null && conditionsMetForEventual(typeInfo);
    }

    // for testing
    public boolean containsPrevious(ObjectFlow objectFlow) {
        return previous.contains(objectFlow);
    }
}
