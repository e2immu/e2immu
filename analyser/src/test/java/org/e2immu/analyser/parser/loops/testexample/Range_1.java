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

// special cases
public class Range_1 {

    public static void method1() {
        for (int i = 0; i < -10; i++) {
            System.out.println("not reachable");
        }
    }

    public static void method2() {
        for (int i = 11; i < 10; i++) {
            System.out.println("not reachable");
        }
    }

    public static void method3() {
        for (int i = 1; i < 10; i += 20) {
            System.out.println("executed once");
        }
    }

    public static void method4() {
        for (int i = 1; i < 10; i += 0) {
            System.out.println("infinite loop");
        }
    }

    public static void method5() {
        for (int i = 1; i < 10; i -= 1) {
            System.out.println("infinite loop");
        }
    }

    public static void method6() {
        for (int i = 10; i >= 0; i = i + 1) {
            System.out.println("infinite loop");
        }
    }
}
