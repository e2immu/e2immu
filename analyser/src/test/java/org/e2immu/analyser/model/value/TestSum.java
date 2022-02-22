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
import org.e2immu.analyser.model.expression.Product;
import org.e2immu.analyser.model.expression.Sum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSum extends CommonAbstractValue {

    private static Expression sum(Expression e1, Expression e2) {
        return Sum.sum(context, e1, e2);
    }

    private static Expression product(Expression e1, Expression e2) {
        return Product.product(context, e1, e2);
    }

    @Test
    public void test1() {
        Expression s = sum(newInt(1), i);
        assertEquals("1+i", s.toString());
        Expression s2 = sum(newInt(2), s);
        assertEquals("3+i", s2.toString());
    }

    @Test
    public void test2() {
        Expression s = sum(newInt(1), sum(i, newInt(3)));
        assertEquals("4+i", s.toString());
        Expression s2 = sum(sum(newInt(3), newInt(2)), sum(s, newInt(-9)));
        assertEquals("i", s2.toString());
        Expression s3 = sum(sum(s2, newInt(3)), negate(s2));
        assertEquals("3", s3.toString());
    }

    @Test
    public void test3() {
        Expression s = sum(sum(newInt(-1), product(j, newInt(-3))), product(newInt(2), i));
        assertEquals("-1+2*i+-3*j", s.toString());
    }
}
