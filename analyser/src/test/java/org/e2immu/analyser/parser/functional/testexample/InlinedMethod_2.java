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

package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.NotModified;

public class InlinedMethod_2 {
    private final int r;

    public InlinedMethod_2(int rr) {
        this.r = rr;
    }

    @NotModified
    public int plus(int i) {
        return i + r;
    }

    @ImmutableContainer
    public int difference31() {
        return plus(3) - plus(1);
    }

    @ImmutableContainer("0")
    public int difference11() {
        return plus(1) - plus(1);
    }
}
