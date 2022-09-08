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

import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record SetOfTypes(Set<ParameterizedType> types) {

    public static final SetOfTypes EMPTY = new SetOfTypes(Set.of());

    public SetOfTypes(Set<ParameterizedType> types) {
        this.types = Set.copyOf(types);
    }

    public static SetOfTypes of(ParameterizedType pt) {
        return new SetOfTypes(Set.of(pt));
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
        return types.stream().map(ParameterizedType::printSimple).sorted().collect(Collectors.joining(", "));
    }

    public SetOfTypes union(SetOfTypes other) {
        Set<ParameterizedType> set = new HashSet<>(types);
        set.addAll(other.types);
        return new SetOfTypes(set);
    }

    public SetOfTypes intersection(SetOfTypes other) {
        Set<ParameterizedType> set = new HashSet<>(types);
        set.retainAll(other.types);
        return new SetOfTypes(set);
    }

    public int size() {
        return types.size();
    }

    /*
    make a translation map based on pt2, and translate from formal to concrete.

    If types contains E=formal type parameter of List<E>, and pt = List<T>, we want
    to return a SetOfTypes containing T instead of E
     */
    public SetOfTypes translate(InspectionProvider inspectionProvider, ParameterizedType pt) {
        Map<NamedType, ParameterizedType> map = pt.initialTypeParameterMap(inspectionProvider);
        return new SetOfTypes(types.stream()
                .map(t -> {
                    // pt is a type without type parameters, and t is a type parameter
                    if (map.isEmpty() && t.isAssignableFrom(inspectionProvider, pt)) {
                        return pt;
                    }
                    return t.applyTranslation(inspectionProvider.getPrimitives(), map);
                })
                .collect(Collectors.toUnmodifiableSet()));
    }

    public SetOfTypes dropArrays() {
        if (types.isEmpty()) return this;
        Set<ParameterizedType> newSet = types.stream()
                .map(ParameterizedType::copyWithoutArrays)
                .collect(Collectors.toUnmodifiableSet());
        return new SetOfTypes(newSet);
    }
}
