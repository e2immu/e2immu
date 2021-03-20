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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.function.Function;

/*
Situation: consumer applied to other parameter. we assume the worst: the abstract method is modifying.
In this way, we indicate that the abstract method is applied to the other parameter, rather than a field
or a variable derived from it.

Important: because we have only one means of "linking" the first parameter to the second, applyToString
cannot be analysed correctly: its first parameter remains modifying.
Given our predilection for containers, we don't think this is a too serious a problem.
 */

public class AbstractTypeAsParameter_1 {

    @Container
    static class Y {
        public final String string;
        private int i;

        public Y(@NotNull String string) {
            this.string = string;
        }

        public int getI() {
            return i;
        }

        public int increment() {
            return ++i;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Y y = (Y) o;
            return string.equals(y.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(string);
        }
    }

    @NotModified
    public static int applyToParameter(@Modified Y y, @NotModified Function<Y, Integer> f) {
        return f.apply(y);
    }

    @NotModified
    public static int applyIncrement(@Modified Y y) {
        return applyToParameter(y, Y::increment);
    }

    @NotModified
    public static int applyToString(@Modified Y y) {
        return applyToParameter(y, Y::getI);
    }
}
