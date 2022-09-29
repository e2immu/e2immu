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

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/*
simpler than _9
 */
@ImmutableContainer(hc = true)
public class Independent1_9_1<T> {

    private final Map<T, Boolean> map = new HashMap<>();

    @SafeVarargs
    @Independent(hc = true, contract = true)
    public static <T> Independent1_9_1<T> of(@NotModified Independent1_9_1<T>... maps) {
        Independent1_9_1<T> result = new Independent1_9_1<>();
        if (maps != null) {
            for (Independent1_9_1<T> map : maps) {
                for (Map.Entry<T, Boolean> e : map.map.entrySet()) {
                    result.put(e.getKey(), e.getValue());
                }
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
    @Independent(hc = true)
    public Stream<T> stream() {
        return map.keySet().stream();
    }

    @ImmutableContainer(hc = true)
    @Independent(hc = true)
    @NotModified
    public List<T> keys() {
        return stream().toList();
    }
}
