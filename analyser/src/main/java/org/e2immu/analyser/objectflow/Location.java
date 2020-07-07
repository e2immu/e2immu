package org.e2immu.analyser.objectflow;


import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.Objects;

public class Location {
    public static final Location NO_LOCATION = new Location();

    public final WithInspectionAndAnalysis info;

    public Location(WithInspectionAndAnalysis info) {
        this.info = Objects.requireNonNull(info);
    }

    private Location() {
        info = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(info, location.info);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info);
    }

    @Override
    public String toString() {
        return info == null ? "<no location>" : info.detailedName();
    }
}
