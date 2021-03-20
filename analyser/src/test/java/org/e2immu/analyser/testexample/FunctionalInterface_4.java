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
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@E2Container
public class FunctionalInterface_4<T> {

    @NotModified
    private final Set<T> ts;

    @Independent
    public FunctionalInterface_4(@NotNull @NotModified Set<T> ts) {
        this.ts = new HashSet<>(ts);
    }

    @NotModified
    public void visit(Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

    @NotModified
    public void visit2(@NotNull Consumer<T> consumer) {
        ts.forEach(consumer);
    }

    @NotModified
    public void visit3(@NotNull Consumer<T> consumer) {
        doTheVisiting(consumer, ts);
    }

    @NotModified
    private static <T> void doTheVisiting(@NotNull Consumer<T> consumer, @NotNull @NotModified Set<T> set) {
        set.forEach(consumer);
    }
}
