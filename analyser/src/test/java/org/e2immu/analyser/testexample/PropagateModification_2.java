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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;
import org.e2immu.annotation.PropagateModification;

public class PropagateModification_2 {

    abstract static class ClassWithConsumer<T> {
        private final String name;
        private int countCalls;

        public ClassWithConsumer(String name) {
            this.name = name;
        }

        // in test1 we demand @NotNull, here we do not
        abstract void accept(T t);

        public String getName() {
            return name;
        }

        public int increment() {
            return countCalls++;
        }
    }

    @Nullable
    private final String string;

    public PropagateModification_2(String in) {
        this.string = in;
    }

    @NotModified
    public void forEach(@Modified @PropagateModification ClassWithConsumer<String> myConsumer) {
        System.out.println("Consumer is " + myConsumer.getName());
        if (myConsumer.increment() % 2 == 0) {
            myConsumer.accept(string);
        }
    }
}
