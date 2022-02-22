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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBooleanAndComparison extends CommonAbstractValue {

    // this test verifies that combining preconditions will work.

    @Test
    public void test1() {
        GreaterThanZero iGe0 = (GreaterThanZero) GreaterThanZero.greater(context, i, newInt(0), true);
        GreaterThanZero iLt0 = (GreaterThanZero) GreaterThanZero.less(context, i, newInt(0), false);
        GreaterThanZero jGe0 = (GreaterThanZero) GreaterThanZero.greater(context, j, newInt(0), true);

        Expression iGe0_and__iLt0_or_jGe0 = newAndAppend(iGe0, newOrAppend(iLt0, jGe0));
        assertEquals("i>=0&&j>=0", iGe0_and__iLt0_or_jGe0.toString());

        Expression addIGe0Again = newAndAppend(iGe0_and__iLt0_or_jGe0, iGe0);
        assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again);

        Expression addIGe0Again2 = newAndAppend(iGe0, iGe0_and__iLt0_or_jGe0);
        assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again2);
    }

}
