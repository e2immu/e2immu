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

import org.e2immu.annotation.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Dependent1_TODO<T> {

    private final Set<T> set ;

    @Independent
    public Dependent1_TODO(Collection<? extends T> collection) {
        set = new HashSet<>(collection);
    }

    @Dependent
    public Dependent1_TODO(Set<T> set) {
       this.set = set;
    }

    public void add(@Dependent1 T t) {
        set.add(t); // trivial propagation
    }

    public void addAll(@Dependent2 Collection<? extends T> ts) {
        this.set.addAll(ts); // trivial propagation
    }

    public void addAll2(@Dependent2 Collection<? extends T> ts) {
        for (T t : ts) add(t); // other type of propagation
    }

    @Dependent
    public Set<T> getSet() {
        return set;
    }

    @Dependent2 // implying @Independent
    @E2Container
    public Set<T> getCopy() {
        return Set.copyOf(set);
    }

    @Dependent1
    public T getSomeT() {
        return set.stream().findFirst().orElse(null);
    }
}
