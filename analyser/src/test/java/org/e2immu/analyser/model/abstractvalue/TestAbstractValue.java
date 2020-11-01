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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.CharValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

public class TestAbstractValue extends CommonAbstractValue {

    @Test
    public void test() {
        Value notA = NegatedValue.negate(minimalEvaluationContext, a);
        Assert.assertEquals("not (a)", notA.toString());
        Value notA2 = NegatedValue.negate(minimalEvaluationContext, a);
        Assert.assertEquals(notA, notA2);
        Assert.assertEquals(a, NegatedValue.negate(minimalEvaluationContext, notA));

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
        Value v = newAndAppend(TRUE, TRUE);
        Assert.assertEquals(TRUE, v);
    }

    @Test
    public void testMoreComplicatedAnd() {
        Value aAndAOrB = newAndAppend(a, newOrAppend(a, b));
        Assert.assertEquals(a, aAndAOrB);

        Value aAndNotAOrB = newAndAppend(a, newOrAppend(NegatedValue.negate(minimalEvaluationContext, a), b));
        Assert.assertEquals("(a and b)", aAndNotAOrB.toString());

        //D && A && !B && (!A || B) && C (the && C, D is there just for show)
        Value v = newAndAppend(d, a, negate(b), newOrAppend(negate(a), b), c);
        Assert.assertEquals(FALSE, v);
    }

    @Test
    public void testExpandAndInOr() {
        // A || (B && C)
        Value v = newOrAppend(a, newAndAppend(b, c));
        Assert.assertEquals("((a or b) and (a or c))", v.toString());
    }

    @Test
    public void testInstanceOf() {
        Value iva = new InstanceOfValue(PRIMITIVES, va, PRIMITIVES.stringParameterizedType, ObjectFlow.NO_FLOW);
        Assert.assertEquals("a instanceof java.lang.String", iva.toString());
        Value ivb = new InstanceOfValue(PRIMITIVES, vb, PRIMITIVES.stringParameterizedType, ObjectFlow.NO_FLOW);
        Value or = newOrAppend(ivb, iva);
        Assert.assertEquals("(a instanceof java.lang.String or b instanceof java.lang.String)", or.toString());
        Value iva2 = new InstanceOfValue(PRIMITIVES, va, PRIMITIVES.objectParameterizedType, ObjectFlow.NO_FLOW);
        Value or2 = newOrAppend(iva, iva2);
        Assert.assertEquals("(a instanceof java.lang.Object or a instanceof java.lang.String)", or2.toString());
    }

    Map<Variable, Boolean> nullClauses(Value v, Value.FilterMode filterMode) {
        return v.filter(minimalEvaluationContext, filterMode, Value::isIndividualNullOrNotNullClause).accepted
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == NullValue.NULL_VALUE));
    }

    @Test
    public void testIsNull() {
        Value v = new EqualsValue(PRIMITIVES, a, NullValue.NULL_VALUE, ObjectFlow.NO_FLOW);
        Assert.assertEquals("null == a", v.toString());
        Map<Variable, Boolean> nullClauses = nullClauses(v, Value.FilterMode.ACCEPT);
        Assert.assertEquals(1, nullClauses.size());
        Assert.assertEquals(true, nullClauses.get(va));

        Value v2 = new EqualsValue(PRIMITIVES, b, NullValue.NULL_VALUE, ObjectFlow.NO_FLOW);
        Assert.assertEquals("null == b", v2.toString());
        Map<Variable, Boolean> nullClauses2 = nullClauses(v2, Value.FilterMode.ACCEPT);
        Assert.assertEquals(1, nullClauses2.size());
        Assert.assertEquals(true, nullClauses2.get(vb));

        Value orValue = newOrAppend(v, negate(v2));
        Assert.assertEquals("(null == a or not (null == b))", orValue.toString());
        Map<Variable, Boolean> nullClausesAnd = nullClauses(orValue, Value.FilterMode.REJECT);
        Assert.assertEquals(2, nullClausesAnd.size());
        Assert.assertEquals(true, nullClausesAnd.get(va));
        Assert.assertEquals(false, nullClausesAnd.get(vb));
    }

    @Test
    public void testIsNotNull() {
        Value v = NegatedValue.negate(minimalEvaluationContext, new EqualsValue(PRIMITIVES, NullValue.NULL_VALUE, a, ObjectFlow.NO_FLOW));
        Assert.assertEquals("not (null == a)", v.toString());
        Map<Variable, Boolean> nullClauses = nullClauses(v, Value.FilterMode.REJECT);
        Assert.assertEquals(1, nullClauses.size());
        Assert.assertEquals(false, nullClauses.get(va));
    }

    public static final String EXPECTED = "((a or c) and (a or d) and (b or c) and (b or d))";
    public static final String EXPECTED2 = "((a or not (c)) and (a or d) and (not (b) or not (c)) and (not (b) or d))";

    @Test
    public void testCNF() {
        // (a && b) || (c && d)
        Value or = newOrAppend(newAndAppend(a, b), newAndAppend(c, d));
        Assert.assertEquals(EXPECTED, or.toString());
        or = newOrAppend(newAndAppend(b, a), newAndAppend(d, c));
        Assert.assertEquals(EXPECTED, or.toString());
        or = newOrAppend(newAndAppend(d, c), newAndAppend(b, a));
        Assert.assertEquals(EXPECTED, or.toString());
    }

    @Test
    public void testCNFWithNot() {
        Value notB = negate(b);
        Value notC = negate(c);
        Value or = newOrAppend(newAndAppend(a, notB), newAndAppend(notC, d));
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
        Value v = newAndAppend(negate(a), negate(b), newOrAppend(a, b));
        Assert.assertEquals(FALSE, v);

        Value cIsA = equals(new CharValue(PRIMITIVES, 'a', ObjectFlow.NO_FLOW), c);
        Value cIsABis = equals(new CharValue(PRIMITIVES, 'a', ObjectFlow.NO_FLOW), c);
        Assert.assertEquals(cIsA, cIsABis);

        Value cIsB = equals(new CharValue(PRIMITIVES, 'b', ObjectFlow.NO_FLOW), c);

        Value v2 = newAndAppend(negate(cIsA), negate(cIsB), newOrAppend(cIsA, cIsB));
        Assert.assertEquals(FALSE, v2);
    }

    @Test
    public void testCompare() {
        Value aGt4 = GreaterThanZeroValue.greater(minimalEvaluationContext, a, newInt(4), true);
        Assert.assertEquals("((-4) + a) >= 0", aGt4.toString());

        Value n4ltB = GreaterThanZeroValue.less(minimalEvaluationContext, newInt(4), b, false);
        Assert.assertEquals("((-5) + b) >= 0", n4ltB.toString());

        Value n4lt8 = GreaterThanZeroValue.less(minimalEvaluationContext, newInt(4), newInt(8), false);
        Assert.assertEquals(TRUE, n4lt8);
    }

    @Test
    public void testSumProduct() {
        Value aa = SumValue.sum(minimalEvaluationContext, a, a, ObjectFlow.NO_FLOW);
        Assert.assertEquals("2 * a", aa.toString());
        Value a0 = SumValue.sum(minimalEvaluationContext, a, newInt(0), ObjectFlow.NO_FLOW);
        Assert.assertEquals(a, a0);
        Value aTimes0 = ProductValue.product(minimalEvaluationContext, a, newInt(0), ObjectFlow.NO_FLOW);
        Assert.assertEquals(newInt(0), aTimes0);

        Value a3a = SumValue.sum(minimalEvaluationContext, a,
                ProductValue.product(minimalEvaluationContext, newInt(3), a, ObjectFlow.NO_FLOW),
                ObjectFlow.NO_FLOW);
        Assert.assertEquals("4 * a", a3a.toString());
        Value b2 = ProductValue.product(minimalEvaluationContext, b, newInt(2), ObjectFlow.NO_FLOW);
        Value b4 = ProductValue.product(minimalEvaluationContext, newInt(4), b, ObjectFlow.NO_FLOW);
        Value b4b2 = SumValue.sum(minimalEvaluationContext, b4, b2, ObjectFlow.NO_FLOW);
        Assert.assertEquals("6 * b", b4b2.toString());
    }
}
