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

import java.util.function.BinaryOperator;

// can still be inlined
public class Lambda_6 {

    public final int i;

    public Lambda_6(int i) {
        this.i = i;
    }

    public int direct(int a, int b) {
        return (a + b) * (a - i);
    }

    public int applyDirect() {
        return direct(i, i);
    }

    public BinaryOperator<Integer> method() {
        return (a, b) -> (a + b) * (a - i);
    }

    public int applyMethod() {
        return method().apply(i, i);
    }
}
