/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser.basics.testexample;

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
        String v3 = someMinorMethod(v1); // ; + potential null ptr exception, 4, 5 IMPROVE currently on v1 and field!!!
        String v2 = field;
        assert v1.equals(v2); // always true... nothing "interrupting"; 6
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
        String v2 = field.toLowerCase(); // toLowerCase does not allow interrupts, see TestCommonJavaLang
        assert v1.equals(v2); // always true (different from IntelliJ!) 9
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
        // can always be overridden, but as of 20200305, we study what we see (code in EvaluateMethodCall)
    }
}
