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

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodAccess implements Access {

    public final MethodInfo methodInfo;
    public final List<ObjectFlow> objectFlowsOfArguments;

    public MethodAccess(MethodInfo methodInfo, List<ObjectFlow> objectFlowsOfArguments) {
        this.methodInfo = methodInfo;
        this.objectFlowsOfArguments = List.copyOf(objectFlowsOfArguments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodAccess that = (MethodAccess) o;
        return methodInfo.equals(that.methodInfo) &&
                objectFlowsOfArguments.equals(that.objectFlowsOfArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, objectFlowsOfArguments);
    }

    @Override
    public String toString() {
        return (methodInfo == null ? "<lambda>" : methodInfo.name) + "(" + objectFlowsOfArguments.size() + " object flows)";
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        if (detailed) {
            return (methodInfo == null ? "<lambda>" : methodInfo.name) + "(" +
                    objectFlowsOfArguments.stream()
                            .map(of -> visited.contains(of) ? "visited object flow @" + of.location : of.safeToString(visited, false))
                            .collect(Collectors.joining(", ")) + ")";
        }
        return toString();
    }
}
