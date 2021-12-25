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

import java.util.Map;
import java.util.stream.Collectors;

public class Lambda_9 {

    record DV(int i) {
        public DV max(DV other) {
            return i > other.i ? this : other;
        }
    }

    public Map<String, DV> method(Map<String, DV> map) {
        return map.entrySet().stream()
                .filter(e -> e.getValue().i > 10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, DV::max));
    }
}
