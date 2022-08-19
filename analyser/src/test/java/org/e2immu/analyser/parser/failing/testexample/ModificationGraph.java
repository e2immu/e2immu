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

import org.e2immu.annotation.ImmutableContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ImmutableContainer
public class ModificationGraph {

    @Test
    public void useC1AndC2() {
        ModificationGraphC1 c1 = new ModificationGraphC1();
        ModificationGraphC2 c2 = new ModificationGraphC2(2, c1);
        assertEquals(3, c2.incrementAndGetWithI());
        assertEquals(1, c1.getI());
        assertEquals(5, c1.useC2(c2));
        assertEquals(2, c1.getI());
        assertEquals(5, c2.incrementAndGetWithI());
        assertEquals(3, c1.getI());
        assertEquals(9, c1.useC2(c2));
        assertEquals(4, c1.getI());
    }
}
