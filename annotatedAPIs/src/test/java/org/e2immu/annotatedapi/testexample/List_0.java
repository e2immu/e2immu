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

package org.e2immu.annotatedapi.testexample;

import org.e2immu.annotation.Constant;

import java.util.*;

/*
test example which renders no errors/warnings IF used correctly with AnnotatedAPIs converted into AnnotatedXML
 */
public class List_0 {

    @Constant("4")
    static int test() {
        List<String> list = new ArrayList<>();
        if (list.size() > 0) { // evaluates to constant
            return 3; // never reached!
        }
        return list.size() + 4;
    }
}
