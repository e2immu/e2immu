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

package org.e2immu.analyser.objectflow.access;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

public class FieldAccess implements Access {
    public final FieldInfo fieldInfo;
    public final Access accessOnField;

    public FieldAccess(FieldInfo fieldInfo, @Nullable Access accessOnField) {
        this.fieldInfo = Objects.requireNonNull(fieldInfo);
        this.accessOnField = accessOnField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAccess that = (FieldAccess) o;
        return fieldInfo.equals(that.fieldInfo) &&
                Objects.equals(accessOnField, that.accessOnField);
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        if (detailed) {
            return "access " + fieldInfo.name + (accessOnField == null ? "" : "." + accessOnField.safeToString(visited, false));
        }
        return toString();
    }

    @Override
    public String toString() {
        return "access " + fieldInfo.name + (accessOnField == null ? "" : "." + accessOnField.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldInfo, accessOnField);
    }
}
