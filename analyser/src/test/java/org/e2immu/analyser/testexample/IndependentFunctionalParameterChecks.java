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

import org.e2immu.annotation.E1Container;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Goal is to verify independence rules for functional parameters
 */
public class IndependentFunctionalParameterChecks {

    @E1Container
    static class Example1<T> {

        private final Set<T> data;

        public Example1(Set<T> ts) {
            this.data = new HashSet<>(ts);
        }

        public T getFirst() {
            return stream().findFirst().orElseThrow();
        }

        public Stream<T> stream() {
            return data.stream();
        }

        public void unsafeVisit(Consumer<Set<T>> consumer) {
            consumer.accept(data);
        }

        public void safeVisit(Consumer<T> consumer) {
            data.forEach(consumer);
        }
    }
}
