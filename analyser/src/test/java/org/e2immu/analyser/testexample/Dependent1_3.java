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

import org.e2immu.annotation.Dependent1;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;

/*
In this example, the type holds an abstract type (a Supplier).
As opposed to example 4, this Supplier is level 2 immutable.
 */
@E2Container
public class Dependent1_3<T> {

    @NotModified
    private final MySupplier<T> mySupplier;

    @E2Container // contracted, implies that get is @NotModified, @Independent
    interface MySupplier<T> {
        // we go a little further here, and stipulate that T is content-linked to the fields of the supplier
        @Dependent1
        T get();
    }

    @Independent // not @Dependent1 because mySupplier is not implicitly immutable!
    public Dependent1_3(MySupplier<T> mySupplier) {
        this.mySupplier = mySupplier;
    }

    @NotModified
    @Dependent1 // because returns implicitly immutable content of a field, using a method
    public T get() {
        return mySupplier.get();
    }
}
