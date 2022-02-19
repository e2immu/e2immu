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

package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.Objects;

public class LocationImpl implements Location {
    public final WithInspectionAndAnalysis info;
    public final String statementIdentifier;
    public final Identifier identifier;
    // cached, speed-up because every CauseOfDelay has a location, and merging of causes of delay is big business
    private final int hashCode;

    public LocationImpl(WithInspectionAndAnalysis info) {
        this(info, null, info.getIdentifier());
    }

    public LocationImpl(WithInspectionAndAnalysis info, Identifier identifier) {
        this(info, null, identifier);
    }

    /**
     * @param info                type, method, field, parameter
     * @param statementIdentifier within a method, index + level (or simply index, if no other info available)
     * @param identifier          association with the source code
     */
    public LocationImpl(WithInspectionAndAnalysis info, String statementIdentifier, Identifier identifier) {
        this.info = Objects.requireNonNull(info);
        this.statementIdentifier = statementIdentifier;
        this.identifier = Objects.requireNonNull(identifier);
        hashCode = Objects.hash(info, statementIdentifier);
    }

    @Override
    public Identifier identifier() {
        return identifier;
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
        LocationImpl location = (LocationImpl) o;
        if (this == NOT_YET_SET || o == NOT_YET_SET) return false;
        return info.equals(location.info) && Objects.equals(statementIdentifier, location.statementIdentifier);
    }

    @Override
    public boolean equalsIgnoreStage(Location other) {
        if (this == other) return true;
        if (other == null) return false;
        if (this == NOT_YET_SET || other == NOT_YET_SET) return false;
        return info.equals(other.getInfo()) && Objects.equals(Stage.without(statementIdentifier), Stage.without(other.statementIdentifierOrNull()));
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        if (info == null) {
            return "NOT_YET_SET";
        }
        return info.niceClassName() + " " + info.fullyQualifiedName()
                + (identifier instanceof Identifier.PositionalIdentifier pi ? " (line " + pi.line() + ", pos " + pi.pos() + ")" :
                (statementIdentifier == null ? "" : " (statement " + statementIdentifier + ")"));
    }

    public String detailedLocation() {
        if (info == null) {
            return "NOT_YET_SET";
        }
        return info.niceClassName() + " " + info.fullyQualifiedName() + (statementIdentifier == null ? "" : ", statement " + statementIdentifier);
    }

    public int compareTo(Location otherLocation) {
        LocationImpl other = (LocationImpl) otherLocation;
        if (info == null || other.info == null) {
            throw new UnsupportedOperationException("Encountering NOT_YET_SET");
        }
        int c = info.getTypeInfo().primaryType().fullyQualifiedName
                .compareTo(other.info.getTypeInfo().primaryType().fullyQualifiedName);
        if (c != 0) return c;
        if (identifier != null && other.identifier != null) {
            return identifier.compareTo(other.identifier);
        }
        if (statementIdentifier != null && other.statementIdentifier != null) {
            return statementIdentifier.compareTo(other.statementIdentifier);
        }
        return 0;
    }

    /*
    best toString method to show in a delay; as brief as possible
     */
    public String toDelayString() {
        if (info == null) return "not_yet_set";
        return info.niceClassName() + "_" + info.name() + (statementIdentifier == null ? "" : "_" + statementIdentifier);
    }

    @Override
    public String delayStringWithoutStatementIdentifier() {
        if (info == null) return "not_yet_set";
        return info.niceClassName() + "_" + info.name() + (statementIdentifier == null ? "" : "_" + Stage.without(statementIdentifier));
    }

    @Override
    public WithInspectionAndAnalysis getInfo() {
        return info;
    }

    @Override
    public String statementIdentifierOrNull() {
        return statementIdentifier;
    }
}
