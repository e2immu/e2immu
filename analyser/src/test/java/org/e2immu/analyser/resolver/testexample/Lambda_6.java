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

package org.e2immu.analyser.resolver.testexample;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Lambda_6 {

    interface X {
    }

    interface XS {
        Stream<X> stream();
    }

    record XSImpl(Set<X> xs) implements XS {

        // to make the distinction between this constructor and the main constructor, the result type R (type parameter
        // of the collect() method) has to have a value
        public XSImpl(X x) {
            this(Set.of(x));
        }

        @Override
        public Stream<X> stream() {
            return xs.stream();
        }

        public XS merge(XS other) {
            return new XSImpl(Stream.concat(stream(), other.stream()).collect(Collectors.toUnmodifiableSet()));
        }
    }
}
