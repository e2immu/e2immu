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

package org.e2immu.analyser.parser.failing.testexample;



import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Dependent1_5<T> {

    private final Set<T> set;

    @Independent(hc = true)
    public Dependent1_5(Collection<? extends T> collection) {
        set = new HashSet<>(collection);
    }

    @Independent(absent = true)
    public Dependent1_5(Set<T> set) {
        this.set = set;
    }

    public void add(T t) {
        set.add(t); // trivial propagation
    }

    public void addAll(@Independent(hc = true) Collection<? extends T> ts) {
        this.set.addAll(ts); // trivial propagation
    }

    public void addAll2(@Independent(hc = true) Collection<? extends T> ts) {
        for (T t : ts) add(t); // other type of propagation
    }

    @Independent(absent = true)
    public Set<T> getSet() {
        return set;
    }

    @ImmutableContainer(hc = true)
    public Set<T> getCopy() {
        return Set.copyOf(set);
    }

    public T getSomeT() {
        return set.stream().findFirst().orElse(null);
    }
}
