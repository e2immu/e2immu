/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
