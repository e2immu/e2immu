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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/*
 variant on MethodReference_3, to test E2Immutable properties of Stream result.
 here, Map.Entry is not transparent!

 See also test 12, 13
 */

@ERContainer
public class E2Immutable_11 {

    @NotModified
    private final TreeMap<String, Integer> map = new TreeMap<>();

    public E2Immutable_11(int i) {
        map.put("" + i, i);
    }


    @ERContainer
    @Nullable
    public Map.Entry<String, Integer> firstEntry() {
        return map.firstEntry();
    }

    public Integer get() {
        return map.firstEntry().getValue();
    }

    @NotNull
    @NotModified
    @ERContainer
    // The firstEntry result is @E2Container; given the dynamic type String, Integer, it is @ERContainer
    // E2Container<ERContainer> = ERContainer
    public Stream<Map.Entry<String, Integer>> stream() {
        return Stream.of(map.firstEntry());
    }
}
