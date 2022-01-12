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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/*
Arrays.ArrayList competes with ArrayList. It is clear that the latter should win!

is @E1Immutable without Annotated API, @E1Container with Annotated API
 */
public class InspectionGaps_11 {
    private final String s1;
    private final String s2;
    private final List<String> list = new ArrayList<String>();

    public InspectionGaps_11(String s1, List<String> list, String s2) {
        this.s1 = s1;
        this.s2 = s2;
        this.list.addAll(list);
    }

    public Stream<String> getStream() {
        return Arrays.stream(new String[]{s1, s2});
    }

    public static InspectionGaps_11 of(String s1, List<String> list, String s2) {
        return new InspectionGaps_11(s1, createUnmodifiable(list), s2);
    }

    public static InspectionGaps_11 ofWithNull(String s1, String s2) {
        return new InspectionGaps_11(s1, null, s2); // raises error
    }

    private static <T> List<T> createUnmodifiable(List<? extends T> list) {
        return new ArrayList<>(list);
    }

    public List<String> getList() {
        return list;
    }

    public String getS1() {
        return s1;
    }

    public String getS2() {
        return s2;
    }
}
