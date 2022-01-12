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

// normal ranges, 2nd method: loop variable already exists
public class Range_3 {

    public static void method1() {
        int i;
        for (i = 0; i < 10; i++) {
            if (i == -1) {
                System.out.println("not reachable 1");
            }
            if (i == 10) {
                System.out.println("not reachable 2");
            }
        }
    }
/*
    public static void method2() {
        int i = 3; // WARNING: useless assignment
        for (i = 0; i < 11; i += 1) {
            System.out.println("!" + i);
        }
    }

    public static void method3() {
        int i;
        for (i = 0; i < 12; ++i) {
            System.out.println("!" + i);
        }
    }

    public static void method4() {
        int i;
        for (i = 13; i >= 0; i--) {
            System.out.println("!" + i);
        }
    }

    public static void method5() {
        int i;
        for (i = 0; i < 14; i = i + 4) {
            System.out.println("!" + i);
        }
    }

    public static void method6() {
        int i;
        for ( i = 11; i >= 1; i -= 2) {
            if (i == 4) {
                System.out.println("Not reachable 3");
            }
        }
    }*/
}
