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

import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Collection;

/*
interesting issue

acceptAll is not an abstract method, but it only makes use of abstract methods.
As a consequence, it also allows for hidden content transfer.

We assume that Collection.forEach has an @IgnoreModifications on its parameter.
 */
public class Consumer_4<S> {

    interface MyConsumer<T> {
        // contracted
        @Modified
        void accept(T t);

        // not contracted
        @NotModified
        default void acceptAll(@NotModified @NotNull(content = true) @Independent(hc = true) Collection<? extends T> collection) {
            collection.forEach(this::accept);
        }
    }

    @NotNull(content = true)
    private final Collection<S> strings;

    /*
    the parameter is dependent!
     */
    public Consumer_4(@NotNull(content = true) @Independent(absent = true) Collection<S> in) {
        this.strings = in;
    }

    @NotModified
    public void forEach(@NotModified @Independent(hc = true) MyConsumer<S> myConsumer) {
        myConsumer.acceptAll(strings);
    }
}
