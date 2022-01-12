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

@Independent
@Container
public class FunctionalInterface_5<T> {

    @NotModified
    private final Set<T> ts;

    @Variable
    private int counter;

    @Modified
    public int incrementAndGet() {
        return ++counter;
    }

    @Independent
    public FunctionalInterface_5(Set<T> ts) {
        this.ts = new HashSet<>(ts);
    }

    // both consumers are allowed to modify T elements;

    // these consumers are NOT allowed to call incrementAndGet
    @NotModified(contract = true)
    public void nonModifyingVisitor(Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

    // these consumers are allowed to call incrementAndGet
    @Modified
    public void modifyingVisitor(Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

    public static void useNonModifyingVisitor() {
        FunctionalInterface_5<Integer> f = new FunctionalInterface_5<>(Set.of(1, 2));
        f.nonModifyingVisitor(i -> System.out.println("I is " + i));
    }

    public static void useNonModifyingVisitorWithError() {
        FunctionalInterface_5<Integer> f = new FunctionalInterface_5<>(Set.of(1, 2));
        f.nonModifyingVisitor(i -> System.out.println("I is " + i + "; counting" + f.incrementAndGet())); // ERROR
    }

    public static void useModifyingVisitor() {
        FunctionalInterface_5<Integer> f = new FunctionalInterface_5<>(Set.of(1, 2));
        f.modifyingVisitor(i -> System.out.println("I is " + i + "; counting" + f.incrementAndGet()));
    }
}
