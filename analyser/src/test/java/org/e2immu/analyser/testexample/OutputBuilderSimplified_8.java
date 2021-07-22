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


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

// bug in @Identity on joining()

public class OutputBuilderSimplified_8 {
    interface OutputElement {
    }

    public static Collector<OutputBuilderSimplified_8, OutputBuilderSimplified_8, OutputBuilderSimplified_8> joining() {
        return joining();
    }

    public static Collector<OutputBuilderSimplified_8, OutputBuilderSimplified_8, OutputBuilderSimplified_8> joining(OutputElement separator) {
        return new Collector<>() {

            @Override
            public Supplier<OutputBuilderSimplified_8> supplier() {
                return null;
            }

            @Override
            public BiConsumer<OutputBuilderSimplified_8, OutputBuilderSimplified_8> accumulator() {
                return null;
            }

            @Override
            public BinaryOperator<OutputBuilderSimplified_8> combiner() {
                return null;
            }

            @Override
            public Function<OutputBuilderSimplified_8, OutputBuilderSimplified_8> finisher() {
              return null;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return null;
            }
        };
    }

}
