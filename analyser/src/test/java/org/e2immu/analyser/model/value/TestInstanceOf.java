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
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.InstanceOf;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestInstanceOf extends CommonAbstractValue {

    private static Expression instanceOf(Expression e, ParameterizedType p) {
        return new InstanceOf(Identifier.CONSTANT, PRIMITIVES, p, e, null);
    }

    @Test
    public void test() {
        Expression e1 = instanceOf(a, new ParameterizedType(PRIMITIVES.boxedBooleanTypeInfo(), 0));
        assertEquals("a instanceof Boolean", e1.toString());

        Expression e2 = instanceOf(a, new ParameterizedType(PRIMITIVES.integerTypeInfo(), 0));
        assertEquals("false", newAndAppend(e1, e2).toString());

        assertEquals("a instanceof Boolean", newAndAppend(e1, negate(e2)).toString());
    }

    @Test
    public void test2() {
        Expression e1 = instanceOf(a, new ParameterizedType(PRIMITIVES.boxedBooleanTypeInfo(), 0));
        assertEquals("a instanceof Boolean", e1.toString());

        // object simply disappears!
        Expression e2 = instanceOf(a, new ParameterizedType(PRIMITIVES.objectTypeInfo(), 0));
        assertEquals("a instanceof Boolean", newAndAppend(e1, e2).toString());

        // not object is not possible
        assertEquals("false", newAndAppend(e1, negate(e2)).toString());
    }

    @Test
    public void test3() {
        Expression e1 = instanceOf(a, new ParameterizedType(PRIMITIVES.objectTypeInfo(), 0));
        assertEquals("a instanceof Object", e1.toString());

        // object simply disappears!
        Expression e2 = instanceOf(a, new ParameterizedType(PRIMITIVES.boxedBooleanTypeInfo(), 0));
        assertEquals("a instanceof Boolean", newAndAppend(e1, e2).toString());

        // a instanceof Object remains
        assertEquals("!(a instanceof Boolean)&&a instanceof Object", newAndAppend(e1, negate(e2)).toString());
    }

    private static final String AND_OF_ORS = "(!(an instanceof Boolean)||null==an)&&(!(an instanceof Character)||null==an)";

    @Test
    public void test4() {
        Expression e1 = instanceOf(an, new ParameterizedType(PRIMITIVES.boxedBooleanTypeInfo(), 0));
        Expression e2 = instanceOf(an, new ParameterizedType(PRIMITIVES.boxed(PRIMITIVES.charTypeInfo()), 0));

        Expression or1 = newOrAppend(negate(e1), equals(NullConstant.NULL_CONSTANT, an));
        assertEquals("!(an instanceof Boolean)||null==an", or1.toString());
        Expression or2 = newOrAppend(negate(e2), equals(NullConstant.NULL_CONSTANT, an));
        assertEquals("!(an instanceof Character)||null==an", or2.toString());

        assertEquals(10, or1.getComplexity());
        Expression and = newAndAppend(or1, or2);
        assertEquals(AND_OF_ORS, and.toString());

        Expression and1 = newAndAppend(negate(e1), negate(e2));
        assertEquals("!(an instanceof Boolean)&&!(an instanceof Character)", and1.toString());
        Expression or3 = newOrAppend(and1, equals(NullConstant.NULL_CONSTANT, an));
        assertEquals(AND_OF_ORS, or3.toString());
    }
}
