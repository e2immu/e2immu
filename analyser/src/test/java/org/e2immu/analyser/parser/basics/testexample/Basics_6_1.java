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

import org.e2immu.annotation.Final;

import java.util.ArrayList;
import java.util.List;

/*
variant on Basics_6 which changes a variable field in an expression, rather than
across expressions
 */
public class Basics_6_1 {

    @Final(absent = true)
    private int field;

    public void test1() {
        int f1, f2, n; // will cause an error: variable not used
        int r = (f1 = field) + (n = interrupting()) + (f2 = field);
        assert r == 2 * f1 + n; // not necessarily true
    }

    public void test2() {
        int f1, f2, n; // will cause an error: variable not used
        int r = (f1 = field) + (n = someMinorMethod(3)) + (f2 = field);
        assert r == 2 * f1 + n; // should always be true
    }

    public int getField() {
        return field;
    }

    public void setField(int field) {
        this.field = field;
    }

    private static int someMinorMethod(int i) {
        return (int) Math.pow(i, 3); // not interrupting!
    }

    private static int interrupting() {
        System.out.println("I might be interrupted");
        return 4;
    }
}
