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

import org.e2immu.annotation.ERContainer;

import java.util.Set;

public class DynamicTypeAnnotation_0 {

    //boolean DynamicTypeAnnotation$Invariant() { return set1.size() == 2; }
    public DynamicTypeAnnotation_0() {}
    
    @ERContainer
    private final Set<String> set1 = Set.of("a", "b");

    public void modifySet1() {
        set1.add("b"); // ERROR
    }

    @ERContainer
    public static Set<String> createSet(String a) {
        return Set.of(a);
    }

    public static void modifySetCreated(String a) {
        createSet(a).add("abc"); // ERROR
    }

}
