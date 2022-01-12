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

package org.e2immu.analyser.parser.start.testexample;

public class Warnings_10 {
    public static int method(int i) {
        int j = i;
        j = j; // useless self-assignment
        return j;
    }

    public static int method2(int i) {
        int j = 0; // no error here
        if(i == 3) {
            j = i;
        }
        return j;
    }

    public static int method3(int i) {
        int j = 0; // error here
        if(i == 3) {
            j = i;
        } else {
            j = -i;
            System.out.println(j);
        }
        return j;
    }

    public static int method4(int i) {
        int j = 0; // no error here
        if(i == 3) {
            System.out.println(j);
            j = i;
        } else {
            j = -i;
        }
        return j;
    }

    public static int method5(int i) {
        int j = 0; // no error here neither, but more tricky
        if(i == 3) {
            System.out.println(j);
            j = i;
            System.out.println(j);
        } else {
            j = -i;
        }
        return j;
    }
}
