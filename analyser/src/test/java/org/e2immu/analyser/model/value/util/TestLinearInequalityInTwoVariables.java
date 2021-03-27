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

package org.e2immu.analyser.model.value.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.e2immu.analyser.model.expression.Product;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.model.expression.util.Inequality;
import org.e2immu.analyser.model.expression.util.InequalityHelper;
import org.e2immu.analyser.model.expression.util.LinearInequalityInTwoVariables;
import org.e2immu.analyser.model.value.CommonAbstractValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestLinearInequalityInTwoVariables extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression iPlusJGe1 = GreaterThanZero.greater(minimalEvaluationContext,
                Sum.sum(minimalEvaluationContext, i, j, ObjectFlow.NO_FLOW), newInt(1), true);
        Inequality inequality =
                InequalityHelper.extract(minimalEvaluationContext, (GreaterThanZero) iPlusJGe1);
        LinearInequalityInTwoVariables two = (LinearInequalityInTwoVariables) inequality;
        assertNotNull(two);
        assertEquals(1.0, two.a(), 0.00001);
        assertEquals(i.variable(), two.x());
        assertEquals(1.0, two.b(), 0.00001);
        assertEquals(j.variable(), two.y());
        assertEquals(-1.0, two.c(), 0.00001);
        assertTrue(two.allowEquals());
    }

    @Test
    public void test2() {
        Expression i2 = Product.product(minimalEvaluationContext, newInt(2), i, ObjectFlow.NO_FLOW);
        Expression minusJ3 = negate(Product.product(minimalEvaluationContext, j, newInt(3), ObjectFlow.NO_FLOW));
        Expression i2Minus3JGe1 = GreaterThanZero.greater(minimalEvaluationContext,
                Sum.sum(minimalEvaluationContext, i2, minusJ3, ObjectFlow.NO_FLOW), newInt(1), true);
        assertEquals("2*i-(3*j)>=1", i2Minus3JGe1.toString());
        Inequality inequality =
                InequalityHelper.extract(minimalEvaluationContext, (GreaterThanZero) i2Minus3JGe1);
        LinearInequalityInTwoVariables two = (LinearInequalityInTwoVariables) inequality;
        assertNotNull(two);
        assertEquals(2.0, two.a(), 0.00001);
        assertEquals(i.variable(), two.x());
        assertEquals(-3.0, two.b(), 0.00001);
        assertEquals(j.variable(), two.y());
        assertEquals(-1.0, two.c(), 0.00001);
        assertTrue(two.allowEquals());
    }
}
