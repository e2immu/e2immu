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

package org.e2immu.analyser.testexample;

import java.util.List;
import java.util.stream.Collectors;

public class TypeParameters_1<T> {
    private List<C2> strings2;

    static class C2<T> {
        final String s2;

        C2(String s2p) {
            this.s2 = s2p;
        }

        C2(T t2p) {
            s2 = t2p.toString();
        }
    }

    public TypeParameters_1(List<T> input2) {
        strings2 = input2.stream().map(C2::new).collect(Collectors.toList());
    }

    public List<C2> getStrings2() {
        return strings2;
    }
}
