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

package org.e2immu.analyser.analyser.delay;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Location;

import java.util.Objects;

public class SimpleCause implements CauseOfDelay {
    private final Location location;
    private final CauseOfDelay.Cause cause;
    private final String withoutStatementIdentifier;

    public SimpleCause(Location location, CauseOfDelay.Cause cause) {
        this.cause = cause;
        this.location = location;
        this.withoutStatementIdentifier = cause.label + "@" + location.delayStringWithoutStatementIdentifier();
    }

    @Override
    public int compareTo(CauseOfDelay o) {
        if (o instanceof SimpleCause sc) {
            int c = cause.compareTo(sc.cause);
            if (c != 0) return c;
            return location.compareTo(sc.location);
        }
        return 1; // VC comes before SimpleCause
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleCause that = (SimpleCause) o;
        return location.equals(that.location) && cause == that.cause;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, cause);
    }

    @Override
    public String toString() {
        return cause.label + "@" + location.toDelayString();
    }

    @Override
    public Cause cause() {
        return cause;
    }

    @Override
    public String withoutStatementIdentifier() {
        return withoutStatementIdentifier;
    }

    @Override
    public Location location() {
        return location;
    }

    @Override
    public boolean variableIsField(FieldInfo fieldInfo) {
        return location().getInfo() instanceof FieldInfo fi && fi == fieldInfo;
    }
}
