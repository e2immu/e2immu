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

import java.util.function.Function;

public class MethodCall_6 {

    interface  A {}
    interface  B extends A {}

    public A accept(Function<B, A> f, B b) {
        return f.apply(b);
    }
    public B accept(Function<A, B> f, A a) {
        return f.apply(a);
    }

    public void test() {
        A a = new A() {
        };
        B b = new B() {
        };
        // CAUSES "Ambiguous method call": accept(bb -> bb, b);
        accept((B bb) -> bb, b);
        accept(aa -> (B)aa, a);
    }
}
