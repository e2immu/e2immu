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

public class Lambda_7<T> {

    record Pair<K, V>(K k, V v) {
    }

    String method(List<Pair<T, String>> list) {
        StringBuilder sb = new StringBuilder();
        list.forEach(p -> sb.append(p.k()).append(": ").append(p.v).append("\n"));
        return sb.toString();
    }

    String method2(Map<T, String> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((t, s) -> sb.append(t).append(": ").append(s).append("\n"));
        return sb.toString();
    }
}
