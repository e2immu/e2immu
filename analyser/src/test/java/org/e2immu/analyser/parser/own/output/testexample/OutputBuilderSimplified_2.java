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

package org.e2immu.analyser.parser.own.output.testexample;

import org.e2immu.annotation.E1Container;

import java.util.LinkedList;
import java.util.List;

/*
infinite loop on IMMUTABLE (but that was not what we're looking for)
 */
@E1Container
public class OutputBuilderSimplified_2 {

    final List<String> list = new LinkedList<>();

    boolean isEmpty() {
        return list.isEmpty();
    }

    OutputBuilderSimplified_2 add(String s) {
        list.add(s);
        return this;
    }

    public static OutputBuilderSimplified_2 go(OutputBuilderSimplified_2 o1, OutputBuilderSimplified_2 o2) {
        if (o1.isEmpty()) return o2;
        if (o2.isEmpty()) return o1;
        return new OutputBuilderSimplified_2().add("abc");
    }
}
