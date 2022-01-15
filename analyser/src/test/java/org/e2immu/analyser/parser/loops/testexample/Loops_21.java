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

package org.e2immu.analyser.parser.loops.testexample;

// double loop, check that VariableNature moves correctly
// semantic nonsense

public class Loops_21 {

    public static String[][] method(int n, int m) {
        String outer = "abc";
        String[][] array = new String[n][m];

        for (int i = 0; i < n - 1; i++) {

            String inner = "xzy";
            for (int j = i; j < m; j++) {
                int outerMod = i % outer.length();
                int innerMod = j % inner.length();
                array[i][j] = outer.charAt(outerMod) + "->" + inner.charAt(innerMod);
            }
        }
        return array;
    }
}
