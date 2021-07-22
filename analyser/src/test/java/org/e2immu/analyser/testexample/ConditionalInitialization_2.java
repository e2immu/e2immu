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

import java.util.HashSet;
import java.util.Set;

// small variant on CI_1 to test a bad assignment

public class ConditionalInitialization_2 {
    private Set<String> set = new HashSet<>();

    public ConditionalInitialization_2(boolean b) {
        if (b) {
            set = Set.of("a", "b"); // @NotNull1
        } else {
            // here, the CI copy should not exist
            System.out.println("Set is " + set);
        }
    }

    public void setSet(Set<String> setParam, boolean c) {
        if (c) {
            this.set = set;
        }
    }

    public Set<String> getSet() {
        return set;
    }
}
