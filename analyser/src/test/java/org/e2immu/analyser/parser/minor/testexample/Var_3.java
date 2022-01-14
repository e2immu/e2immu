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

package org.e2immu.analyser.parser.minor.testexample;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class Var_3<X> {

    public final List<X> xes;

    public Var_3(Collection<X> xes) {
        var set = new HashSet<X>(xes); // keep the X here
        this.xes = new LinkedList<>(set);
    }

    public String method() {
        var all = new StringBuilder();
        for (var i : new int[]{1, 2, 3}) {
            all.append(i);
        }
        for (var s : new String[]{"abc", "def"}) {
            all.append(s);
        }
        // List<E> implements Collection<E> implements Iterable<E>
        for (var x : xes) {
            all.append(x.toString());
        }
        return all.toString();
    }

}
