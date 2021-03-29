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
    public final String statementWithinMethod;
    public final Expression expression; // in the same statement, there can be multiple identical flows starting...

    public Location(WithInspectionAndAnalysis info) {
        this(Objects.requireNonNull(info), null, null);
    }

    public Location(WithInspectionAndAnalysis info, Expression expression) {
        this(Objects.requireNonNull(info), null, expression);
    }

    public Location(MethodInfo methodInfo, String statementIndex) {
        this(Objects.requireNonNull(methodInfo), statementIndex, null);
    }

    public Location(WithInspectionAndAnalysis info, String statementWithinMethod, Expression expression) {
        this.info = info;
        this.statementWithinMethod = statementWithinMethod;
        this.expression = expression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(info, location.info) &&
                Objects.equals(statementWithinMethod, location.statementWithinMethod) &&
                Objects.equals(expression, location.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, statementWithinMethod, expression);
    }

    @Override
    public String toString() {
        return info == null ? "<no location>" : typeLetter(info) + ":" + info.name()
                + (statementWithinMethod == null ? "" : ":" + statementWithinMethod)
                + (expression == null ? "" : "#" + expression.toString());
    }

    public static String typeLetter(WithInspectionAndAnalysis info) {
        return Character.toString(info.getClass().getSimpleName().charAt(0));
    }
}
