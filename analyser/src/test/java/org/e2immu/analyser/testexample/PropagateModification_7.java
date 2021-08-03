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
import org.e2immu.annotation.NotNull;

// causes scope equality problems (expands into Extended.this.name and ClassWithConsumer.this.name)

public class PropagateModification_7 {

    @E2Container
    abstract static class ClassWithConsumer<T> {
        private final String name;

        public ClassWithConsumer(String name) {
            this.name = name;
        }

        // default: @Modified
        abstract String accept(@NotNull T t);

        public String getName() {
            return name;
        }
    }

    static class Extended extends ClassWithConsumer<Integer> {
        public Extended(String name) {
            super(name);
        }

        @Override
        String accept(Integer integer) {
            return getName() + "=" + integer;
        }
    }
}
