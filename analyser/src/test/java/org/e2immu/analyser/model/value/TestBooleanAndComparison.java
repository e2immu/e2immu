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
import org.e2immu.analyser.model.expression.Or;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void test2() {
        Expression iLe3 = GreaterThanZero.less(context, i, newInt(3), true);
        Expression iGe4 = GreaterThanZero.greater(context, i, newInt(4), true);
        Expression iGe5 = GreaterThanZero.greater(context, i, newInt(5), true);
        Expression or = Or.or(context, iGe4, iLe3);
        assertTrue(or.isBoolValueTrue(), "Have " + or);
        Expression or2 = Or.or(context, iLe3, iGe4);
        assertTrue(or2.isBoolValueTrue(), "Have " + or);
        Expression or3 = Or.or(context, iLe3, iGe5);
        assertFalse(or3.isBoolValueTrue());
        Expression or4 = Or.or(context, iLe3, iLe3);
        assertFalse(or4.isBoolValueTrue());
    }

    @Test
    public void test3() {
        // "l0>=11||l0<=2||l0<=10"
        Expression iLe2 = GreaterThanZero.less(context, i, newInt(2), true);
        Expression iLe10 = GreaterThanZero.less(context, i, newInt(10), true);
        Expression iGe11 = GreaterThanZero.greater(context, i, newInt(11), true);
        Expression or1 = Or.or(context, iGe11, iLe10);
        assertTrue(or1.isBoolValueTrue());
        Expression or2 = Or.or(context, iLe2, iLe10);
        assertEquals(iLe10, or2);
        Expression or3 = Or.or(context, iGe11, iLe2, iLe10);
        assertTrue(or3.isBoolValueTrue(), "Got " + or3);
        Expression or4 = Or.or(context, iLe10, iGe11, iLe2);
        assertTrue(or4.isBoolValueTrue(), "Got " + or3);
        Expression or5 = Or.or(context, iLe2, iGe11, iLe10);
        assertTrue(or5.isBoolValueTrue(), "Got " + or3);
    }
}
