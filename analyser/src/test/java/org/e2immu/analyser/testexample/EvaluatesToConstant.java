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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.NotNull;


/*
 ERROR in M:method2:1: Condition in 'if' or 'switch' statement evaluates to constant
 ERROR in M:method2:1.0.0: Unreachable statement

 ERROR in M:method3:1.0.1.0.0: Unreachable statement
 ERROR in M:method3:1.0.1: Condition in 'if' or 'switch' statement evaluates to constant
 ERROR in M:method3:2: Unused local variable: b

 The errors involving b in method3 are not really wrong
 */
@ERContainer
public class EvaluatesToConstant {

    @NotNull
    private static String someMethod(String a) {
        return a == null ? "x" : a;
    }

    @Constant("c")
    private static String method2(String param) {
        String b = someMethod(param);
        if (b == null) {
            return "a";
        }
        return "c";
    }

    @Constant("c")
    private static String method3(String param) {
        String b = someMethod(param);
        if (param.contains("a")) {
            String a = someMethod("xzy");
            if (a == null) {
                return b + "c";
            }
        }
        return "c";
    }
}
