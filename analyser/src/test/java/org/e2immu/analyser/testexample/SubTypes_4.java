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

/*
In this example we warn against assigning to a field outside of the owning type
 */
public class SubTypes_4 {

    private static String staticField;

    static class StaticSubType {
        @Override
        public String toString() {
            staticField = "abc"; // warning
            return "hello" + staticField;
        }

        public static void add() {
            staticField += "a"; // warning
        }

        static class SubTypeOfStaticSubType {

            @Override
            public int hashCode() {
                return 3;
            }
        }
    }

}
