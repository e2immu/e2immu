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

import java.util.stream.Stream;

public class Warnings_5 {

    private static class ParentClass {
        String s;

        public ParentClass(String s) {
            this.s = s;
        }
    }

    static class ChildClass extends ParentClass {

        private final String t;

        public ChildClass(String s, String t) {
            super(s);
            this.t = t;
        }

        public String methodMustNotBeStatic(String input) {
            return s + "something" + input;
        }

        // warning! unused input
        public boolean methodMustNotBeStatic2(String input) {
            return (s instanceof String);
        }

        public static String methodMustBeStatic(String input) {
            return "something" + input;
        }

        // warning! unused input
        public ChildClass methodMustNotBeStatic3(String input) {
            return this;
        }

        public String methodMustNotBeStatic4(String input) {
            return Stream.of(input).map(s -> {
                if(s == null) return "null";
                return s + "something" + t;
            }).findAny().get();
        }

        public ChildClass methodMustNotBeStatic5(String input) {
            return methodMustNotBeStatic3(input);
        }

    }
}
