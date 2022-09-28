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

import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/*
More consumer linking
 */
@FinalFields
public class Independent1_11<T> {

    private final List<T> list = new ArrayList<>();

    @Modified
    public void addAllLambda(@Independent(hc = true) Independent1_11<T> other) {
        other.list.stream().forEach(e -> list.add(e));
    }

    @NotModified
    public void addAllLambda2(@Independent Independent1_11<T> other) {
        other.list.stream().forEach(e -> System.out.println(e+" - "+list.size()));
    }

    @Modified
    public void addAllMR(@Independent(hc = true) Independent1_11<T> other) {
        other.list.stream().forEach(this.list::add);
    }

    @Modified
    public void addAllMR2(@Independent(hc = true) Independent1_11<T> other) {
        other.list.stream().forEach(this::add);
    }

    @NotModified
    public void addAllMR3(@Independent(hc = true) @Modified Independent1_11<T> other) {
        list.stream().forEach(other::add);
    }

    @Modified
    public void addAllCC(@Independent(hc = true) Independent1_11<T> other) {
        other.list.stream().forEach(new Consumer<T>() {
            @Override
            public void accept(T t) {
                list.add(t);
            }
        });
    }

    private void add(T t) {
        list.add(t);
    }
}
