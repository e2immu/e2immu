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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Loops_11 {

    // semantic nonsense, but used to verify which variables are assigned in the loop
    public static Map<String, String> method(Set<String> queried, Map<String, String> map) {
        Map<String, String> result = new HashMap<>(); // NOT ASSIGNED
        Instant now = LocalDateTime.now().toInstant(ZoneOffset.UTC); // NOT ASSIGNED
        int count = 0; // ASSIGNED
        for (Map.Entry<String, String> entry : map.entrySet()) { // LOOP VARIABLE, NOT ASSIGNED
            String key = entry.getKey(); // local variable
            if (!queried.contains(key)) { // NOT ASSIGNED
                String container = entry.getValue(); // local variable
                if (container != null && container.compareTo(now.toString()) > 0) {
                    result.put(key, container);
                    count++;
                }
            }
        }
        System.out.println(count);
        return result;
    }
}
