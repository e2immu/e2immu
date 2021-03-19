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

import org.e2immu.annotation.NotModified;

import java.util.Set;

/*
Test is here to catch certain delays in computing modifications.

 */
public class Basics_9 {

    @NotModified
    public static boolean isFact(boolean b) {
        throw new UnsupportedOperationException();
    }

    @NotModified
    public static boolean isKnown(boolean test) {
        throw new UnsupportedOperationException();
    }

    @NotModified
    static boolean setContainsValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? containsE : !isKnown(true) && size > 0 && retVal;
    }

    @NotModified
    public static boolean test1(Set<Integer> set) {
        return setContainsValueHelper(1, set.contains(3), set.isEmpty());
    }
}
