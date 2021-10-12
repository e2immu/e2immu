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

import java.util.ArrayList;
import java.util.List;

/*
Test the dependency graph in content linking: works across statically assigned variables,
and linked variables.
 */

@E2Container
public class Dependent1_6<T> {

    private final List<T> list;

    public Dependent1_6(List<T> in) {
        list = new ArrayList<>(in);
    }

    @Independent(absent = true)
    public T get1(int index) {
        return list.get(index);
    }

    @Independent(absent = true)
    public T get2(int index) {
        List<T> list1 = list;
        return list1.get(index);
    }

    @Independent(absent = true)
    public T get3(int index) {
        T s = list.get(index);
        T t = s;
        return t;
    }

    @Independent(absent = true)
    public T get4(int index) {
        List<T> list1 = list.subList(0, index + 1);
        return list1.get(index);
    }

    @Independent(absent = true)
    public T get5(int index) {
        List<T> list1 = list.subList(0, index + 1);
        List<T> list2 = list1;
        T s = list1.get(index);
        T t = s;
        return t;
    }

}
