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

import org.e2immu.annotation.Constant;

public class Basics_8 {

    // the first test isn't that interesting, in that v always gets substituted for a value

    @Constant("true")
    static boolean test1(int l) {
        int v = l;
        int w = v + 1; // == l+1
        assert w == l + 1; // always true
        v = v + 1; // == l+1
        int u = v + 2; // == l+3
        assert u == l + 3; // always true
        v = v + 1; // == l+2
        return v == u - 1;
    }

    private int i;

    // statement time doesn't advance because of the synchronization

    @Constant("true")
    synchronized boolean test2(int q) {
        int j = i; // some value
        int i2 = i + q; // some value + q
        int k = i2;
        return k == j + q;
    }

    @Constant("true")
    synchronized boolean test3(int q) {
        int j = i; // some value
        i = i + q; // some value + q
        int k = i;
        return k == j + q;
    }

    // more complicated, to verify that the correct statement and statement times are used
    void test4(int q) {
        System.out.println("i is " + i); // time 1
        int j = i;
        System.out.println("i is again " + i); // time 2
        int k = i;
        if (j == k) {
            synchronized (this) {
                int j0 = i; // some value
                i = i + q; // some value + q
                int k0 = i;
                assert k0 == j0 + q; // always true
            }
        }
    }
}
