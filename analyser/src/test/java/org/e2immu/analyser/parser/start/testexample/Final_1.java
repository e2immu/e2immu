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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.*;

/* direct COPY-PASTE into the manual, @Final annotation */

@Container
class Final_1 {

    @Final
    private int i;

    @Final(absent = true)
    private int j;

    public final int k; // <1>

    public Final_1(int p, int q) {
        setI(p);
        this.k = q;
    }

    @NotModified
    public int getI() {
        return i;
    }

    @Modified(construction = true) // <2>
    private void setI(int i) {
        this.i = i;
    }

    @NotModified
    public int getJ() {
        return j;
    }

    @Modified
    public void setJ(int j) {
        this.j = j;
    }
}