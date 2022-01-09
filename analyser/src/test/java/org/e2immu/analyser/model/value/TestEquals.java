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
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.Sum;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEquals extends CommonAbstractValue {
    @BeforeAll
    public static void beforeClass() {
        CommonAbstractValue.beforeClass();
    }

    @Test
    public void test() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        assertEquals("false", Equals.equals(minimalEvaluationContext, int3, int5).toString());
    }

    @Test
    public void testCommonTerms() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, int3, i);
        Expression right = Sum.sum(minimalEvaluationContext, int5, i);
        assertEquals("false", Equals.equals(minimalEvaluationContext, left, right).toString());
    }

    @Test
    public void testCommonTerms2() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, i, int5);
        Expression right = Sum.sum(minimalEvaluationContext, int5, i);
        assertEquals("true", Equals.equals(minimalEvaluationContext, left, right).toString());
    }

    @Test
    public void testCommonTerms3() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, i, int5);
        Expression right = i;
        assertEquals("false", Equals.equals(minimalEvaluationContext, left, right).toString());
    }

    @Test
    public void testSort() {
        assertEquals("-4==i", Equals.equals(minimalEvaluationContext, newInt(-4), i).toString());
        assertEquals("-4==i", Equals.equals(minimalEvaluationContext, newInt(4), negate(i)).toString());
    }

    @Test
    public void test1() {
        assertEquals("true", Equals.equals(minimalEvaluationContext, newInt(-4), newInt(-4)).toString());
        assertEquals("0==i", Equals.equals(minimalEvaluationContext, i, negate(i)).toString());
    }

    @Test
    public void test2() {
        Expression sum = Sum.sum(minimalEvaluationContext, i, j);
        assertEquals("i+j", sum.toString());
        assertEquals("-4==i+j", Equals.equals(minimalEvaluationContext, sum, newInt(-4)).toString());
    }
}
