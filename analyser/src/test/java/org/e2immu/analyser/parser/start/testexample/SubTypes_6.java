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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.*;

import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

/*
There is no hidden content, since Function<> and RemoveOne, the types of the fields, are created as lambdas.

 */
@ImmutableContainer
public class SubTypes_6 {

    @NotModified
    @Final
    @NotNull
    private static Function<Set<String>, Set<String>> removeElement =
            (@NotNull @Modified Set<String> set1) -> {
                Iterator<String> it1 = set1.iterator();
                if (it1.hasNext()) it1.remove();
                return set1;
            };

    // an alternative approach is to use an interface, which also allows for the @Identity

    @FunctionalInterface
    interface RemoveOne {
        @Identity
        Set<String> go(@NotNull @Modified Set<String> in);
    }

    @NotModified
    final static RemoveOne removeOne = set2 -> {
        Iterator<String> it2 = set2.iterator();
        if (it2.hasNext()) it2.remove();
        return set2;
    };

}
