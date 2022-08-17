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

import org.e2immu.annotation.ConstantContainer;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Variable;

// more complicated version of Basics_6
// showing that local variable copies are needed in the scope of fields, when that
// field is variable
public class Basics_18 {

    @ConstantContainer
    record A(int i) {
    }

    @Variable
    @NotNull
    private A a = new A(3);

    public void get() {
        A a11 = a;
        System.out.println("!");
        A a12 = a;
        assert a11 == a12;
        assert a11.i == a12.i; // mmmm.... why would it be possible they're different, but mush have the same i?
        // we can do better than IntelliJ here; it is possible that a11!=a12, and therefore also that a11.i!=a12.i
    }

    public void setA(A a) {
        if(a == null) throw new UnsupportedOperationException();
        this.a = a;
    }
}
