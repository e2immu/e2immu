package org.e2immu.analyser.objectflow;


import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.Objects;

public class Location {
    public static final Location NO_LOCATION = new Location((WithInspectionAndAnalysis) null, null);

    public final WithInspectionAndAnalysis info;
    public final String statementWithinMethod;

    public Location(WithInspectionAndAnalysis info) {
        this(Objects.requireNonNull(info), null);
    }

    public Location(MethodInfo methodInfo, NumberedStatement currentStatement) {
        this(Objects.requireNonNull(methodInfo), currentStatement.streamIndices());
    }

    private Location(WithInspectionAndAnalysis info, String statementWithinMethod) {
        this.info = info;
        this.statementWithinMethod = statementWithinMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(info, location.info) &&
                Objects.equals(statementWithinMethod, location.statementWithinMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, statementWithinMethod);
    }

    @Override
    public String toString() {
        return info == null ? "<no location>" : ObjectFlow.typeLetter(info) + ":" + info.name() + (statementWithinMethod == null ? "" : ":" + statementWithinMethod);
    }

    public void registerNewObjectFlow(ObjectFlow objectFlowOfResult) {
        if (info instanceof MethodInfo) {
            ((MethodInfo) info).methodAnalysis.get().addInternalObjectFlow(objectFlowOfResult);
        } else if (info instanceof FieldInfo) {
            ((FieldInfo) info).fieldAnalysis.get().addInternalObjectFlow(objectFlowOfResult);
        }
    }
}
