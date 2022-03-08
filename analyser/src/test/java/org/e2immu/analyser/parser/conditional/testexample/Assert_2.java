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

// differs from _1 in the assert statement in test

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

public class Assert_2 {

    @Constant(absent = true)
    static boolean test(@Modified Set<String> strings) {
        assert containsA(strings);

        Set<String> set = new HashSet<>(strings);
        return set.size() == strings.size();
    }

    @Constant("true")
    static boolean containsA(@NotModified Set<String> set) {
        assert !set.contains("a");
        return true;
    }
}
