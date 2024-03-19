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

package org.e2immu.analyser.util;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.type.UtilityClass;

import java.util.HashSet;
import java.util.Set;

@UtilityClass
public class SetUtil {

    private SetUtil() {
    }

    public static <T> Set<T> immutableUnion(@NotNull(content = true) @NotModified Set<T> set1,
                                            @NotNull(content = true) @NotModified Set<T> set2) {
        if (set1.isEmpty()) return set2;
        if (set2.isEmpty()) return set1;
        Set<T> builder = new HashSet<>(set1);
        builder.addAll(set2);
        return Set.copyOf(builder);
    }
}
