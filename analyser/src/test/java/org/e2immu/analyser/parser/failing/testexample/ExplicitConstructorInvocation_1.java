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

import org.e2immu.annotation.Final;
import org.e2immu.annotation.Nullable;

public class ExplicitConstructorInvocation_1 {

    @Nullable
    @Final
    private String s;

    private final int n;

    public ExplicitConstructorInvocation_1() {
        this("abc", 3);
        s = "x";
    }

    public ExplicitConstructorInvocation_1(String sp, int n) {
        s = sp;
        this.n = n;
    }

    public String getS() {
        return s;
    }

    public int getN() {
        return n;
    }
}
