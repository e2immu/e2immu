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

/*
In this example we warn against assigning to a field outside the owning type
 */
public class SubTypes_8 {

    static class StaticSubType {
        @Override
        public String toString() {
            StaticSubType2.staticField = "abc"; // error
            return "hello" + StaticSubType2.staticField;
        }

        public static void add() {
           StaticSubType2.staticField += "a"; // error
        }

        static class SubTypeOfStaticSubType {

            @Override
            public int hashCode() {
                return 3;
            }
        }
    }

    // not enclosed in StaticSubType, so assignment outside of type errors
    static class StaticSubType2 {
        private static String staticField;
    }
}
