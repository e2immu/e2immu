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

import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.E2Container;

import java.util.Set;

public class DynamicTypeAnnotation_0 {

    //boolean DynamicTypeAnnotation$Invariant() { return set1.size() == 2; }
    public DynamicTypeAnnotation_0() {}
    
    @E2Container
    private final Set<String> set1 = Set.of("a", "b");

    public void modifySet1() {
        set1.add("b"); // ERROR
    }

    @E2Container
    public static Set<String> createSet(String a) {
        return ImmutableSet.of(a);
    }

    public static void modifySetCreated(String a) {
        createSet(a).add("abc"); // ERROR
    }

}
