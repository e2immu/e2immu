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

package org.e2immu.analyser.parser.conditional.testexample;

import org.e2immu.annotation.*;

import java.util.List;

/**
 * IMPORTANT: scope variables, so line numbers in the test; do not add lines except at end. Example of array+field combo
 */

@FinalFields @Container
public class NotNull_5 {

    // effectively not null eventually content not null, but we cannot mark before or after
    @NotNull
    public final String[] strings;

    public NotNull_5(int n) {
        strings = new String[n];
    }

    // NOTE: @NN1 for source is not enforced here
    @Modified
    // @Mark("assigned") there is no system for eventual @NotNull as of mid 2022.
    public void reInitialize(@NotNull @NotModified List<String> source) {
        int i = 0;
        while (i < strings.length) {
            for (String s : source) {
                if (i >= strings.length) break;
                strings[i++] = s;
            }
        }
    }
}
