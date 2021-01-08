/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
