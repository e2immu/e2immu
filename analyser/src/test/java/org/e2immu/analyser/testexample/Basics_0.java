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

@E2Container
public class Basics_0 {

    // we don't want @Final here, because it is explicitly so...
    @Final(absent = true)
    // again, String is @E2Container by definition, we don't want that plastered all around
    @E2Container(absent = true)

    @Constant("abc")
    private final String explicitlyFinal = "abc";

    // again, String is @E2Container by definition, we don't want that plastered all around
    @E2Container(absent = true)
    // a method returning an @E2Immutable type is always @Independent
    @Independent(absent = true)

    @NotNull
    @Constant("abc")
    public String getExplicitlyFinal() {
        return explicitlyFinal;
    }
}
