/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.ExtensionClass;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.UtilityClass;

@ExtensionClass(of = String.class)
@E2Container
public class UtilityClassChecks {

    @NotModified
    static void print(@NotModified(absent = true) String toPrint) {
        System.out.println(toPrint);
    }

    @UtilityClass
    @ExtensionClass(of = String.class)
    @E2Container
    static class UtilityClass1 {

        @NotModified
        static void hello(String s) {
            UtilityClassChecks.print(s);
        }

        private UtilityClass1() {
            // nothing here
            throw new UnsupportedOperationException();
        }
    }

    @UtilityClass(absent = true)
    static class NotAUtilityClass {
        static void hello(String s) {
            print(s);
        }
    }

    @UtilityClass(absent = true)
    static class NotAUtilityClass2 {
        static void hello(String s) {
            print(s);
        }

        private NotAUtilityClass2() {
            // nothing here
            print("?");
        }

        static void createInstance() {
            new NotAUtilityClass2();
        }
    }

}
