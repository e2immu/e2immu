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

import java.util.Set;


// probably more interesting of its @NotNull on p
// the statement time does not increase going from statement 0 to the reading of s (before add!)
// the @NotNull on the local copy must be on the field's value as well

@Container(absent = true)
@MutableModifiesArguments
public class Container_0 {

    @Nullable
    private Set<String> s;

    @Modified
    public void setS(@Modified @NotNull Set<String> p, String toAdd) {
        this.s = p;
        this.s.add(toAdd);
    }

    public Set<String> getS() {
        return s;
    }
}
