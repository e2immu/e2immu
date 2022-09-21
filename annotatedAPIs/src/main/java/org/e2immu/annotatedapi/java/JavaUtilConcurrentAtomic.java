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

package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class JavaUtilConcurrentAtomic {
    final static String PACKAGE_NAME = "java.util.concurrent.atomic";

    @Container
    @Independent
    interface AtomicInteger$ {

        @Modified
        int getAndIncrement();

        @Modified
        int incrementAndGet();
    }

    @Container
    @Independent
    interface AtomicBoolean$ {

        @NotModified
        boolean get();

        @Modified
        void set(boolean newValue);
    }

    @Container
    @Independent
    interface AtomicLong$ {

        @NotModified
        boolean get();

        @Modified
        void set(long newValue);
    }

    @Container
    @Independent
    interface AtomicReference$<V> {

        @NotModified
        V get();

        @Modified
        void set(V newValue);
    }
}
