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

import org.e2immu.annotation.Dependent;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Independent1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/*
second test, by propagation (see JavaUtil, annotated methods)
 */
public class Dependent1_1<T> {

    private final List<T> list;

    public Dependent1_1(Collection<? extends T> collection) {
        list = new ArrayList<>(collection);
    }

    public void add(@Independent1 T t) {
        list.add(t); // trivial propagation
    }

    public void add2(@Independent1 T t) {
        List<T> theList = list;
        T theT = t;

        theList.add(theT); // propagation with a few redundant variables
    }

    @Dependent
    public List<T> getList() {
        return list;
    }

    // implicitly @Independent1
    @Independent(absent = true)
    @Dependent(absent = true)
    public T getFirst() {
        return list.get(0);
    }
}
