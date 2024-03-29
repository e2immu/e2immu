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

package org.e2immu.analyser.parser.conditional.testexample;

import java.util.Map;

/*
identical to IfStatement_4 but with an additional state (See also 'list' method in Test_Own_01_SMapList)
 */
public class IfStatement_5 {

    private final Map<String, Integer> map;

    public IfStatement_5(Map<String, Integer> map) {
        this.map = map;
    }

    // all 3 get methods should return the same in-lined value
    // local variable [ conditional (c, wrapper( ), default) ]

    public int get1(String label1, int defaultValue1) {
        if("3".equals(label1)) throw new UnsupportedOperationException();

        return map.get(label1) == null ? defaultValue1 : map.get(label1);
    }

    public int get2(String label2, int defaultValue2) {
        if("3".equals(label2)) throw new UnsupportedOperationException();

        Integer i2 = map.get(label2);
        if (i2 == null) return defaultValue2;
        return i2;
    }

    public int get3(String label3, int defaultValue3) {
        if("3".equals(label3)) throw new UnsupportedOperationException();

        Integer i3 = map.get(label3);
        return i3 != null ? i3 : defaultValue3;
    }
}
