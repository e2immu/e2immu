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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Constructor_3 {

    static record MemberValuePair(String clazz, String s) {
    }

    public void method( Map<Class<?>, Map<String, Object>> map) {
        for (Map.Entry<Class<?>, Map<String, Object>> entry : map.entrySet()) {
            List<Object> list;
            if (entry.getValue().equals(Map.of())) {
                list = List.of();
            } else {
                list = entry.getValue().entrySet().stream().map(e -> new MemberValuePair(e.getKey(), e.getValue().toString())).collect(Collectors.toList());
            }
            System.out.println(list);
        }
    }
}
