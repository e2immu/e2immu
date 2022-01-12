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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;

public class Modification_2 {

    // quick check: the set2 field is not final now, public!

    @Container
    static class Example2bis {
        @Variable
        public Set<String> set2bis = new HashSet<>(); // ERROR: non-private field not effectively final

        @NotModified
        int size2() {
            return set2bis.size(); // WARN: potential null pointer exception!
        }
    }

    @E1Container
    static class Example2ter {
        @Modified
        private final Set<String> set2ter = new HashSet<>();

        @Modified
        public void add(String s) {
            set2ter.add(s);
        }

        @NotModified
        public String getFirst(String s) {
            return set2ter.isEmpty() ? "" : set2ter.stream().findAny().orElseThrow();
        }
    }

}
