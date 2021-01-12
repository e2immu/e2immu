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

import java.util.ArrayList;
import java.util.List;

public class Basics_6 {

    @Variable
    private String field;

    public void test1() {
        String v1 = field;
        System.out.println(v1); // interrupts!
        String v2 = field;
        assert v1.equals(v2); // most likely true; Warn: potential null ptr exception 1
    }

    public void test2() {
        String v1 = field;
        String v2 = field;
        assert v1.equals(v2); // Warn: always true; + potential null ptr exception, 2, 3
    }

    public String test3() {
        String v1 = field;
        String v3 = someMinorMethod(v1); // ; + potential null ptr exception, 4
        String v2 = field;
        assert v1.equals(v2); // always true... nothing "interrupting"; + potential null ptr exception; 5, 6
        return v3;
    }

    public void test4() {
        String v1 = field;
        nonPrivateMethod(); // must interrupt, non-private
        String v2 = field;
        assert v1.equals(v2); // most likely true..."event" in between; + potential null ptr exception 7
    }

    public void test5() {
        String v1 = field.toLowerCase(); // ; + potential null ptr exception 8
        String v2 = field.toLowerCase(); // ; + potential null ptr exception 9
        assert v1.equals(v2); // most likely true... semantics of toLowerCase  -- NO warning anymore for null ptr
    }

    public List<String> test6() {
        String v1 = field;
        List<String> twentySeven = new ArrayList<>(27); // some constructors do not interrupt
        String v2 = field;
        assert v1.equals(v2); // always true, no interruption; ; + potential null ptr exception 10, 11
        return twentySeven;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    private static String someMinorMethod(String s) {
        return s.toUpperCase(); // not interrupting!
    }

    void nonPrivateMethod() {
        // can always be overridden
    }
}
