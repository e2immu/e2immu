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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Remove countMid and the delay is gone...
// alternatively, remove the (a, b) bi-consumer and replace by 'null'

// variant of 9, without modifying countMid

public class OutputBuilderSimplified_11 {

    public static Supplier<BiConsumer<OutputBuilderSimplified_11, OutputBuilderSimplified_11>> joining() {
        return new Supplier<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public BiConsumer<OutputBuilderSimplified_11, OutputBuilderSimplified_11> get() {
                return (a, b) -> {
                    countMid.get();
                };
            }
        };
    }
}
