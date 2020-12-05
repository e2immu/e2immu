/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

public class TestAbstractValue extends CommonAbstractValue {

    @Test
    public void test() {
        Expression notA = Negation.negate(minimalEvaluationContext, a);
        Assert.assertEquals("not (a)", notA.toString());
        Expression notA2 = Negation.negate(minimalEvaluationContext, a);
        Assert.assertEquals(notA, notA2);
        Assert.assertEquals(a, Negation.negate(minimalEvaluationContext, notA));

        Assert.assertEquals(a, newAndAppend(a, a));
        Assert.assertEquals(notA, newAndAppend(notA, notA));
        Assert.assertEquals(FALSE, newAndAppend(a, notA));

        // A && A, !A && !A
        Assert.assertEquals(a, newAndAppend(a, a));
        Assert.assertEquals(notA, newAndAppend(notA, notA));
        // A && !A, !A && A
        Assert.assertEquals(FALSE, newAndAppend(a, notA));
        Assert.assertEquals(FALSE, newAndAppend(notA, a));

        // F || T
        Assert.assertEquals(TRUE, newOrAppend(FALSE, TRUE));
        // A || A, !A || !A
        Assert.assertEquals(a, newOrAppend(a, a));
        Assert.assertEquals(notA, newOrAppend(notA, notA));
        // A || !A, !A || A
        Assert.assertEquals(TRUE, newOrAppend(a, notA));
        Assert.assertEquals(TRUE, newOrAppend(notA, a));
    }

    @Test
    public void testAndOfTrues() {
        Expression v = newAndAppend(TRUE, TRUE);
        Assert.assertEquals(TRUE, v);
    }

    @Test
    public void testMoreComplicatedAnd() {
        Expression aAndAOrB = newAndAppend(a, newOrAppend(a, b));
        Assert.assertEquals(a, aAndAOrB);

        Expression aAndNotAOrB = newAndAppend(a, newOrAppend(Negation.negate(minimalEvaluationContext, a), b));
        Assert.assertEquals("(a and b)", aAndNotAOrB.toString());

        //D && A && !B && (!A || B) && C (the && C, D is there just for show)
        Expression v = newAndAppend(d, a, negate(b), newOrAppend(negate(a), b), c);
        Assert.assertEquals(FALSE, v);
    }

    @Test
    public void testExpandAndInOr() {
        // A || (B && C)
        Expression v = newOrAppend(a, newAndAppend(b, c));
        Assert.assertEquals("((a or b) and (a or c))", v.toString());
    }

    @Test
    public void testInstanceOf() {
        Expression iva = new InstanceOf(PRIMITIVES, PRIMITIVES.stringParameterizedType, null, va, ObjectFlow.NO_FLOW);
        Assert.assertEquals("a instanceof java.lang.String", iva.toString());
        Expression ivb = new InstanceOf(PRIMITIVES, PRIMITIVES.stringParameterizedType, null, vb, ObjectFlow.NO_FLOW);
        Expression or = newOrAppend(ivb, iva);
        Assert.assertEquals("(a instanceof java.lang.String or b instanceof java.lang.String)", or.toString());
        Expression iva2 = new InstanceOf(PRIMITIVES, PRIMITIVES.objectParameterizedType, null, va, ObjectFlow.NO_FLOW);
        Expression or2 = newOrAppend(iva, iva2);
        Assert.assertEquals("(a instanceof java.lang.Object or a instanceof java.lang.String)", or2.toString());
    }

    Map<Variable, Boolean> nullClauses(Expression v, Filter.FilterMode filterMode) {
        return Filter.filter(minimalEvaluationContext, v, filterMode, Filter.INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE).accepted()
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() instanceof Equals Equals && Equals.lhs == NullConstant.NULL_CONSTANT));
    }

    @Test
    public void testIsNull() {
        Expression v = new Equals(PRIMITIVES, a, NullConstant.NULL_CONSTANT, ObjectFlow.NO_FLOW);
        Assert.assertEquals("null == a", v.toString());
        Map<Variable, Boolean> nullClauses = nullClauses(v, Filter.FilterMode.ACCEPT);
        Assert.assertEquals(1, nullClauses.size());
        Assert.assertEquals(true, nullClauses.get(va));

        Expression v2 = new Equals(PRIMITIVES, b, NullConstant.NULL_CONSTANT, ObjectFlow.NO_FLOW);
        Assert.assertEquals("null == b", v2.toString());
        Map<Variable, Boolean> nullClauses2 = nullClauses(v2, Filter.FilterMode.ACCEPT);
        Assert.assertEquals(1, nullClauses2.size());
        Assert.assertEquals(true, nullClauses2.get(vb));

        Expression orValue = newOrAppend(v, negate(v2));
        Assert.assertEquals("(null == a or not (null == b))", orValue.toString());
        Map<Variable, Boolean> nullClausesAnd = nullClauses(orValue, Filter.FilterMode.REJECT);
        Assert.assertEquals(2, nullClausesAnd.size());
        Assert.assertEquals(true, nullClausesAnd.get(va));
        Assert.assertEquals(false, nullClausesAnd.get(vb));
    }

    @Test
    public void testIsNotNull() {
        Expression v = Negation.negate(minimalEvaluationContext, new Equals(PRIMITIVES, NullConstant.NULL_CONSTANT, a, ObjectFlow.NO_FLOW));
        Assert.assertEquals("not (null == a)", v.toString());
        Map<Variable, Boolean> nullClauses = nullClauses(v, Filter.FilterMode.REJECT);
        Assert.assertEquals(1, nullClauses.size());
        Assert.assertEquals(false, nullClauses.get(va));
    }

    public static final String EXPECTED = "((a or c) and (a or d) and (b or c) and (b or d))";
    public static final String EXPECTED2 = "((a or not (c)) and (a or d) and (not (b) or not (c)) and (not (b) or d))";

    @Test
    public void testCNF() {
        // (a && b) || (c && d)
        Expression or = newOrAppend(newAndAppend(a, b), newAndAppend(c, d));
        Assert.assertEquals(EXPECTED, or.toString());
        or = newOrAppend(newAndAppend(b, a), newAndAppend(d, c));
        Assert.assertEquals(EXPECTED, or.toString());
        or = newOrAppend(newAndAppend(d, c), newAndAppend(b, a));
        Assert.assertEquals(EXPECTED, or.toString());
    }

    @Test
    public void testCNFWithNot() {
        Expression notB = negate(b);
        Expression notC = negate(c);
        Expression or = newOrAppend(newAndAppend(a, notB), newAndAppend(notC, d));
        Assert.assertEquals(EXPECTED2, or.toString());
        or = newOrAppend(newAndAppend(notB, a), newAndAppend(d, notC));
        Assert.assertEquals(EXPECTED2, or.toString());
        or = newOrAppend(newAndAppend(d, notC), newAndAppend(notB, a));
        Assert.assertEquals(EXPECTED2, or.toString());
    }

    // (not ('a' == c (parameter 0)) and not ('b' == c (parameter 0)) and ('a' == c (parameter 0) or 'b' == c (parameter 0)))
    // not a and not b and (a or b)

    @Test
    public void testForSwitchStatement() {
        Expression v = newAndAppend(negate(a), negate(b), newOrAppend(a, b));
        Assert.assertEquals(FALSE, v);

        Expression cIsA = equals(new CharConstant(PRIMITIVES, 'a', ObjectFlow.NO_FLOW), c);
        Expression cIsABis = equals(new CharConstant(PRIMITIVES, 'a', ObjectFlow.NO_FLOW), c);
        Assert.assertEquals(cIsA, cIsABis);

        Expression cIsB = equals(new CharConstant(PRIMITIVES, 'b', ObjectFlow.NO_FLOW), c);

        Expression v2 = newAndAppend(negate(cIsA), negate(cIsB), newOrAppend(cIsA, cIsB));
        Assert.assertEquals(FALSE, v2);
    }

    @Test
    public void testCompare() {
        Expression aGt4 = GreaterThanZero.greater(minimalEvaluationContext, a, newInt(4), true);
        Assert.assertEquals("((-4) + a) >= 0", aGt4.toString());

        Expression n4ltB = GreaterThanZero.less(minimalEvaluationContext, newInt(4), b, false);
        Assert.assertEquals("((-5) + b) >= 0", n4ltB.toString());

        Expression n4lt8 = GreaterThanZero.less(minimalEvaluationContext, newInt(4), newInt(8), false);
        Assert.assertEquals(TRUE, n4lt8);
    }

    @Test
    public void testSumProduct() {
        Expression aa = Sum.sum(minimalEvaluationContext, a, a, ObjectFlow.NO_FLOW);
        Assert.assertEquals("2 * a", aa.toString());
        Expression a0 = Sum.sum(minimalEvaluationContext, a, newInt(0), ObjectFlow.NO_FLOW);
        Assert.assertEquals(a, a0);
        Expression aTimes0 = Product.product(minimalEvaluationContext, a, newInt(0), ObjectFlow.NO_FLOW);
        Assert.assertEquals(newInt(0), aTimes0);

        Expression a3a = Sum.sum(minimalEvaluationContext, a,
                Product.product(minimalEvaluationContext, newInt(3), a, ObjectFlow.NO_FLOW),
                ObjectFlow.NO_FLOW);
        Assert.assertEquals("4 * a", a3a.toString());
        Expression b2 = Product.product(minimalEvaluationContext, b, newInt(2), ObjectFlow.NO_FLOW);
        Expression b4 = Product.product(minimalEvaluationContext, newInt(4), b, ObjectFlow.NO_FLOW);
        Expression b4b2 = Sum.sum(minimalEvaluationContext, b4, b2, ObjectFlow.NO_FLOW);
        Assert.assertEquals("6 * b", b4b2.toString());
    }
}
