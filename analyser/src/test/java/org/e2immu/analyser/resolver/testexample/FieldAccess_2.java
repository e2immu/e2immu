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
import java.util.stream.Stream;

public record FieldAccess_2(Container c) {

    interface VIC {
        String current();
    }

    record Variables(Map<String, VIC> variables) {
        Stream<Map.Entry<String, VIC>> stream() {
            return variables.entrySet().stream();
        }
    }

    record Container(Variables v) {

    }

    public void test() {
        c.v.stream().map(Map.Entry::getValue).forEach(vic -> System.out.println(vic.current()));
    }
}
