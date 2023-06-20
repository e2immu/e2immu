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

package org.e2immu.analyser.parser.own.output.testexample;

import org.e2immu.annotation.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Remove countMid and the delay is gone...
// alternatively, remove the (a, b) bi-consumer and replace by 'null'

// the supplier should not be immutable, since it contains a @Modified field
// as a consequence, the BiConsumer should not be immutable either

public class OutputBuilderSimplified_9 {

    @FinalFields
    @Container
    @NotNull
    @NotModified
    public static Supplier<BiConsumer<OutputBuilderSimplified_9, OutputBuilderSimplified_9>> joining() {
        return new Supplier<>() {

            @Modified
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public BiConsumer<OutputBuilderSimplified_9, OutputBuilderSimplified_9> get() {
                return (a, b) -> {
                    countMid.incrementAndGet();
                };
            }
        };
    }
}
