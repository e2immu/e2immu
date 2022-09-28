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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/*
similar to putAll in UpgradableBooleanMap
 */
@ImmutableContainer(hc = true)
public class Independent1_9<T> {

    private final Map<T, Boolean> map = new HashMap<>();

    @SafeVarargs
    @Independent
    public static <T> Independent1_9<T> of(Independent1_9<T>... maps) {
        Independent1_9<T> result = new Independent1_9<>();
        if (maps != null) {
            for (Independent1_9<T> map : maps) {
                result.putAll(map);
            }
        }
        return result;
    }

    @Modified(construction = true)
    private void put(T t, boolean b) {
        if (!map.containsKey(t)) {
            map.put(t, b);
        }
    }

    @ImmutableContainer(hc = true)
    @NotNull // TODO (content = true) has not been implemented yet
    public Stream<Map.Entry<T, Boolean>> stream() {
        return map.entrySet().stream().map(e -> new UpgradableBooleanMap.ImmutableEntry<>(e.getKey(), e.getValue()));
    }

    @Modified(construction = true)
    private void putAll(@Independent(hc = true) Independent1_9<T> other) {
        other.stream().forEach(e -> this.put(e.getKey(), e.getValue()));
    }
}
