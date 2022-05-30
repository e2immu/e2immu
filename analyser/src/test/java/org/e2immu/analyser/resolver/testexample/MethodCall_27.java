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

package org.e2immu.analyser.resolver.testexample;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

// see also MethodCall_7
public class MethodCall_27<A, B> {

    public void method(List<B> list, Predicate<B> b) {
        b.test(list.get(0));
    }

    public void method(List<B> list, Consumer<B> b) {
        b.accept(list.get(0));
    }

    public void method(List<A> list, BiConsumer<A, B> a) {
        a.accept(list.get(0), null);
    }

    public void test(A a, B b) {
        // COMPILATION ERROR: method(List.of(bb), System.out::println);
        method(List.of(a), (x, y) -> System.out.println(x + " " + y));
        method(List.of(b), x -> x.toString().length() > 3);
    }
}
