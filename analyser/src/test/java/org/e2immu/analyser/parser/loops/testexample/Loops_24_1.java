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

import org.e2immu.annotation.NotNull1;

import java.util.Set;

// same as _17, but then with sets
// requires context computation over all methods, not only the constructor

public class Loops_24_1 {

    @NotNull1
    private final Set<String> set;

    public Loops_24_1(@NotNull1 Set<String> set) {
        this.set = set;
    }

    public int method() {
        int res = 3;
        for (String s : set) {
            if (s.length() == 9) {
                res = 4;
                break;
            }
        }
        return res;
    }

}
