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

package org.e2immu.analyser.parser.loops.testexample;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Loops_18 {

    record Container(int read) {}

    private Map<String, Container> kvStore;

    public void setKvStore(Map<String, Container> kvStore) {
        this.kvStore = kvStore;
    }

    public Map<String, String> method(Set<String> queried) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Container> entry : kvStore.entrySet()) {
            String key = entry.getKey();
            if (!queried.contains(key)) {
                Container container = entry.getValue();
                if (container.read != 0) {
                    result.put(key, container.read + "?");
                }
            }
        }
        System.out.println(result);
        return result;
    }

}
