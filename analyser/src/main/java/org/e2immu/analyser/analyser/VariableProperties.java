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

import org.e2immu.support.Freezable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class VariableProperties extends Freezable {
    private final Map<VariableProperty, DV> map = new HashMap<>();

    public boolean isDone(VariableProperty variableProperty) {
        DV v = map.get(variableProperty);
        return v != null && v.isDone();
    }

    public DV getOrDefaultNull(VariableProperty variableProperty) {
        Objects.requireNonNull(variableProperty);
        return map.get(variableProperty);
    }

    public DV getOrDefault(VariableProperty variableProperty, DV defaultValue) {
        Objects.requireNonNull(variableProperty);
        Objects.requireNonNull(defaultValue);
        return map.getOrDefault(variableProperty, defaultValue);
    }

    public void put(VariableProperty variableProperty, DV dv) {
        Objects.requireNonNull(dv);
        Objects.requireNonNull(variableProperty);
        DV inMap = map.get(variableProperty);
        if (inMap == null || inMap.isDelayed()) {
            map.put(variableProperty, dv);
        } else if (!inMap.equals(dv)) {
            throw new IllegalArgumentException("Changing value of " + variableProperty + " from " + inMap + " to " + dv);
        }
    }

    public Stream<Map.Entry<VariableProperty, DV>> stream() {
        return map.entrySet().stream();
    }

    public Map<VariableProperty, DV> toImmutableMap() {
        return Map.copyOf(map);
    }
}
