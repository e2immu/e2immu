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

import java.util.*;

public class InspectionGaps_2 {
    private static final Map<String, Integer> PRIORITY = new HashMap<>();

    static {
        PRIORITY.put("e2container", 1);
        PRIORITY.put("e2immutable", 2);
    }

    static {
        PRIORITY.put("e1container", 3);
        PRIORITY.put("e1immutable", 4);
    }

    private static int priority(String in) {
        return PRIORITY.getOrDefault(in.substring(0, in.indexOf('-')), 10);
    }

    private static String highestPriority(String[] annotations) {
        List<String> toSort = new ArrayList<>(Arrays.asList(annotations));
        toSort.sort(Comparator.comparing(InspectionGaps_2::priority));
        return toSort.get(0);
    }
}
