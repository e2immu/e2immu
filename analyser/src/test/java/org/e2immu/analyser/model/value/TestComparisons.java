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
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestComparisons extends CommonAbstractValue {

    @Test
    public void testNegate1() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(minimalEvaluationContext, i, newInt(3), false);
        assertEquals("i>=4", gt3.toString());
        GreaterThanZero notGt3 = (GreaterThanZero) gt3.negate(minimalEvaluationContext);
        assertEquals("i<=3", notGt3.toString());
    }

    @Test
    public void testXb() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(minimalEvaluationContext, i, newInt(3), false);
        assertEquals("i>=4", gt3.toString());
        GreaterThanZero.XB xb = gt3.extract(minimalEvaluationContext);
        assertNotNull(xb);
        assertTrue(gt3.allowEquals());
        assertEquals(4, (int) xb.b());
        assertEquals(i, xb.x());
        assertFalse(xb.lessThan());
    }

    @Test
    public void testXb2() {
        GreaterThanZero lt3 = (GreaterThanZero) GreaterThanZero.less(minimalEvaluationContext, i, newInt(3), false);
        assertEquals("i<=2", lt3.toString());
        GreaterThanZero.XB xb = lt3.extract(minimalEvaluationContext);
        assertNotNull(xb);
        assertTrue(lt3.allowEquals());
        assertEquals(2, (int) xb.b());
        assertEquals(i, xb.x());
        assertTrue(xb.lessThan());
    }

    @Test
    public void testEqualsEquals() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iEq3 = equals(newInt(3), i);
        Expression and = newAndAppend(iEq3, iEq4);
        assertEquals(FALSE, and);
    }

    @Test
    public void testEqualsNotEquals() {
        Expression iEq4 = equals(i, newInt(4));
        assertEquals("4==i", iEq4.toString());
        Expression iNeq3 = negate(equals(newInt(3), i));
        assertEquals("3!=i", iNeq3.toString());
        Expression and = newAndAppend(iNeq3, iEq4);
        assertEquals(iEq4, and);
    }

    @Test
    public void testEqualsGreaterThan0() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        Expression and = newAndAppend(iGe0, iEq4);
        assertEquals(iEq4, and);

        Expression iEqMinus4 = equals(i, newInt(-4));
        Expression and2 = newAndAppend(iGe0, iEqMinus4);
        assertEquals(FALSE, and2);

        Expression iEq0 = equals(i, newInt(0));
        Expression and4 = newAndAppend(iGe0, iEq0);
        assertEquals(iEq0, and4);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsGreaterThan1() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iGe1 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(1), true);
        Expression and = newAndAppend(iGe1, iEq4);
        assertEquals(iEq4, and);

        Expression iEqMinus4 = equals(i, newInt(-4));
        Expression and2 = newAndAppend(iGe1, iEqMinus4);
        assertEquals(FALSE, and2);

        Expression iEq0 = equals(i, newInt(0));
        Expression iGt0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), false);
        Expression and3 = newAndAppend(iGt0, iEq0);
        assertEquals(FALSE, and3);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsLessThan1() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iLe1 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(1), true);
        Expression and = newAndAppend(iLe1, iEq4);
        assertEquals(FALSE, and);

        Expression iEqMinus4 = equals(i, newInt(-4));
        Expression and2 = newAndAppend(iLe1, iEqMinus4);
        assertEquals(iEqMinus4, and2);

        Expression iEq0 = equals(i, newInt(0));
        Expression iLt0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), false);
        Expression and3 = newAndAppend(iLt0, iEq0);
        assertEquals(FALSE, and3);
    }

    @Test
    public void test1() {
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iGe3 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(3), true);
        assertEquals("i>=3", iGe3.toString());
        Expression and = newAndAppend(iGe0, iGe3);
        assertEquals(iGe3, and);
        Expression and2 = newAndAppend(iGe3, iGe0);
        assertEquals(iGe3, and2);
    }

    // i <= 0 && i <= 3 ==> i <= 0
    @Test
    public void test2() {
        Expression iLe0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i<=0", iLe0.toString());
        Expression iLe3 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(3), true);
        assertEquals("i<=3", iLe3.toString()); // even though ugly, formatting is correct
        Expression and = newAndAppend(iLe0, iLe3);
        assertEquals(iLe0, and);
    }

    @Test
    public void test3() {
        Expression iLe0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i<=0", iLe0.toString());
        Expression iGe3 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(3), true);
        assertEquals("i>=3", iGe3.toString());
        Expression and = newAndAppend(iLe0, iGe3);
        assertEquals(FALSE, and);
        Expression and2 = newAndAppend(iGe3, iLe0);
        assertEquals(FALSE, and2);
    }

    @Test
    public void test4() {
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iLe3 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(3), true);
        assertEquals("i<=3", iLe3.toString());
        Expression and = newAndAppend(iGe0, iLe3);
        assertTrue(and instanceof And);
        Expression and2 = newAndAppend(iLe3, iGe0);
        assertTrue(and2 instanceof And);
    }

    @Test
    public void test5() {
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iGt0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), false);
        assertEquals("i>=1", iGt0.toString());
        Expression and = newAndAppend(iGe0, iGt0);
        assertEquals(iGt0, and);
    }

    @Test
    public void test6() {
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iLe0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i<=0", iLe0.toString());
        Expression and = newAndAppend(iGe0, iLe0);
        assertEquals("0==i", and.toString());
    }

    @Test
    public void test7() {
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), false);
        assertEquals("i>=1", iGe0.toString());
        Expression iLe0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), false);
        assertEquals("i<=-1", iLe0.toString());
        Expression and = newAndAppend(iGe0, iLe0);
        assertEquals(FALSE, and);
        Expression and2 = newAndAppend(iLe0, iGe0);
        assertEquals(FALSE, and2);
    }

    @Test
    public void testGEZeroLZero() {
        Expression iLt0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), false);
        assertEquals("i<=-1", iLt0.toString());
        Expression iGe0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());

        assertEquals("false", newAndAppend(iLt0, iGe0).toString());
        assertEquals("false", newAndAppend(iGe0, iLt0).toString());
    }

    // (p>=3||p<=2)&&(p>=3||q>=5)&&(p<=2||q<=-1)&&(p<=2||q<0) &&( q>=5||q<=-1) && (q>=5||q<0)
    // problem seems to be that q<0 should never arise -> see second test!
    @Test
    public void testCC7() {
        Expression iLt0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), false);
        Expression iLeM1 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(-1), true);
        assertEquals(iLt0, iLeM1);

        Expression jGt5 = GreaterThanZero.greater(minimalEvaluationContext, j, newInt(5), true);

        Expression or1 = newOrAppend(iLt0, jGt5);
        assertEquals("j>=5||i<=-1", or1.toString());
        Expression or2 = newOrAppend(iLeM1, jGt5);
        assertEquals("j>=5||i<=-1", or2.toString());

        assertEquals(or1, or2);

        Expression negOr1 = negate(or1);
        assertEquals("j<=4&&i>=0", negOr1.toString());
    }

    @Test
    public void testCC7_2() {
        Expression iLt0 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(2), true);
        Expression jGe0 = GreaterThanZero.greater(minimalEvaluationContext, j, newInt(0), true);
        Expression or1 = newOrAppend(iLt0, jGe0);
        assertEquals("i<=2||j>=0", or1.toString());
        Expression notOr1 = negate(or1);
        assertEquals("i>=3&&j<=-1", notOr1.toString());
    }
}
