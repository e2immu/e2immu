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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Properties {
    public static final Properties EMPTY = Properties.frozen();

    private final Map<Property, DV> map;

    private Properties(Map<Property, DV> map) {
        this.map = map;
    }

    public static Properties frozen() {
        return new Properties(Map.of());
    }

    public static Properties writable() {
        return new Properties(new HashMap<>());
    }

    public boolean isDone(Property property) {
        DV v = map.get(property);
        return v != null && v.isDone();
    }

    public DV getOrDefaultNull(Property property) {
        Objects.requireNonNull(property);
        return map.get(property);
    }

    public DV getOrDefault(Property property, DV defaultValue) {
        Objects.requireNonNull(property);
        Objects.requireNonNull(defaultValue);
        return map.getOrDefault(property, defaultValue);
    }

    public DV get(Property property) {
        Objects.requireNonNull(property);
        DV dv = map.get(property);
        Objects.requireNonNull(dv);
        return dv;
    }

    public void put(Property property, DV dv) {
        Objects.requireNonNull(dv);
        Objects.requireNonNull(property);
        DV inMap = map.get(property);
        if (inMap == null || inMap.isDelayed()) {
            map.put(property, dv);
        } else if (!inMap.equals(dv)) {
            throw new IllegalArgumentException("Changing value of " + property + " from " + inMap + " to " + dv);
        }
    }

    public Stream<Map.Entry<Property, DV>> stream() {
        return map.entrySet().stream();
    }

    public Map<Property, DV> toImmutableMap() {
        return Map.copyOf(map);
    }
}
