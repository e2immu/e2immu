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

package org.e2immu.annotatedapi;

import org.e2immu.annotation.AllowsInterrupt;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class JavaIo {
    final static String PACKAGE_NAME = "java.io";

    @Container
    public static class PrintStream$ {
        @Modified
        @AllowsInterrupt
        public void print(char c) {
        }

        @Modified
        @AllowsInterrupt
        public void print(boolean b) {
        }

        @Modified
        @AllowsInterrupt
        public void print(int i) {
        }

        @Modified
        @AllowsInterrupt
        public void print(float f) {
        }

        @Modified
        @AllowsInterrupt
        public void print(long l) {
        }

        @Modified
        @AllowsInterrupt
        public void print(String s) {
        }

        @Modified
        @AllowsInterrupt
        public void print(@NotModified Object obj) {
        }

        @Modified
        @AllowsInterrupt
        public void println() {
        }

        @Modified
        @AllowsInterrupt
        public void println(char c) {
        }

        @Modified
        @AllowsInterrupt
        public void println(boolean b) {
        }

        @Modified
        @AllowsInterrupt
        public void println(int i) {
        }

        @Modified
        @AllowsInterrupt
        public void println(float f) {
        }

        @Modified
        @AllowsInterrupt
        public void println(long l) {
        }

        @Modified
        @AllowsInterrupt
        public void println(String s) {
        }

        @Modified
        @AllowsInterrupt
        public void println(@NotModified Object obj) {
        }
    }

}
