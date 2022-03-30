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

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.model.TypeInfo;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * SortedTypes -> List<TypeCycle>
 * TypeCycle
 * - A, B, C: List<SortedType>
 *   SortedType = primary type + methods, fields, types
 * - D: List<WithInspectionAndAnalysis>
 *   pre-computed ordering
 */
public record SortedTypes(List<TypeCycle> typeCycles) {

    public static final SortedTypes EMPTY = new SortedTypes(List.of());

    @Override
    public String toString() {
        return typeCycles.stream().map(Object::toString).collect(Collectors.joining("; "));
    }

    public Stream<TypeInfo> primaryTypeStream() {
        return typeCycles.stream().flatMap(TypeCycle::primaryTypeStream);
    }

    public boolean isEmpty() {
        return typeCycles.isEmpty();
    }
}
