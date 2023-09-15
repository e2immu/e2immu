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
public class FactoryMethod_0<T> {

    private final List<T> list = new ArrayList<>();
/*
    @Independent // because the return value does not link to t
    public static <T> FactoryMethod_0<T> of1(@Independent(hc = true) T t) {
        return new FactoryMethod_0<T>().add(t);
    }
*/
    @Independent // because the return value does not link to t, tt
    public static <T> FactoryMethod_0<T> of2(@Independent(hc = true) T t,
                                             @Independent(hc = true) T tt) {
        FactoryMethod_0<T> f = new FactoryMethod_0<>();
        f.add(t);
        f.add(tt);
        return f;
    }
/*
    @Independent(hc = true) // ts links to ts, common hidden content
    public static <T> FactoryMethod_0<T> of(@Independent(hc = true) T[] ts) {
        FactoryMethod_0<T> f = new FactoryMethod_0<>();
        for (T t : ts) {
            f.add(t);
        }
        return f;
    }*/

    @Fluent
    @Modified(construction = true)
    private FactoryMethod_0<T> add(T t) {
        list.add(t);
        return this;
    }
/*
    @Independent(hc = true)
    public T get(int index) {
        return list.get(index);
    }

    @Independent(hc = true)
    public Stream<T> getStream() {
        return list.stream();
    }

    @Independent(hc = true)
    public List<T> copy() {
        return list.stream().toList();
    }

    @Independent(hc = true)
    public List<T> copy2() {
        List<T> result = new ArrayList<>(list.size());
        this.list.stream().forEach(e -> result.add(e));
        return result;
    }*/
}
