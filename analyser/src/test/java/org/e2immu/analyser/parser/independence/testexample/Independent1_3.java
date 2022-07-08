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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Independent1;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/*
tests the Arrays.setAll(...) method, now with a method reference
 */
@E2Container
public class Independent1_3<T> {
    private final T[] ts;

    /*
    we expect @Independent1 rather than @Independent, as the content
    of 'ts' will be linked copied into the content of 'set'.
     */
    @SuppressWarnings("unchecked")
    public Independent1_3( @Independent1 List<T> content) {
        this.ts = (T[]) new Object[content.size()];
        Arrays.setAll(this.ts, content::get);
    }

    public Set<T> getSet() {
        Set<T> set = new HashSet<>();
        Collections.addAll(set, this.ts);
        return set;
    }
}
