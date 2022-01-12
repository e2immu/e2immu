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

import java.util.LinkedList;
import java.util.List;

/*
Problem becomes infinite loop when the separator if-statement (combiner:2) is removed.
Otherwise, its a plain error overwriting CNN from 1 to 5 for a.list
 */
public class OutputBuilderSimplified_3 {
    interface OutputElement {
        int size();
    }

    final List<OutputElement> list = new LinkedList<>();

    public OutputBuilderSimplified_3 add(OutputElement outputElement) {
        list.add(outputElement);
        return this;
    }

    public OutputBuilderSimplified_3 add(OutputBuilderSimplified_3 b) {
        list.addAll(b.list);
        return this;
    }

    public static OutputBuilderSimplified_3 combiner(OutputBuilderSimplified_3 a,
                                                     OutputBuilderSimplified_3 b,
                                                     OutputElement separator,
                                                     OutputElement mid) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        if (separator != null)
            a.add(separator);
        return a.add(mid).add(b);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }
}
