package org.e2immu.analyser.objectflow;


import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.Objects;

public class Location {
    public static final Location NO_LOCATION = new Location((WithInspectionAndAnalysis) null, null, 0);

    public final WithInspectionAndAnalysis info;
    public final String statementWithinMethod;
    public final int counter; // in the same statement, there can be multiple identical flows starting...

    public Location(WithInspectionAndAnalysis info) {
        this(Objects.requireNonNull(info), null, 0);
    }

    public Location(WithInspectionAndAnalysis info, int counter) {
        this(Objects.requireNonNull(info), null, counter);
    }

    public Location(MethodInfo methodInfo, NumberedStatement currentStatement) {
        this(Objects.requireNonNull(methodInfo), currentStatement.streamIndices(), 0);
    }

    public Location(MethodInfo methodInfo, NumberedStatement currentStatement, int counter) {
        this(Objects.requireNonNull(methodInfo), currentStatement.streamIndices(), counter);
    }

    private Location(WithInspectionAndAnalysis info, String statementWithinMethod, int counter) {
        this.info = info;
        this.statementWithinMethod = statementWithinMethod;
        this.counter = counter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(info, location.info) &&
                Objects.equals(statementWithinMethod, location.statementWithinMethod) &&
                counter == location.counter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, statementWithinMethod, counter);
    }

    @Override
    public String toString() {
        return info == null ? "<no location>" : ObjectFlow.typeLetter(info) + ":" + info.name()
                + (statementWithinMethod == null ? "" : ":" + statementWithinMethod)
                + (counter == 0 ? "" : "#" + counter);
    }
}
