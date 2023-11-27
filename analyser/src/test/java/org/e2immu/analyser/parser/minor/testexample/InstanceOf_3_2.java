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

import org.e2immu.annotation.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// variant on _3, mod is now on list
@FinalFields
public class InstanceOf_3_2 {

    @NotModified
    private final Set<String> base = new HashSet<>();

    @Nullable
    @NotModified
    public String add(@Nullable Collection<String> collection) {
        // because we execute without A API, addAll's parameter is @Nullable @Modified; the method itself is @NotModified
        if (collection instanceof List<String> list) {
            base.addAll(list);
            return list.isEmpty() ? "Empty" : list.get(0);
        }
        return null;
    }

    @NotModified
    public Stream<String> getBase() {
        return base.stream();
    }
}
