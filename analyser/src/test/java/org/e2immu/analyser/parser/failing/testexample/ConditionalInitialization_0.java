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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.Container;

import java.util.HashSet;
import java.util.Set;

// The type is MUTABLE! each construction potentially overwrites set (agreed, with the same value,
// but that's going too far)
@Container
public class ConditionalInitialization_0 {
    private static Set<String> set = new HashSet<>(); // @NotNull

    public ConditionalInitialization_0(boolean b) {
        if (set.isEmpty()) {
            set = Set.of("a", "b"); // @NotNull1
        }
        if (b) {
            // here, the CI copy should not exist
            System.out.println("Set is " + set);
        }
    }

    public static boolean contains(String c) {
        return set.contains(c);
    }
}
