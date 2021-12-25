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

package org.e2immu.analyser.resolver.testexample;


import java.util.List;
import java.util.Set;

public class Lambda_8 {

    record DV(int j) {

        public DV min(DV other) {
            return this;
        }
    }

    static final DV START = new DV(9);

    static class Type {
        DV toDV(int j) {
            return new DV(j);
        }
    }

    static class FieldReference {
        Type type() {
            return new Type();
        }
    }

    public Set<Type> toTypes(FieldReference fieldReference) {
        return Set.of(fieldReference.type());
    }

    public DV method(List<FieldReference> fields) {
        return fields.stream()
                // hidden content is available, because linking has been computed(?)
                .flatMap(fr -> toTypes(fr).stream())
                .map(pt -> pt.toDV(8))
                .reduce(START, DV::min);
    }
}
