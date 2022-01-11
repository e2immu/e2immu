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

package org.e2immu.analyser.analyser.analysis.range;

import org.e2immu.analyser.analysis.range.NumericRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestNumericRange {

    @Test
    public void test() {
        assertEquals(10, new NumericRange(0, 10, 1, null).loopCount());
        assertEquals(5, new NumericRange(0, 10, 2, null).loopCount());
        assertEquals(4, new NumericRange(0, 10, 3, null).loopCount());
        assertThrows(AssertionError.class, ()-> new NumericRange(0, 10, 0, null));

        assertEquals(10, new NumericRange(10, 0, -1, null).loopCount());
        assertEquals(5, new NumericRange(10, 0, -2, null).loopCount());
        assertEquals(4, new NumericRange(10, 0, -3, null).loopCount());
    }
}
