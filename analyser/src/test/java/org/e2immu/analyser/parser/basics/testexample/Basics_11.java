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

package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.NotNull;

/*
tests static assignments and context not null

No annotated APIs have been loaded. Println will be modifying out.
toLowerCase cannot modify s2, because String is hard-wired to be @E2Container
 */
public class Basics_11 {

    public static void test(@NotNull String in) {
        String s1 = in;
        String s2 = in;
        System.out.println(s1); // does not cause context not null
        System.out.println(s2.toLowerCase()); // does cause context not null
        assert s1 != null; // should always be true
    }

}
