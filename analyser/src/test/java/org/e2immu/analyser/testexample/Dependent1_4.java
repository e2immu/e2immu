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

import org.e2immu.annotation.*;

/*
this container holds a single object (a supplier) in an immutable way.
The type of the supplier and its actions are outside the scope.
 */
@E2Container
public class Dependent1_3<T> {

    private final MySupplier<T> mySupplier;

    @E1Container // computed (get is modified, so not E2)
    interface MySupplier<T> {
        @Modified
        @Dependent1 // computed
        T get();
    }

    @Independent // not @Dependent1 because mySupplier is not implicitly immutable!
    // how come independent? mySupplier is not @E2Immutable
    public Dependent1_3(MySupplier<T> mySupplier) {
        this.mySupplier = mySupplier;
    }

    @Dependent1 // because returns implicitly immutable content of a field, using a method
    public T get() {
        return mySupplier.get();
    }
}
