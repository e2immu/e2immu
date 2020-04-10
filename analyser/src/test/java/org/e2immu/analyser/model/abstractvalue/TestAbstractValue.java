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

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.junit.Assert;
import org.junit.Test;

public class TestAbstractValue {

    static Variable createVariable(String name) {
        return new Variable() {
            @Override
            public ParameterizedType parameterizedType() {
                return null;
            }

            @Override
            public ParameterizedType concreteReturnType() {
                return null;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String detailedString() {
                return name;
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public SideEffect sideEffect(SideEffectContext sideEffectContext) {
                return null;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    static final Variable va = createVariable("a");
    static final Variable vb = createVariable("b");
    static final Variable vc = createVariable("c");
    static final Variable vd = createVariable("d");
    static final VariableValue a = new VariableValue(va);
    static final VariableValue b = new VariableValue(vb);
    static final VariableValue c = new VariableValue(vc);
    static final VariableValue d = new VariableValue(vd);

    @Test
    public void test() {
        Value notA = NegatedValue.negate(a);
        Assert.assertEquals("not a", notA.toString());
        Value notA2 = NegatedValue.negate(a);
        Assert.assertEquals(notA, notA2);
        Assert.assertEquals(a, NegatedValue.negate(notA));

        Assert.assertEquals(a, AndValue.and(a, a));
        Assert.assertEquals(notA, AndValue.and(notA, notA));
        Assert.assertEquals(BoolValue.FALSE, AndValue.and(a, notA));

        Assert.assertEquals(a, AndValue.and(a, a));
        Assert.assertEquals(notA, AndValue.and(notA, notA));
        Assert.assertEquals(BoolValue.FALSE, AndValue.and(a, notA));

        Assert.assertEquals(BoolValue.TRUE, OrValue.or(BoolValue.FALSE, BoolValue.TRUE));
    }

    @Test
    public void testInstanceOf() {
        Value iva = new InstanceOfValue(va, Primitives.PRIMITIVES.stringParameterizedType);
        Assert.assertEquals("a instanceof java.lang.String", iva.toString());
        Value ivb = new InstanceOfValue(vb, Primitives.PRIMITIVES.stringParameterizedType);
        Value or = OrValue.or(ivb, iva);
        Assert.assertEquals("(a instanceof java.lang.String or b instanceof java.lang.String)", or.toString());
        Value iva2 = new InstanceOfValue(va, Primitives.PRIMITIVES.objectParameterizedType);
        Value or2 = OrValue.or(iva, iva2);
        Assert.assertEquals("(a instanceof java.lang.Object or a instanceof java.lang.String)", or2.toString());
    }

    @Test
    public void testIsNull() {
        Value v = new EqualsValue(a, NullValue.NULL_VALUE);
        Assert.assertEquals("null == a", v.toString());
        Assert.assertEquals(va, v.variableIsNull().orElseThrow());
    }

    @Test
    public void testIsNotNull() {
        Value v = NegatedValue.negate(new EqualsValue(NullValue.NULL_VALUE, a));
        Assert.assertEquals("null != a", v.toString());
        Assert.assertEquals(va, v.variableIsNotNull().orElseThrow());
    }

    public static final String EXPECTED = "(((a or c) and (a or d)) and ((b or c) and (b or d)))";
    public static final String EXPECTED2 = "(((a or not c) and (a or d)) and ((not b or not c) and (not b or d)))";

    @Test
    public void testCNF() {
        Value or = OrValue.or(AndValue.and(a, b), AndValue.and(c, d));
        Assert.assertEquals(EXPECTED, or.toString());
        or = OrValue.or(AndValue.and(b, a), AndValue.and(d, c));
        Assert.assertEquals(EXPECTED, or.toString());
        or = OrValue.or(AndValue.and(d, c), AndValue.and(b, a));
        Assert.assertEquals(EXPECTED, or.toString());
    }

    @Test
    public void testCNFWithNot() {
        Value notB = NegatedValue.negate(b);
        Value notC = NegatedValue.negate(c);
        Value or = OrValue.or(AndValue.and(a, notB), AndValue.and(notC, d));
        Assert.assertEquals(EXPECTED2, or.toString());
        or = OrValue.or(AndValue.and(notB, a), AndValue.and(d, notC));
        Assert.assertEquals(EXPECTED2, or.toString());
        or = OrValue.or(AndValue.and(d, notC), AndValue.and(notB, a));
        Assert.assertEquals(EXPECTED2, or.toString());
    }

    @Test
    public void testCompare() {
        Value aGt4 = GreaterThanZeroValue.greater(a, new IntValue(4), true);
        Assert.assertEquals("((-4) + a) >= 0", aGt4.toString());

        Value n4ltB = GreaterThanZeroValue.less(new IntValue(4), b, false);
        Assert.assertEquals("((-4) + b) > 0", n4ltB.toString());

        Value n4lt8 = GreaterThanZeroValue.less(new IntValue(4), new IntValue(8), false);
        Assert.assertEquals(BoolValue.TRUE, n4lt8);
    }

    @Test
    public void testSumProduct() {
        Value aa = SumValue.sum(a, a);
        Assert.assertEquals("2 * a", aa.toString());
        Value a0 = SumValue.sum(a, IntValue.ZERO_VALUE);
        Assert.assertEquals(a, a0);
        Value aTimes0 = ProductValue.product(a, IntValue.ZERO_VALUE);
        Assert.assertEquals(IntValue.ZERO_VALUE, aTimes0);

        Value a3a = SumValue.sum(a, ProductValue.product(new IntValue(3), a));
        Assert.assertEquals("4 * a", a3a.toString());

        Value b4b2 = SumValue.sum(ProductValue.product(new IntValue(4), b), ProductValue.product(b, new IntValue(2)));
        Assert.assertEquals("6 * b", b4b2.toString());
    }
}
