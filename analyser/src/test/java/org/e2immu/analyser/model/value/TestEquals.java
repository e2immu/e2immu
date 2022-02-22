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
import org.e2immu.analyser.model.expression.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEquals extends CommonAbstractValue {
    @BeforeAll
    public static void beforeClass() {
        CommonAbstractValue.beforeClass();
    }

    private static Expression eq(Expression e1, Expression e2) {
        return Equals.equals(context, e1, e2);
    }

    @Test
    public void test() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        assertEquals("false", eq(int3, int5).toString());
    }

    @Test
    public void testCommonTerms() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        Expression left = Sum.sum(context, int3, i);
        Expression right = Sum.sum(context, int5, i);
        assertEquals("false", eq(left, right).toString());
    }

    @Test
    public void testCommonTerms2() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(context, i, int5);
        Expression right = Sum.sum(context, int5, i);
        assertEquals("true", eq(left, right).toString());
    }

    @Test
    public void testCommonTerms3() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(context, i, int5);
        Expression right = i;
        assertEquals("false", eq(left, right).toString());
    }

    @Test
    public void testSort() {
        assertEquals("-4==i", eq(newInt(-4), i).toString());
        assertEquals("-4==i", eq(newInt(4), negate(i)).toString());
    }

    @Test
    public void test1() {
        assertEquals("true", eq(newInt(-4), newInt(-4)).toString());
        assertEquals("0==i", eq(i, negate(i)).toString());
    }

    @Test
    public void test2() {
        Expression sum = Sum.sum(context, i, j);
        assertEquals("i+j", sum.toString());
        assertEquals("-4==i+j", eq(sum, newInt(-4)).toString());
    }

    @Test
    public void testModulo1() {
        Expression iMod2 = Remainder.remainder(context, i, newInt(2));
        Expression e1 = eq(newInt(1), iMod2);
        Expression e2 = eq(newInt(4), i);
        assertEquals("1==i%2", e1.toString());
        assertEquals("4==i", e2.toString());
        assertTrue(i.compareTo(iMod2) > 0);
        assertEquals("false", And.and(context, e1, e2).toString());
    }

    @Test
    public void testModulo2() {
        Expression iMod2 = Remainder.remainder(context, i, newInt(2));
        Expression e1 = eq(newInt(1), iMod2);
        Expression e2 = eq(newInt(5), i);
        assertEquals("1==i%2", e1.toString());
        assertEquals("5==i", e2.toString());
        assertEquals("5==i", And.and(context, e1, e2).toString());
    }

    @Test
    public void testModulo3() {
        Expression iMod2 = Remainder.remainder(context, i, newInt(2));
        Expression e1 = eq(newInt(1), iMod2);
        Expression e2 = eq(newInt(5), i);
        assertEquals("1==i%2", e1.toString());
        assertEquals("5==i", e2.toString());
        assertEquals("5==i", And.and(context, e1, e2).toString());
    }

    @Test
    public void testEqualsOr() {
        Expression eq1 = eq(newInt(1), i);
        Expression ge10 = GreaterThanZero.greater(context, i, newInt(10), true);
        Expression leM1 = GreaterThanZero.less(context, i, newInt(-1), true);
        // 1==i && (i >= 10 || i <= -1)
        Or or = (Or) newOrAppend(ge10, leM1);
        assertTrue(And.safeToExpandOr(i, or));
        Expression joint = newAndAppend(eq1, or);
        assertEquals("false", joint.toString());
    }
}
