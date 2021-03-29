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
import org.e2immu.analyser.model.expression.util.InequalitySolver;
import org.e2immu.analyser.model.value.CommonAbstractValue;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInequalitySolver extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression iGt0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), false);
        Expression jLt0 = GreaterThanZero.less(minimalEvaluationContext, j, newInt(0), false);
        Expression iGt0AndJLt0 = newAndAppend(iGt0, jLt0);
        InequalitySolver inequalitySolver = new InequalitySolver(minimalEvaluationContext, iGt0AndJLt0);
        assertEquals("i=[i>=1],j=[j<=-1]", inequalitySolver.getPerComponent()
                .entrySet().stream().map(Object::toString).sorted().collect(Collectors.joining(",")));

        Expression i2 = Product.product(minimalEvaluationContext, newInt(2), i);
        Expression minusJ3 = negate(Product.product(minimalEvaluationContext, j, newInt(3)));
        Expression i2Minus3JGe1 = GreaterThanZero.greater(minimalEvaluationContext,
                Sum.sum(minimalEvaluationContext, i2, minusJ3), newInt(1), true);
        assertEquals("2*i-(3*j)>=1", i2Minus3JGe1.toString());

        assertTrue(inequalitySolver.evaluate(i2Minus3JGe1));
    }

    @Test
    public void test2() {

    }
}
