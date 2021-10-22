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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.ParameterizedType;

import java.util.Set;
import java.util.stream.Collectors;

public record SetOfTypes(Set<ParameterizedType> types) {

    public static final SetOfTypes EMPTY = new SetOfTypes(Set.of());

    public SetOfTypes(Set<ParameterizedType> types) {
        this.types = Set.copyOf(types);
    }

    // if T is hidden, then ? extends T is hidden as well
    public boolean contains(ParameterizedType parameterizedType) {
        if (types.contains(parameterizedType)) return true;
        if (parameterizedType.typeParameter != null && parameterizedType.wildCard != ParameterizedType.WildCard.NONE) {
            ParameterizedType withoutWildcard = new ParameterizedType(parameterizedType.typeParameter, parameterizedType.arrays,
                    ParameterizedType.WildCard.NONE);
            return types.contains(withoutWildcard);
        }
        return false;
    }

    public boolean isEmpty() {
        return types.isEmpty();
    }

    @Override
    public String toString() {
        return types.stream().map(ParameterizedType::toString).sorted().collect(Collectors.joining(", "));
    }
}
