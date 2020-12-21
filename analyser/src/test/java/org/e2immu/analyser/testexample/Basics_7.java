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

/*
Statement time stands still in constructors and synchronization blocks,
where fields are effectively treated as local variables.
 */
public class Basics_7 {

    private int i;

    Basics_7(int p, boolean b) {
        if (b) {
            i = p;
            System.out.println("i is " + i);
        }
        if (i < 10) {
            i = i + 1;
        }
    }

    @Constant("true")
    synchronized boolean increment(int q) {
        int k = i;
        System.out.println("q is " + q);
        i += q;
        System.out.println("i is " + i);
        return i == k + q;
    }

    void increment2() {
        System.out.println("i is " + i);
        synchronized (this) {
            int j = i;
            i++;
            System.out.println("i is " + i);
            assert j == i - 1;
        }
    }
}
