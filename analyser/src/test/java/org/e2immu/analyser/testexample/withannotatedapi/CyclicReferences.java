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

package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Final;
import org.e2immu.annotation.NotModified;

import java.util.List;

public class CyclicReferences {

    @Final
    private String field1;
    @Final
    private String field2;

    public CyclicReferences() {
        this("abc");
    }
    public CyclicReferences(String field1) {
        this(field1, "cde");
    }
    public CyclicReferences(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    public String getField1() {
        return field1;
    }

    public String getField2() {
        return field2;
    }

    @NotModified
    public static boolean findTailRecursion(String find, List<String> list) {
        if (list.isEmpty()) return false;
        if (list.get(0).equals(find)) return true;
        return findTailRecursion(find, list.subList(1, list.size()));
    }

    public static boolean methodA(String paramA) {
        if ("b".equals(paramA)) return methodB(paramA);
        return "a".equals(paramA);
    }

    public static boolean methodB(String paramB) {
        if ("a".equals(paramB)) return methodA(paramB);
        return "b".equals(paramB);
    }

    public static boolean methodC(String paramC) {
        if ("b".equals(paramC)) return methodD(paramC);
        return "a".equals(paramC);
    }

    public static boolean methodD(String paramD) {
        if ("a".equals(paramD)) return methodE(paramD);
        return "b".equals(paramD);
    }
    public static boolean methodE(String paramE) {
        if ("b".equals(paramE)) return methodF(paramE);
        return "a".equals(paramE);
    }

    public static boolean methodF(String paramF) {
        if ("a".equals(paramF)) return methodC(paramF);
        return "b".equals(paramF);
    }
}
