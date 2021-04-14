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

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.PropagateModification;

import java.util.Collection;

/*
interesting issue

acceptAll is not an abstract method, but it only makes use of abstract methods.
As a consequence, it also qualifies for the @PropagateModification rule.
 */
public class PropagateModification_6 {

    interface MyConsumer<T> {
        void accept(T t);

        default void acceptAll(Collection<? extends T> collection) {
            collection.forEach(this::accept);
        }
    }

    private final Collection<String> strings;

    public PropagateModification_6(Collection<String> in) {
        this.strings = in;
    }

    @NotModified
    public void forEach(@NotModified @PropagateModification MyConsumer<String> myConsumer) {
        myConsumer.acceptAll(strings);
    }
}
