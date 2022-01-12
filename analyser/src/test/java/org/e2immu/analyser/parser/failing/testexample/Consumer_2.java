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

/*
change wrt ForEachMethod_1: the abstract class's fields are modifiable,
which allows for other types of modification.
The method forEach is now @Modified because of the increment() call.
Other change is the @Nullable on field s.
 */
public class Consumer_2<S> {

    @Container
    abstract static class ClassWithConsumer<T> {
        private final String name;
        private int countCalls;

        public ClassWithConsumer(String name) {
            this.name = name;
        }

        @Modified
        abstract void accept(T t); // implicit: @NotModified, because @Container

        @NotModified
        public String getName() {
            return name;
        }

        @Modified
        public int increment() {
            return countCalls++;
        }
    }

    @Nullable
    private final S s;

    public Consumer_2(S in) {
        this.s = in;
    }

    @NotModified
    public void forEach(@Modified @Independent1 ClassWithConsumer<S> myConsumer) {
        System.out.println("Consumer is " + myConsumer.getName());
        if (myConsumer.increment() % 2 == 0) {
            myConsumer.accept(s);
        }
    }
}
