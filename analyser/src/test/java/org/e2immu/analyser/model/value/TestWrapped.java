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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWrapped extends CommonAbstractValue {

    private static Expression eq(Expression e1, Expression e2) {
        return Equals.equals(context, e1, e2);
    }

    @Test
    public void testStringEquals() {
        Expression stringA = new StringConstant(PRIMITIVES, "a");
        Expression stringB = new StringConstant(PRIMITIVES, "b");
        assertTrue(eq(stringA, stringB).isBoolValueFalse());
        assertTrue(eq(stringA, stringA).isBoolValueTrue());

        Expression bWrapped = PropertyWrapper.propertyWrapper(stringB, LinkedVariables.of(Map.of(va, LinkedVariables.LINK_ASSIGNED)));
        assertEquals("\"b\"/*{L a:assigned:1}*/", bWrapped.toString());

        assertTrue(eq(bWrapped, stringB).isBoolValueTrue());
        assertTrue(eq(stringA, bWrapped).isBoolValueFalse());
    }

    @Test
    public void testGe() {
        Expression ten = new IntConstant(PRIMITIVES, 10);
        Expression tenWrapped = PropertyWrapper.propertyWrapper(ten, LinkedVariables.of(Map.of(va, LinkedVariables.LINK_ASSIGNED)));
        Expression gt = GreaterThanZero.greater(context, ten, tenWrapped, true);
        assertTrue(gt.isBoolValueTrue());
    }
}
