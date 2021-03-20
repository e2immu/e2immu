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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/*
 object flows:

 new HashSet (origin: ObjectCreation), assigned to 'set'

 set.add() adds modifying access to the field's object flow... HOW??

 set.stream() adds method access to the set of the field
 */
public class ObjectFlow4 {

    private final Set<String> set = new HashSet<>();

    public void add(String s) {
        set.add(s);
    }

    public Stream<String> stream() {
        return set.stream();
    }
}
