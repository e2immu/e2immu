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

package org.e2immu.analyser.testexample;

public class Variables {

    public static int localVariables1() {
        String s1 = "abc";
        return s1.length();
    }

    public static int localVariables2() {
        String s2 = "abc";
        int i2 = s2.length();
        return i2;
    }

    public static int localVariables3() {
        String s3 = "abc";
        int i3 = s3.length();
        s3 = "def";
        int j3 = s3.lastIndexOf('e');
        return i3 + j3;
    }

    public static int parameter4(int i4) {
        return i4;
    }

    public static int parameter5(int i5, int j5) {
        String s5 = "abc";
        int k5 = s5.length();
        return i5 + j5 + k5;
    }

}
