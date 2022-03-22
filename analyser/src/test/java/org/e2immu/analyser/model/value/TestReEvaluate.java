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

import org.e2immu.analyser.analyser.ForwardReEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.Product;
import org.e2immu.analyser.model.expression.Sum;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestReEvaluate extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression square = Product.product(context, i, i);
        assertEquals("i*i", square.toString());
        Map<Expression, Expression> translate = Map.of(i, newInt(3));
        Expression re = square.reEvaluate(context, translate, ForwardReEvaluationInfo.DEFAULT).value();
        assertEquals("9", re.toString());
    }

    @Test
    public void test2() {
        Expression value = Sum.sum(context,
                newInt(10), negate(Product.product(context, i, j)));
        assertEquals("10-(i*j)", value.toString());
        Map<Expression, Expression> translate = Map.of(i, newInt(3));
        Expression re = value.reEvaluate(context, translate, ForwardReEvaluationInfo.DEFAULT).value();
        assertEquals("10-(3*j)", re.toString());
        Map<Expression, Expression> translate2 = Map.of(j, newInt(2));
        Expression re2 = re.reEvaluate(context, translate2, ForwardReEvaluationInfo.DEFAULT).value();
        assertEquals("4", re2.toString());
    }
}
