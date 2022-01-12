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
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.function.Function;

/*
Situation: consumer applied to other parameter.

It is important to realize that Y is not a transparent type in FunctionalInterface_7.
Neither does FunctionalInterface_7 have fields.
So there is no @Dependent1; and applyToParameter cannot propagate modifications.
It must make parameter Y @Modified.

As an unfortunate consequence, applyGetI incorrectly claims that it modifies its parameter.
 */

public class FunctionalInterface_7 {

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

    // because there are no fields!
    @NotModified
    public static int applyToParameter(@Modified Y y, @Modified Function<Y, Integer> f) {
        return f.apply(y);
    }

    @NotModified
    public static int applyIncrement(@Modified Y y) {
        return applyToParameter(y, Y::increment);
    }

    @NotModified
    public static int applyGetI(@Modified Y y) {
        return applyToParameter(y, Y::getI);
    }
}
