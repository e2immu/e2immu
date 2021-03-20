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

import org.e2immu.annotation.*;

import java.util.List;

/**
 * conclusion from this example:
 * <p>
 * The eventual system with @Only @Mark should work in the sense that there is only one modifying method
 *
 * We introduce @NotNull on @Container objects to indicate results, parameters and fields all at the same time
 *
 */

@E1Container
public class EventuallyNotNull1 {

    // effectively not null eventually content not null, but we cannot mark before or after
    @NotNull1(after = "assigned")
    public final String[] strings;

    public EventuallyNotNull1(int n) {
        strings = new String[n];
    }

    // NOTE: List is @NotNull1 because we say so in the annotated APIs
    @Modified
    @Mark("assigned")
    public void reInitialize(@NotNull1 @NotModified List<String> source) {
        int i = 0;
        while (i < strings.length) {
            for (String s : source) {
                if (i >= strings.length) break;
                strings[i++] = s;
            }
        }
    }
}
