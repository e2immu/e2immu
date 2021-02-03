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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class CyclicReferences_4 {
    private static int count = 0;

    @Modified
    public static boolean methodC(String paramC) {
        if ("b".equals(paramC)) return methodD(paramC);
        return "a".equals(paramC);
    }

    @Modified
    public static boolean methodD(String paramD) {
        if ("a".equals(paramD)) return methodE(paramD);
        return "b".equals(paramD);
    }

    @Modified
    public static boolean methodE(String paramE) {
        if ("b".equals(paramE)) return methodF(paramE);
        return "a".equals(paramE);
    }

    @Modified
    public static boolean methodF(String paramF) {
        if ("a".equals(paramF)) return methodC(paramF);
        count++;
        return "b".equals(paramF);
    }

    @NotModified
    public static int getCount() {
        return count;
    }
}
