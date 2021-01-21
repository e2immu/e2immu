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

import java.util.List;
import java.util.stream.Collectors;

public class TypeParameters_1<T> {
    private List<C2> strings2;

    static class C2<T> {
        final String s2;

        C2(String s2p) {
            this.s2 = s2p;
        }

        C2(T t2p) {
            s2 = t2p.toString();
        }
    }

    public TypeParameters_1(List<T> input2) {
        strings2 = input2.stream().map(C2::new).collect(Collectors.toList());
    }

    public List<C2> getStrings2() {
        return strings2;
    }
}
