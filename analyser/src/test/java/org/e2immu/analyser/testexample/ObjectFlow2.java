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

import org.e2immu.analyser.util.SetUtil;

import java.util.HashSet;
import java.util.Set;

public class ObjectFlow2 {

    /*
    flow of of#0 (param s): pass on + access substring
    flow of res: creation, modify, modify again in new flow object + return
    flow of sMinus = result of call + pass on

    flow of "abc"
    flow of set1 = result of call + access
    flow of "def"
    flow of set2 = result of call + access
    flow of set3 = result of call
     */

    static Set<String> of(String s) {
        Set<String> res = new HashSet<>();
        res.add(s);
        String sMinus = s.substring(1);
        res.add(sMinus);
        return res;
    }

    static final Set<String> set1 = of("abc");
    static final Set<String> set2 = of("def");
    static final Set<String> set3 = SetUtil.immutableUnion(set1, set2);

    static boolean useOf() {
        return of("x").contains("x");
    }
}
