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

package org.e2immu.analyser.parser.modification.testexample;

import java.util.HashSet;
import java.util.Set;

/*
test computation of linked variables in evaluation of if-statement
 */
public class Modification_25<T> {
    private final Set<T> ts;

    public Modification_25(T t) {
        ts = new HashSet<>();
        ts.add(t);
    }

    public boolean add(Set<T> as, Set<T> bs, Set<T> cs) {
        boolean r;
        if (ts.addAll(as)) {
            r = ts.addAll(bs);
        } else {
            r = ts.addAll(cs);
        }
        // ts should link to as, bs, cs
        return r;
    }

    public boolean add2(Set<T> as) {
        boolean r;
        if (ts.addAll(as)) {
            r = true;
        } else {
            r = false;
        }
        // ts should be linked to as
        return r;
    }
}
