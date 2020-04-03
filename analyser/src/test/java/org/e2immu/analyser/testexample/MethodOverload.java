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

public class MethodOverload {

    @Override
    public int hashCode() {
        return 10;
    }

    interface I1 {
        String method(int i);

        String method(int i, int j);

        String method(int i, String k);
    }

    static class C1 implements I1 {

        @Override
        public String method(int i) {
            return "i=" + i;
        }

        @Override
        public String method(int i, int j) {
            return "i+j=" + (i + j);
        }

        @Override
        public String method(int i, String k) {
            return k + "=" + i;
        }

        @Override
        public String toString() {
            return method(1) + method(1, 2) + method(1, "h");
        }
    }

    static class C2 extends C1 {
        @Override
        public String toString() {
            return "C2 goes to C1:" + super.toString();
        }

        @Override
        public String method(int i, int j) {
            return "C2 sum=" + (i + j);
        }
    }
}
