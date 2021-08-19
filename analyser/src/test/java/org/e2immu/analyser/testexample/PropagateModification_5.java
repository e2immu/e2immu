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
types T and S are implicitly immutable
Type T[] is not -> ts part of support data
myFunction sits somewhere in between

 */
@E1Container
public class PropagateModification_5<T, S> {

    @Dependent1
    private final MyFunction<T, S> myFunction;
    @Linked(to = {"PropagateModification:0:ts"})
    private final T[] ts;

    interface MyFunction<T, S> {
        S apply(T t);
    }

    @Dependent // because of ts
    public PropagateModification_5(T[] ts, @Dependent1 MyFunction<T, S> myFunction) {
        this.myFunction = myFunction;
        this.ts = ts;
    }

    @Independent // not feeding in actual S elements held by the object
    public S get(int i) {
        // ts[i] is content linked to ts
        return myFunction.apply(ts[i]); // feeding in actual T elements -> @Dependent1 on myFunction
    }

    @Dependent1
    public T getT(int i) {
        return ts[i];
    }
}
