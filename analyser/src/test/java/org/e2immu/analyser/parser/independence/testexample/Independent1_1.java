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

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
tests the Collections.addAll(...) method
 */
@ImmutableContainer(hc = true)
public class Independent1_1<T> {
    private final Set<T> set;

    /*
    we expect hc = true, as the content
    of 'ts' will be copied into the content of 'set'.
     */
    public Independent1_1(@Independent(hc = true) T... ts) {
        set = new HashSet<>();
        Collections.addAll(set, ts);
    }

    @Independent(hc = true)
    public Set<T> getSet() {
        return new HashSet<>(set);
    }
}
