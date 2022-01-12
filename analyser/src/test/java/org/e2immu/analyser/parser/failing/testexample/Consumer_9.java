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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/*
Situation: consumer applied to a non-transparent field.

The consumer 'in' in 'print' is @NotModified; reasoning goes as follows:

the parameter 'consumer' is dependent, so we need to link the modification to the parameter in 'print'.
In this particular case, there is no modification, so 'in' remains @NotModified.
 */

@E1Container
public class Consumer_9 {

    // not transparent
    private final Set<Integer> integers;

    @Independent
    public Consumer_9(@NotModified Set<Integer> set) {
        integers = new HashSet<>(set);
    }

    @Modified
    public void doSomething(@IgnoreModifications @Dependent Consumer<Set<Integer>> consumer) {
        consumer.accept(integers);
    }

    public static void enrichWith27(@Modified Consumer_9 in) {
        in.doSomething(set -> set.add(27)); // modifying lambda modifies in
    }

    public static void print(@NotModified Consumer_9 in) {
        in.doSomething(System.out::println); // non-modifying method reference -> in not modified
    }

    public int size() {
        return integers.size();
    }
}
