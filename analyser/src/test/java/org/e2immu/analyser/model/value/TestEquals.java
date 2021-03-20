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
import org.e2immu.analyser.objectflow.ObjectFlow;
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
        assertEquals("false", Equals.equals(minimalEvaluationContext, int3, int5, ObjectFlow.NO_FLOW).toString());
    }

    @Test
    public void testCommonTerms() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, int3, i, ObjectFlow.NO_FLOW);
        Expression right = Sum.sum(minimalEvaluationContext, int5, i, ObjectFlow.NO_FLOW);
        assertEquals("false", Equals.equals(minimalEvaluationContext, left, right, ObjectFlow.NO_FLOW).toString());
    }

    @Test
    public void testCommonTerms2() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, i, int5, ObjectFlow.NO_FLOW);
        Expression right = Sum.sum(minimalEvaluationContext, int5, i, ObjectFlow.NO_FLOW);
        assertEquals("true", Equals.equals(minimalEvaluationContext, left, right, ObjectFlow.NO_FLOW).toString());
    }

    @Test
    public void testCommonTerms3() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, i, int5, ObjectFlow.NO_FLOW);
        Expression right = i;
        assertEquals("false", Equals.equals(minimalEvaluationContext, left, right, ObjectFlow.NO_FLOW).toString());
    }
}
