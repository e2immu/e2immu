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
Exact copy of ForEachMethod_0, but with other names.
 */
@E2Container // computed
public class ForEachMethod_5<S> {

    // implicitly: @E1Container
    interface Set<T> {
        @Modified
        void add(@NotModified T t);
    }

    // of implicitly immutable type
    private final S s;

    public ForEachMethod_5(@Dependent1 S in) {
        this.s = in;
    }

    // set is @Modified, the normal way of working.
    @NotModified
    public void addToSet(@Modified @Dependent2 Set<S> set) {
        set.add(s);
    }

}
