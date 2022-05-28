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

import java.util.Map;

// same as _17, but final
// IMPORTANT: @NotNull1 means that the elements of iterable are never null
// this is not semantically the same as no keys or values are ever null!!

// there is no mechanism yet (2022 05 28) for Map.Entry to become @NotNull1
public class Loops_24 {

    @NotNull1
    private final Map<String, Integer> map;

    public Loops_24(@NotNull1 Map<String, Integer> map) {
        this.map = map;
    }

    public int method() {
        int res = 3;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() == 9) {
                res = 4;
                break;
            }
        }
        return res;
    }

}
