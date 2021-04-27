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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.Nullable;

public class SubTypes_2 {

    private String field;

    // ERROR: toString is @Nullable, which is worse than what we demand in Object.toString()
    class NonStaticSubType {
        @Nullable
        @Override
        public String toString() {
            return field;
        }
    }


    // ERROR: toString is @Modified, which is worse than what we demand in Object.toString()
    // Note: no error assigning to field outside type, as it goes "up"
    class NonStaticSubType2 {
        @Modified
        @Override
        public String toString() {
            field = "x";
            return "abc";
        }
    }

    class NonStaticSubType3 {
        @Override
        public String toString() {
            return "ok" + field;
        }
    }
}
