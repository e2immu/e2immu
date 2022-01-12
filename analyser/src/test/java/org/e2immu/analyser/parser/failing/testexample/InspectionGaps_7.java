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


public class InspectionGaps_7 {

    interface Interface1 {
        String makeAString();
    }

    interface Interface2 {
        String makeAString();
    }

    interface Interface12 extends Interface1, Interface2 {
        String makeAString();
    }

    interface Interface12Without extends Interface1, Interface2 {
    }

    static class Class1 implements Interface1, Interface2 {

        @Override
        public String makeAString() {
            return "x";
        }
    }

    static String method1(Interface12 i) {
        return i.makeAString();
    }

    static String method2(Class1 i) {
        return i.makeAString();
    }

    static String method3(Interface12Without i) {
        return i.makeAString();
    }
}
