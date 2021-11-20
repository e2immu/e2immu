/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model;

import java.util.Objects;

public class Location implements Comparable<Location> {
    public static final Location NOT_YET_SET = new Location();
    public final WithInspectionAndAnalysis info;
    public final String statementIndexInMethod;
    public final Identifier identifier;

    private Location() {
        this.info = null;
        this.statementIndexInMethod = null;
        this.identifier = Identifier.CONSTANT;
    }

    public Location(WithInspectionAndAnalysis info) {
        this(info, null, info.getIdentifier());
    }

    public Location(WithInspectionAndAnalysis info, Identifier identifier) {
        this(info, null, identifier);
    }

    public Location(WithInspectionAndAnalysis info, String statementIndexInMethod, Identifier identifier) {
        this.info = Objects.requireNonNull(info);
        this.statementIndexInMethod = statementIndexInMethod;
        this.identifier = Objects.requireNonNull(identifier);
    }

    /*
    Important: we look at the WithInspection (Type, method, field) and the statement index,
    but not at the identifier. This allows us to check if a certain error is present, or not, as in
    StatementAnalyser.checkUselessAssignments()
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        if(this == NOT_YET_SET || o == NOT_YET_SET) return false;
        return info.equals(location.info) && Objects.equals(statementIndexInMethod, location.statementIndexInMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, statementIndexInMethod);
    }

    @Override
    public String toString() {
        if (info == null) {
            return "NOT_YET_SET";
        }
        return info.niceClassName() + " " + info.fullyQualifiedName()
                + (identifier instanceof Identifier.PositionalIdentifier pi ? " (line " + pi.line() + ", pos " + pi.pos() + ")" :
                (statementIndexInMethod == null ? "" : " (statement " + statementIndexInMethod + ")"));
    }

    public String detailedLocation() {
        if (info == null) {
            return "NOT_YET_SET";
        }
        return info.niceClassName() + " " + info.fullyQualifiedName() + (statementIndexInMethod == null ? "" : ", statement " + statementIndexInMethod);
    }

    public int compareTo(Location other) {
        if (info == null || other.info == null) {
            throw new UnsupportedOperationException("Encountering NOT_YET_SET");
        }
        int c = info.getTypeInfo().primaryType().fullyQualifiedName
                .compareTo(other.info.getTypeInfo().primaryType().fullyQualifiedName);
        if (c != 0) return c;
        if (identifier != null && other.identifier != null) {
            return identifier.compareTo(other.identifier);
        }
        if (statementIndexInMethod != null && other.statementIndexInMethod != null) {
            return statementIndexInMethod.compareTo(other.statementIndexInMethod);
        }
        return 0;
    }

    /*
    best toString method to show in a delay; as brief as possible
     */
    public String toDelayString() {
        if(info == null) return "not_yet_set";
        return info.niceClassName() + "_" + info.name() + (statementIndexInMethod == null ? "" : "_" + statementIndexInMethod);
    }
}
