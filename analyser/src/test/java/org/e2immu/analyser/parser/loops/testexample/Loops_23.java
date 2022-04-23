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

package org.e2immu.analyser.parser.loops.testexample;

// very similar to Var_2 in code, tests the same issues as Loops_21 and _22.

import java.util.Collection;

public class Loops_23<X> {

    public final Collection<X> xes;

    public Loops_23(Collection<X> xesIn) {
        assert xesIn != null;
        this.xes = xesIn;
    }

    public String method() {
        StringBuilder all = new StringBuilder();
        for (int i : new int[]{1, 2, 3}) {
            all.append(i);
        }
        System.out.println("Intermediate statement, all should have VN Normal again");
        for (String s : new String[]{"abc", "def"}) {
            all.append(s);
        }
        // Collection<E> implements Iterable<E> directly
        for (X x : xes) {
            all.append(x.toString());
        }
        System.out.println("Intermediate statement, all should have VN Normal again");
        return all.toString();
    }
}
