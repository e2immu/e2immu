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

package org.e2immu;

import java.util.ArrayList;
import java.util.List;

public class TestHighlighter<E> {
    private final List<String> list = new ArrayList<>();

    private String getMe() {
        List<String> localString = new ArrayList<>();
        String result = "hello";
        boolean var = true;
        List<Boolean> list = new ArrayList<>();
        list.add(true);
        return "jes";
    }

    private String withOneParam(int j, double[] f, String in) {
        return j + f[0] + in;
    }

    <T> T myMethod(T s, E e) {
        return s;
   }

   String read(E e) {
        return e.toString();
   }

   String read2(E e) { return toString(); }
}
