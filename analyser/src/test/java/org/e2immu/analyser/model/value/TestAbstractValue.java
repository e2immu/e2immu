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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAbstractValue extends CommonAbstractValue {

    @Test
    public void test() {
        Expression notA = Negation.negate(context, a);
        assertEquals("!a", notA.toString());
        Expression notA2 = Negation.negate(context, a);
        assertEquals(notA, notA2);
        assertEquals(a, Negation.negate(context, notA));

        assertEquals(a, newAndAppend(a, a));
        assertEquals(notA, newAndAppend(notA, notA));
        assertEquals(FALSE, newAndAppend(a, notA));

        // A && A, !A && !A
        assertEquals(a, newAndAppend(a, a));
        assertEquals(notA, newAndAppend(notA, notA));
        // A && !A, !A && A
        assertEquals(FALSE, newAndAppend(a, notA));
        assertEquals(FALSE, newAndAppend(notA, a));

        // F || T
        assertEquals(TRUE, newOrAppend(FALSE, TRUE));
        // A || A, !A || !A
        assertEquals(a, newOrAppend(a, a));
        assertEquals(notA, newOrAppend(notA, notA));
        // A || !A, !A || A
        assertEquals(TRUE, newOrAppend(a, notA));
        assertEquals(TRUE, newOrAppend(notA, a));
    }

    @Test
    public void testAndOfTrues() {
        Expression v = newAndAppend(TRUE, TRUE);
        assertEquals(TRUE, v);
    }

    @Test
    public void testMoreComplicatedAnd() {
        Expression aAndAOrB = newAndAppend(a, newOrAppend(a, b));
        assertEquals(a, aAndAOrB);

        Expression aAndNotAOrB = newAndAppend(a, newOrAppend(Negation.negate(context, a), b));
        assertEquals("a&&b", aAndNotAOrB.toString());

        //D && A && !B && (!A || B) && C (the && C, D is there just for show)
        Expression v = newAndAppend(d, a, negate(b), newOrAppend(negate(a), b), c);
        assertEquals(FALSE, v);
    }

    @Test
    public void testExpandAndInOr() {
        // A || (B && C)
        Expression v = newOrAppend(a, newAndAppend(b, c));
        assertEquals("(a||b)&&(a||c)", v.toString());
    }

    @Test
    public void testInstanceOf() {
        Expression iva = new InstanceOf(Identifier.generate("test"),
                PRIMITIVES, PRIMITIVES.stringParameterizedType(), a, null);
        assertEquals("a instanceof String", iva.toString());
        Expression ivb = new InstanceOf(Identifier.generate("test"),
                PRIMITIVES, PRIMITIVES.stringParameterizedType(), b, null);
        Expression or = newOrAppend(ivb, iva);
        assertEquals("a instanceof String||b instanceof String", or.toString());
        Expression iva2 = new InstanceOf(Identifier.generate("test"),
                PRIMITIVES, PRIMITIVES.objectParameterizedType(), a, null);
        Expression or2 = newOrAppend(iva, iva2);
        assertEquals("a instanceof Object||a instanceof String", or2.toString());
    }

    Map<Variable, Boolean> nullClauses(Expression v, Filter.FilterMode filterMode) {
        Filter filter = new Filter(context, filterMode);
        return filter.filter(v, filter.individualNullOrNotNullClause()).accepted()
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() instanceof Equals Equals && Equals.lhs == NullConstant.NULL_CONSTANT));
    }

    // note: van and vbn are nullable, va and vb are NOT (see CommonAbstractValue)
    @Test
    public void testIsNull() {
        Expression v = Equals.equals(context, an, NullConstant.NULL_CONSTANT);
        assertEquals("null==an", v.toString());
        Map<Variable, Boolean> nullClauses = nullClauses(v, Filter.FilterMode.ACCEPT);
        assertEquals(1, nullClauses.size());
        assertEquals(true, nullClauses.get(van));

        Expression v2 = Equals.equals(context, bn, NullConstant.NULL_CONSTANT);
        assertEquals("null==bn", v2.toString());
        Map<Variable, Boolean> nullClauses2 = nullClauses(v2, Filter.FilterMode.ACCEPT);
        assertEquals(1, nullClauses2.size());
        assertEquals(true, nullClauses2.get(vbn));

        Expression orValue = newOrAppend(v, negate(v2));
        assertEquals("null==an||null!=bn", orValue.toString());
        Map<Variable, Boolean> nullClausesAnd = nullClauses(orValue, Filter.FilterMode.REJECT);
        assertEquals(2, nullClausesAnd.size());
        assertEquals(true, nullClausesAnd.get(van));
        assertEquals(false, nullClausesAnd.get(vbn));
    }

    @Test
    public void testIsNotNull() {
        Expression v = Negation.negate(context, new Equals(Identifier.generate("test"),
                PRIMITIVES, NullConstant.NULL_CONSTANT, a));
        assertEquals("null!=a", v.toString());
        Map<Variable, Boolean> nullClauses = nullClauses(v, Filter.FilterMode.REJECT);
        assertEquals(1, nullClauses.size());
        assertEquals(false, nullClauses.get(va));
    }

    public static final String EXPECTED = "(a||c)&&(a||d)&&(b||c)&&(b||d)";
    public static final String EXPECTED2 = "(a||!c)&&(a||d)&&(!b||!c)&&(!b||d)";

    @Test
    public void testCNF() {
        // (a && b) || (c && d)
        Expression or = newOrAppend(newAndAppend(a, b), newAndAppend(c, d));
        assertEquals(EXPECTED, or.toString());
        or = newOrAppend(newAndAppend(b, a), newAndAppend(d, c));
        assertEquals(EXPECTED, or.toString());
        or = newOrAppend(newAndAppend(d, c), newAndAppend(b, a));
        assertEquals(EXPECTED, or.toString());
    }

    @Test
    public void testCNFWithNot() {
        Expression notB = negate(b);
        Expression notC = negate(c);
        Expression or = newOrAppend(newAndAppend(a, notB), newAndAppend(notC, d));
        assertEquals(EXPECTED2, or.toString());
        or = newOrAppend(newAndAppend(notB, a), newAndAppend(d, notC));
        assertEquals(EXPECTED2, or.toString());
        or = newOrAppend(newAndAppend(d, notC), newAndAppend(notB, a));
        assertEquals(EXPECTED2, or.toString());
    }

    // (not ('a' == c (parameter 0)) and not ('b' == c (parameter 0)) and ('a' == c (parameter 0) or 'b' == c (parameter 0)))
    // not a and not b and (a or b)

    @Test
    public void testForSwitchStatement() {
        Expression v = newAndAppend(negate(a), negate(b), newOrAppend(a, b));
        assertEquals(FALSE, v);

        Expression cIsA = equals(new CharConstant(PRIMITIVES, 'a'), c);
        Expression cIsABis = equals(new CharConstant(PRIMITIVES, 'a'), c);
        assertEquals(cIsA, cIsABis);

        Expression cIsB = equals(new CharConstant(PRIMITIVES, 'b'), c);

        Expression v2 = newAndAppend(negate(cIsA), negate(cIsB), newOrAppend(cIsA, cIsB));
        assertEquals(FALSE, v2);
    }

    @Test
    public void testCompare() {
        Expression aGt4 = GreaterThanZero.greater(context, i, newInt(4), true);
        assertEquals("i>=4", aGt4.toString());

        Expression n4ltB = GreaterThanZero.less(context, newInt(4), i, false);
        assertEquals("i>=5", n4ltB.toString());

        Expression n4lt8 = GreaterThanZero.less(context, newInt(4), newInt(8), false);
        assertEquals(TRUE, n4lt8);
    }

    @Test
    public void testSumProduct() {
        Expression aa = Sum.sum(context, a, a);
        assertEquals("2*a", aa.toString());
        Expression a0 = Sum.sum(context, a, newInt(0));
        assertEquals(a, a0);
        Expression aTimes0 = Product.product(context, a, newInt(0));
        assertEquals(newInt(0), aTimes0);

        Expression a3a = Sum.sum(context, a,
                Product.product(context, newInt(3), a));
        assertEquals("4*a", a3a.toString());
        Expression b2 = Product.product(context, b, newInt(2));
        Expression b4 = Product.product(context, newInt(4), b);
        Expression b4b2 = Sum.sum(context, b4, b2);
        assertEquals("6*b", b4b2.toString());
    }
}
