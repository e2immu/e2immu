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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

/*
change wrt Consumer_0,_1,_2: not calling the abstract method, so no hidden content is communicated.
 */
public class Consumer_3<S> {

    @Container
    abstract static class ClassWithConsumer<T> {
        private final String name;
        private int countCalls;

        public ClassWithConsumer(String name) {
            this.name = name;
        }

        @Modified
        abstract void accept(T t);

        @NotModified
        public String getName() {
            return name;
        }

        @Modified
        public int increment() {
            return countCalls++;
        }
    }

    private final S s;

    public Consumer_3(S in) {
        this.s = in;
    }

    /*
     In this example, the abstract method is not called, so there's no need for @Dependent2.
     The modifying method is called, however, so myConsumer is @Modified.
     */

    @NotModified
    public String forEach(@Modified @Independent ClassWithConsumer<S> myConsumer) {
        return s + ": Consumer is " + myConsumer.getName() + ", count " + myConsumer.increment();
    }
}
