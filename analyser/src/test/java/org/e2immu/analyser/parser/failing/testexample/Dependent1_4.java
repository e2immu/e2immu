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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;

/*
Variant on Dependent1_3, but now mutable.

Note that get() is modified! Ignoring the modification of the method can only be done
in the case of parameters (see ForEachMethod examples).
 */
@FinalFields
@Container
public class Dependent1_4<T> {

    @Modified
    private final MySupplier<T> mySupplier;

    // both type annotations are or can be computed
    @FinalFields
    @Container
    @Independent
    interface MySupplier<T> {
        // we mark that get is @Modified, and returns content linked to the fields of the implementation
        @Modified
        // @Independent1, implicitly
        T get();
    }

    // independent, because while not E2, MySupplier is Independent itself
    // not Dep1, because mySupplier is not transparent.
    @Independent
    public Dependent1_4(MySupplier<T> mySupplier) {
        this.mySupplier = mySupplier;
    }

    // modified because get() is a modifying method
    @Modified
    @Independent(hc = true) // because returns immutable content of a field, using a method
    public T get() {
        return mySupplier.get();
    }
}
