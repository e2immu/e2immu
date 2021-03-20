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

import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

/*
so while T is implicitly immutable, this promise is broken by the use
of a cast in the incrementedT method
 */
@E1Immutable
public class Cast_1<T> {

    static class Counter {
        private int i = 0;

        public int increment() {
            return ++i;
        }
    }

    @Modified
    private final T t;

    public Cast_1(@Modified T input) {
        t = input;
    }

    @NotModified
    public T getT() {
        return t;
    }

    @NotModified
    public String getTAsString() {
        return (String) t;
    }

    @Modified
    public int incrementedT() {
        return ((Counter) t).increment();
    }
}
