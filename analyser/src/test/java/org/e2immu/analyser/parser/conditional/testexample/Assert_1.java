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

// is Precondition_4, but then in a method

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

public class Assert_1 {

    // because there are no Annotated APIs, (1) the HashSet<> constructor modifies the parameter
    // and (2) there are no companion methods to determine the constant outcome
    @ImmutableContainer
    static boolean test(@Modified Set<String> strings) {
        containsA(strings);

        Set<String> set = new HashSet<>(strings);
        return set.size() == strings.size();
    }

    @ImmutableContainer("true")
    static boolean containsA(@NotModified Set<String> set) {
        assert !set.contains("a");
        return true;
    }
}
