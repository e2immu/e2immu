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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

// almost exact copy of 1, but now without the @PropagateModification on accept
public class PropagateModification_8 {

    @E2Container
    abstract static class ClassWithConsumer<T> {
        private final String name;

        public ClassWithConsumer(String name) {
            this.name = name;
        }

        // implicitly @NotModified, because CWC is not a @FunctionalInterface, and it is @E2Immutable after some iterations
        abstract void abstractAccept(@NotNull T t);

        public String getName() {
            return name;
        }
    }

    @NotNull
    private final String string;

    public PropagateModification_8(@NotNull String in) {
        this.string = in;
    }

    // do not write @NotModified on myConsumer, because the type is @E2Container (implicit!)
    @NotModified
    public String forEach(ClassWithConsumer<String> myConsumer) {
        String n = myConsumer.getName();
        myConsumer.abstractAccept(string);
        return n;
    }
}
