package org.e2immu.analyser.objectflow;


import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.Objects;
import java.util.StringJoiner;

public class Location {
    public final WithInspectionAndAnalysis info;

    public Location(WithInspectionAndAnalysis info) {
        this.info = info;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return info.equals(location.info);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info);
    }

    @Override
    public String toString() {
        return info.name();
    }
}
