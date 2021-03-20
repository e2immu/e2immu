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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.MutableModifiesArguments;
import org.e2immu.annotation.Variable;

@MutableModifiesArguments
class ModificationGraphC1 {

    @Variable
    private int i;

    @Modified
    public int incrementAndGet() {
        return ++i;
    }

    public int getI() {
        return i;
    }

    @Modified // <1>
    public int useC2(@Modified ModificationGraphC2 c2) {
        return i + c2.incrementAndGetWithI();
    }

}
