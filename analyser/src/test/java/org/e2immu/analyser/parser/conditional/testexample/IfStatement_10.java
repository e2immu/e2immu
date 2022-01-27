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

public class IfStatement_10 {

    public static boolean method(boolean a, boolean b, boolean c, boolean d) {
        if (a || b || c || d) {
            boolean added = false;
            if (a) {
                System.out.println("0 is true");
                added = true;
            }
            if (b) {
                if (added) {
                    System.out.println("1 is true, 0 was too");
                }
                added = true;
                System.out.println("added");
            }
            if (c) {
                if (added) {
                    System.out.println("2, 0, 1 is true");
                }
                added = true;
                System.out.println("added");
            }
            if (d) {
                if (added) System.out.println("0 is true");
            }
            System.out.println("added");
        }
        return true;
    }
}
