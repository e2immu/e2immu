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

@E2Container // computed
public class Consumer_0<S> {

    // implicitly: @E1Immutable (not @Container, @Modified on parameter t)
    interface MyConsumer<T> {
        @Modified
        void accept(T t);
    }

    // of unbound parameter type
    private final S s;

    public Consumer_0(S in) {
        this.s = in;
    }

    // Note that @IgnoreModifications is ALWAYS contracted!
    @NotModified
    public void forEach(@IgnoreModifications @Independent1 MyConsumer<S> myConsumer) {
        myConsumer.accept(s);
    }

    @NotModified
    public void visit(@NotModified @Independent1 MyConsumer<S> myConsumer) {
        forEach(myConsumer);
    }
}
