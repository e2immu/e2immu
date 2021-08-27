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

import java.util.Set;

public class Basics_1 {

    public final Set<String> f1;

    public Basics_1(Set<String> p0, Set<String> p1, String p2) {
        Set<String> s1 = p0;
        this.f1 = s1;
    }

    public Set<String> getF1() {
        return f1;
    }

    // this method is here to ensure that Set<String> does not become transparent
    public boolean contains(String s) {
        return f1 != null && f1.contains(s);
    }
}
