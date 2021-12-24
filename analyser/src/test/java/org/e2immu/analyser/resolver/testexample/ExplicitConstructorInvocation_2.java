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

public class ExplicitConstructorInvocation_2 {

    static class C1 {
        public final Map<String, Integer> map;

        public C1() {
            this(Map.of());
        }

        public C1(Map<String, Integer> map) {
            this.map = Map.copyOf(map);
        }
    }

    static class C2 extends C1 {

        public C2(Map<String, Integer> map) {
            super(map);
        }
    }

    static class C3 extends C2 {

        public C3() {
            super(Map.of());
        }

        public C3(Map<String, Integer> map) {
            super(map);
        }
    }

}
