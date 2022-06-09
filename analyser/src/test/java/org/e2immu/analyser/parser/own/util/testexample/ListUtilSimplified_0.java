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

package org.e2immu.analyser.parser.own.util.testexample;

import java.util.Iterator;
import java.util.List;

/*
Test introduced 20220609 due to delay loop.
The problem is in "joinLists", but any solution must work for "compare" as well.
 */
public class ListUtilSimplified_0 {

    public static <K, L> void joinLists(List<K> list1, List<L> list2) {
        Iterator<L> it2 = list2.iterator();
        for (K t1 : list1) {
            if (!it2.hasNext()) break;
            L t2 = it2.next();
        }
    }

    public static <T extends Comparable<? super T>> int compare(List<T> values1, List<T> values2) {
        Iterator<T> it2 = values2.iterator();
        for (T t1 : values1) {
            if (!it2.hasNext()) return 1;
            T t2 = it2.next();
            int c = t1.compareTo(t2);
            if (c != 0) return c;
        }
        if (it2.hasNext()) return -1;
        return 0;
    }
}
