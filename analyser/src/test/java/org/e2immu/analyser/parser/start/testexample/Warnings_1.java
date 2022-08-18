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

package org.e2immu.analyser.parser.start.testexample;

/*

 Check for unused local variables

 ERROR in M:UnusedLocalVariableChecks:0: Unused local variable: a
 ERROR in M:UnusedLocalVariableChecks:0: Useless assignment: a
 ERROR in M:method1: Method should be marked static
 WARN in M:method1:2: Ignoring result of method call: java.lang.String.trim()
 ERROR in M:method1:1: Condition in 'if' or 'switch' statement evaluates to constant TODO Wrong
 ERROR in M:method1:2: Unused local variable: s
 ERROR in M:checkArray2:2: Useless assignment: integers[i]
 ERROR in M:checkArray2:2: Unused local variable: integers[i]       (this is debatable, but not wrong, because a/ var? b/ already error)
 ERROR in M:checkForEach:1.0.0: Unused local variable: loopVar
 */

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.type.ExtensionClass;

@ImmutableContainer
@ExtensionClass(of=boolean.class)
public class Warnings_1 {

    public Warnings_1() {
        int a = 0;
        // ERROR: private variable a is not used, useless assignment
    }

    // ERROR: method1 should be marked static
    private void method1(String t) {
        String s;
        // ERROR: local variable s is not used
        if (t.length() < 19) {
            return;
        }
        // WARNING: ignoring result of method call
        t.trim();
    }

    @ImmutableContainer("1")
    private static int checkArray() {
        int[] integers = {1, 2, 3};
        int i = 0;
        return integers[i];
    }

    private static void checkArray2() {
        int[] integers = {1, 2, 3};
        int i = 0;
        integers[i] = 3;
        // ERROR: assignment is not used
    }

    private static void checkForEach() {
        int[] integers = {1, 2, 3};
        for (int loopVar : integers) {
            // WARN: loopVar is not used
            System.out.println("hello!");
        }
    }

    public static int method5(boolean x) {
        int a;
        if(x) {
            // ERROR: overwriting previous assignment
            a = 5;
        }
        a = 6;
        return a;
    }
}
