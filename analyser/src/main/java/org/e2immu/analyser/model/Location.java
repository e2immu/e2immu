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

public class Location {
    public final WithInspectionAndAnalysis info;
    public final String statementIndexInMethod;
    public final Identifier identifier;

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
        return info.equals(location.info) && Objects.equals(statementIndexInMethod, location.statementIndexInMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, statementIndexInMethod);
    }

    @Override
    public String toString() {
        return info.niceClassName() + " " + info.fullyQualifiedName()
                + (identifier instanceof Identifier.PositionalIdentifier pi ? " (line " + pi.line() + ", pos " + pi.pos() + ")" :
                (statementIndexInMethod == null ? "" : " (statement " + statementIndexInMethod + ")"));
    }

    public String detailedLocation() {
        String type;
        if (info instanceof TypeInfo) type = "Type";
        else if (info instanceof FieldInfo) type = "Field";
        else if (info instanceof MethodInfo mi) {
            if (mi.isConstructor) type = "Constructor";
            else type = "Method";
        } else if (info instanceof ParameterInfo) type = "Parameter";
        else throw new UnsupportedOperationException();
        return type + " " + info.fullyQualifiedName() + (statementIndexInMethod == null ? "" : ", statement " + statementIndexInMethod);
    }
}
