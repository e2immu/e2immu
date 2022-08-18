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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Independent1_6 {
    @ImmutableContainer(hc = true)
    record One<T>(T t) {
    }

    @ImmutableContainer(hc = true)
    static class ImmutableArrayOfOnes<T> {

        private final One<T>[] ones;

        @SuppressWarnings("unchecked")
        public ImmutableArrayOfOnes(int size, @Independent(hc = true) Supplier<One<T>> generator) {
            ones = new One[size]; // makes One explicit
            Arrays.setAll(ones, i -> generator.get());
        }

        public int size() {
            return ones.length;
        }

        @NotModified
        public One<T> get(int index) {
            return ones[index];
        }

        public void visit(@Independent(hc = true) Consumer<One<T>> consumer) {
            for (One<T> one : ones) consumer.accept(one);
        }
    }
}
