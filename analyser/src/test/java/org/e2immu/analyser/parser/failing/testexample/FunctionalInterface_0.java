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
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Modified;

import java.util.function.Supplier;

@FinalFields
@Container
public class FunctionalInterface_0 {

    @Container
    static class Counter {
        private int counter;

        @Modified
        public int increment() {
            return ++counter;
        }
    }

    @Modified // getAndIncrement calls the increment method (even if  we have no idea whether the supplier gets called)
    private final Counter myCounter = new Counter();

    @Modified // with declaration
    private final Supplier<Integer> explicitGetAndIncrement = new Supplier<Integer>() {
        @Override
        @Modified // 1
        public Integer get() {
            return myCounter.increment();
        }
    };

    @Modified // calling a modifying method
    public int myExplicitIncrementer() {
        return explicitGetAndIncrement.get();
    }

}
