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

import org.e2immu.annotation.*;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Independent1_5 {
    record One<T>(T t) {}

    @E2Container
    static class ImmutableArrayOfTransparentOnes {

        private final One<Integer>[] ones;

        public ImmutableArrayOfTransparentOnes(One<Integer>[] ones, @Independent1 Supplier<One<Integer>> generator) {
            this.ones = ones.clone();
            Arrays.setAll(ones, i -> generator.get());
        }

        public int size() {
            return ones.length;
        }

        @E2Container
        public One<Integer> first() {
            return ones[0];
        }

        @NotModified
        @E2Container
        public One<Integer> get(int index) {
            return ones[index];
        }

        public void visit(@Independent1 Consumer<One<Integer>> consumer) {
            for (One<Integer> one : ones) consumer.accept(one);
        }
    }
}
