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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/*
Situation: consumer applied to a non-implicitly immutable field.

This type is not @E2Immutable because doSomething is not @Independent.
The consumer remains @NotModified, because it is not applied to a parameter.
FIXME how do we see that? there could be other reasons for doSomething to be @Modified
 */

@E1Container
public class ForEachMethod_9 {

    // not implicitly immutable
    private final Set<Integer> integers;

    @Independent
    public ForEachMethod_9(@NotModified Set<Integer> set) {
        integers = new HashSet<>(set);
    }

    @Modified
    public void doSomething(@IgnoreModifications @Dependent Consumer<Set<Integer>> consumer) {
        consumer.accept(integers);
    }

    public static void enrichWith27(@Modified ForEachMethod_9 in) {
        in.doSomething(set -> set.add(27)); // modifying lambda modifies in
    }

    public static void print(@NotModified ForEachMethod_9 in) {
        in.doSomething(System.out::println); // non-modifying method reference -> in not modified
    }

    public int size() {
        return integers.size();
    }
}
