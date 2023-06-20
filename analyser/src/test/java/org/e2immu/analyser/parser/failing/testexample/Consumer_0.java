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
import org.e2immu.annotation.rare.IgnoreModifications;

@ImmutableContainer(hc = true)
public class Consumer_0<S> {

    interface MyConsumer<T> {
        @Modified // contracted
        void accept(T t);
    }

    // of unbound parameter type
    private final S s;

    public Consumer_0(@Independent(hc = true) S in) {
        this.s = in;
    }

    // Note that @IgnoreModifications is ALWAYS contracted!
    @NotModified
    public void forEach(@IgnoreModifications @Independent(hc = true) MyConsumer<S> myConsumer) {
        myConsumer.accept(s);
    }

    @NotModified
    public void visit(@NotModified @Independent(hc = true) MyConsumer<S> myConsumer) {
        forEach(myConsumer);
    }
}
