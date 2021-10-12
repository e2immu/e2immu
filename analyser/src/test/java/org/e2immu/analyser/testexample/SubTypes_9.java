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

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.Modified;

public class SubTypes_9 {

    @E1Container
    static class HoldsStringBuilder {

        @Modified // break2
        private final StringBuilder sb = new StringBuilder();

        public HoldsStringBuilder(String s) {
            add(s).add(s);
        }

        // NOT part of construction only!! (break1)
        private HoldsStringBuilder add(String s) {
            sb.append(s);
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    public static String break1(String s) {
        HoldsStringBuilder hsb = new HoldsStringBuilder(s);
        hsb.add("modify!");
        return hsb.toString();
    }

    public static String break2(String s) {
        HoldsStringBuilder hsb = new HoldsStringBuilder(s);
        hsb.sb.append("modify field");
        return hsb.toString();
    }

    // breaks dependence on method return value
    public static StringBuilder break3(String s) {
        HoldsStringBuilder hsb = new HoldsStringBuilder(s);
        hsb.sb.append("modify field");
        return hsb.sb;
    }
}
