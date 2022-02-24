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

package org.e2immu.analyser.parser.loops.testexample;

import java.util.Set;

/*
Problem to be solved: "found" in merge in 1.0.0.0.0 has the E as existing vi,
but its value is still the one from outside.

variableValue in SAEvaluationContext works fine when evaluating "found", but
because it does not change the actual value used in the merge, there still are issues.
in evaluation of VariableExpression we try to actually change this value.
 */
public class VariableInLoop_0 {

    public static void method(Set<String> set) {
        boolean found = false;
        for (String s : set) {
            if (s.contains("x")) {
                if (!found) {
                    found = true;
                    System.out.println("Have x");
                }
            }
        }
    }
}
