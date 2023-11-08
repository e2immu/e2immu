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

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Will have to be merged with MethodTypeParameterMap at some stage
 */
public record TypeParameterMap(Map<NamedType, ParameterizedType> map) {

    public TypeParameterMap {
        assert map != null;
    }

    public static final TypeParameterMap EMPTY = new TypeParameterMap();

    private TypeParameterMap() {
        this(Map.of());
    }

    public TypeParameterMap merge(TypeParameterMap other) {
        if (other.isEmpty()) return this;
        if (isEmpty()) return other;
        Map<NamedType, ParameterizedType> newMap = new HashMap<>(map);
        newMap.putAll(other.map);
        if (newMap.size() > 1 && containsCycles(newMap)) {
            return this;
        }
        return new TypeParameterMap(Map.copyOf(newMap));
    }

    private boolean containsCycles(Map<NamedType, ParameterizedType> newMap) {
        for (NamedType start : newMap.keySet()) {
            if (containsCycles(start, newMap)) return true;
        }
        return false;
    }

    private boolean containsCycles(NamedType start, Map<NamedType, ParameterizedType> newMap) {
        Set<NamedType> visited = new HashSet<>();
        NamedType s = start;
        while (s != null) {
            if (!visited.add(s)) {
                return true;
            }
            ParameterizedType t = newMap.get(s);
            if (t != null && t.isTypeParameter()) {
                s = t.typeParameter;
            } else break;
        }
        return false;
    }

    private boolean isEmpty() {
        return map.isEmpty();
    }
}
