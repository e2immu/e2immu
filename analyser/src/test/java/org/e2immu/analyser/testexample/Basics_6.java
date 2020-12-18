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

import org.e2immu.annotation.Variable;

public class Basics_6 {

    @Variable
    private String field;

    public void test1() {
        String v1 = field;
        System.out.println(v1);
        String v2 = field;
        assert v1.equals(v2); // most likely true
    }

    public void test2() {
        String v1 = field;
        String v2 = field;
        assert v1.equals(v2); // always true
    }

    public String test3() {
        String v1 = field;
        String v3 = someMinorMethod(v1);
        String v2 = field;
        assert v1.equals(v2); // always true... nothing "interrupting"
        return v3;
    }

    public void test4() {
        String v1 = field;
        nonPrivateMethod();
        String v2 = field;
        assert v1.equals(v2); // most likely true..."event" in between
    }

    public void test5() {
        String v1 = field.toLowerCase();
        String v2 = field.toLowerCase();
        assert v1.equals(v2); // most likely true... semantics of toLowerCase
    }

    public void test6() {
        String v1 = field;
        Basics_6 basics_6 = new Basics_6();
        String v2 = field;
        assert v1.equals(v2); // new object creation does not interrupt
        assert basics_6.field.equals(v1);
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    private static String someMinorMethod(String s) {
        return s.toUpperCase();
    }

    void nonPrivateMethod() {
        // can always be overridden
    }
}
