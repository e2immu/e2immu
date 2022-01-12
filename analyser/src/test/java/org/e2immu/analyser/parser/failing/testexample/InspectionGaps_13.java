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

package org.e2immu.analyser.parser.failing.testexample;

// IMPORTANT: ensure that there is a * here; and that the static keyword is present!

import static org.e2immu.analyser.parser.failing.testexample.a.TypeWithStaticSubType.*;

public class InspectionGaps_13 {

    public static int method1() {
        SubType1 subType1 = new SubType1(2);
        return subType1.doSomething(C1.CONSTANT);
    }

    public static int method2(SubType2 subType2) {
        return subType2.doSomething(C2.CONSTANT);
    }
}
