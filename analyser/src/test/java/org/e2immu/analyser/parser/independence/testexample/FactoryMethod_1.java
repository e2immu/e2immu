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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ImmutableContainer(hc = true)
public class FactoryMethod_1<T> {

    private final List<T> list = new ArrayList<>();

    @Independent // because the return value does not link to t
    public static <T> FactoryMethod_1<T> of(@Independent(hc = true) T t) {
        FactoryMethod_1<T> f = new FactoryMethod_1<>();
        f.add(t);
        return f;
    }

    // only used in the construction phase
    @Fluent
    @Modified(construction = true)
    private FactoryMethod_1<T> add(T t) {
        list.add(t);
        return this;
    }

    public Stream<T> list() { return list.stream(); }
}
