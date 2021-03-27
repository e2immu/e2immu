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

import org.e2immu.analyser.model.expression.util.Interval;
import org.e2immu.analyser.model.expression.util.LinearInequalityInOneVariable;
import org.e2immu.analyser.model.value.CommonAbstractValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLinearEqualityInOneVariable extends CommonAbstractValue {

    @Test
    public void test1() {
        // inequality is i >= 1, written as 1*i + (-1) >= 0
        LinearInequalityInOneVariable l = new LinearInequalityInOneVariable(minimalEvaluationContext,
                1, vi, -1, true);
        assertFalse(l.accept(-1));
        Interval i1 = new Interval(Double.NEGATIVE_INFINITY, true, -1, false);
        assertFalse(l.accept(i1));

        assertTrue(l.accept(1));
        Interval i2 = new Interval(Double.NEGATIVE_INFINITY, true, 1, false);
        assertFalse(l.accept(i2));
        Interval i3 = new Interval(Double.NEGATIVE_INFINITY, true, 1, true);
        assertTrue(l.accept(i3));
        Interval i4 = new Interval(1, false, Double.POSITIVE_INFINITY, true);
        assertTrue(l.accept(i4));

        assertTrue(l.accept(2));
        Interval i5 = new Interval(2, false, 4, true);
        assertTrue(l.accept(i5));

        Interval i6 = new Interval(-3, false, -2, true);
        assertFalse(l.accept(i6));
        Interval i7 = new Interval(-3, false, 2, true);
        assertTrue(l.accept(i7));
    }

    @Test
    public void test2() {
        // inequality is 0.5i < 10 (or i<20), written as -0.5*i + 10 > 0
        LinearInequalityInOneVariable l = new LinearInequalityInOneVariable(minimalEvaluationContext,
                -0.5, vi, 10, false);
        assertFalse(l.accept(21));
        assertFalse(l.accept(20));
        assertTrue(l.accept(19));

        Interval i1 = new Interval(Double.NEGATIVE_INFINITY, true, 19, false);
        assertTrue(l.accept(i1));
        Interval i2 = new Interval(Double.NEGATIVE_INFINITY, true, 21, false);
        assertTrue(l.accept(i2));

        Interval i3 = new Interval(19, true, Double.POSITIVE_INFINITY, true);
        assertTrue(l.accept(i3));
        Interval i4 = new Interval(20, false, Double.POSITIVE_INFINITY, true);
        assertFalse(l.accept(i4));
        Interval i5 = new Interval(21, false, Double.POSITIVE_INFINITY, true);
        assertFalse(l.accept(i5));

        Interval i6 = new Interval(21, false, 30, true);
        assertFalse(l.accept(i6));
        Interval i7 = new Interval(19, true, 30, true);
        assertTrue(l.accept(i7));
    }
}
