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


import java.util.Random;

public class VariableScope_1 {

    /*
    convoluted code to ensure that k cannot simply be expressed in terms of a parameter
    we want j to have a "value of its own"
     */
    static int method() {
        int k;
        {
            int j = 0;
            Random r = new Random();
            for (int i = 0; i < 10; i++) {
                j += r.nextInt();
            }
            k = j;
        }
        return k; // should not refer to j!
    }
}