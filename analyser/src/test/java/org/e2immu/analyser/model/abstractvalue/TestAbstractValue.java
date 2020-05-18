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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.CharValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class TestAbstractValue {

    @BeforeClass
    public static void beforeClass() {
        Logger.activate(Logger.LogTarget.CNF);
    }

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

    static EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public int getIteration() {
            return 0;
        }

        @Override
        public DebugConfiguration getDebugConfiguration() {
            return new DebugConfiguration.Builder().build();
        }

        @Override
        public MethodInfo getCurrentMethod() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeInfo getCurrentType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvaluationContext childInSyncBlock(Value conditional, Runnable uponUsingConditional, boolean inSyncBlock, boolean guaranteedToBeReachedByParentStatement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvaluationContext child(Value conditional, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeContext getTypeContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createLocalVariableOrParameter(Variable variable, VariableProperty... initialProperties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void linkVariables(Variable variableFromExpression, Set<Variable> toBestCase, Set<Variable> toWorstCase) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Value currentValue(Variable variable) {
            return new VariableValue(this, variable, variable.name());
        }

        @Override
        public VariableValue newVariableValue(Variable variable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Value arrayVariableValue(Value array, Value indexValue, ParameterizedType parameterizedType, Set<Variable> dependencies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            return 0;
        }

        @Override
        public boolean equals(Variable variable, Variable other) {
            return variable.name().equals(other.name());
        }

        @Override
        public void merge(EvaluationContext child) {
            throw new UnsupportedOperationException();
        }
    };

    static final Variable va = createVariable("a");
    static final Variable vb = createVariable("b");
    static final Variable vc = createVariable("c");
    static final Variable vd = createVariable("d");
    static final VariableValue a = new VariableValue(minimalEvaluationContext, va, "a");
    static final VariableValue b = new VariableValue(minimalEvaluationContext, vb, "b");
    static final VariableValue c = new VariableValue(minimalEvaluationContext, vc, "c");
    static final VariableValue d = new VariableValue(minimalEvaluationContext, vd, "d");

    @Test
    public void test() {
        Value notA = NegatedValue.negate(a);
        Assert.assertEquals("not (a)", notA.toString());
        Value notA2 = NegatedValue.negate(a);
        Assert.assertEquals(notA, notA2);
        Assert.assertEquals(a, NegatedValue.negate(notA));

        Assert.assertEquals(a, new AndValue().append(a, a));
        Assert.assertEquals(notA, new AndValue().append(notA, notA));
        Assert.assertEquals(BoolValue.FALSE, new AndValue().append(a, notA));

        // A && A, !A && !A
        Assert.assertEquals(a, new AndValue().append(a, a));
        Assert.assertEquals(notA, new AndValue().append(notA, notA));
        // A && !A, !A && A
        Assert.assertEquals(BoolValue.FALSE, new AndValue().append(a, notA));
        Assert.assertEquals(BoolValue.FALSE, new AndValue().append(notA, a));

        // F || T
        Assert.assertEquals(BoolValue.TRUE, new OrValue().append(BoolValue.FALSE, BoolValue.TRUE));
        // A || A, !A || !A
        Assert.assertEquals(a, new OrValue().append(a, a));
        Assert.assertEquals(notA, new OrValue().append(notA, notA));
        // A || !A, !A || A
        Assert.assertEquals(BoolValue.TRUE, new OrValue().append(a, notA));
        Assert.assertEquals(BoolValue.TRUE, new OrValue().append(notA, a));
    }

    @Test
    public void testAndOfTrues() {
        Value v = new AndValue().append(BoolValue.TRUE, BoolValue.TRUE);
        Assert.assertEquals(BoolValue.TRUE, v);
    }

    @Test
    public void testMoreComplicatedAnd() {
        //D && A && !B && (!A || B) && C (the && C, D is there just for show)
        Value v = new AndValue().append(d, a, NegatedValue.negate(b), new OrValue().append(NegatedValue.negate(a), b), c);
        Assert.assertEquals(BoolValue.FALSE, v);
    }

    @Test
    public void testExpandAndInOr() {
        // A || (B && C)
        Value v = new OrValue().append(a, new AndValue().append(b, c));
        Assert.assertEquals("((a or b) and (a or c))", v.toString());
    }

    @Test
    public void testInstanceOf() {
        Value iva = new InstanceOfValue(va, Primitives.PRIMITIVES.stringParameterizedType);
        Assert.assertEquals("a instanceof java.lang.String", iva.toString());
        Value ivb = new InstanceOfValue(vb, Primitives.PRIMITIVES.stringParameterizedType);
        Value or = new OrValue().append(ivb, iva);
        Assert.assertEquals("(a instanceof java.lang.String or b instanceof java.lang.String)", or.toString());
        Value iva2 = new InstanceOfValue(va, Primitives.PRIMITIVES.objectParameterizedType);
        Value or2 = new OrValue().append(iva, iva2);
        Assert.assertEquals("(a instanceof java.lang.Object or a instanceof java.lang.String)", or2.toString());
    }

    @Test
    public void testIsNull() {
        Value v = new EqualsValue(a, NullValue.NULL_VALUE);
        Assert.assertEquals("null == a", v.toString());
        Map<Variable, Boolean> nullClauses = v.individualNullClauses();
        Assert.assertEquals(1, nullClauses.size());
        Assert.assertEquals(true, nullClauses.get(va));

        Value v2 = new EqualsValue(b, NullValue.NULL_VALUE);
        Assert.assertEquals("null == b", v2.toString());
        Map<Variable, Boolean> nullClauses2 = v2.individualNullClauses();
        Assert.assertEquals(1, nullClauses2.size());
        Assert.assertEquals(true, nullClauses2.get(vb));

        Value andValue = new AndValue().append(v, NegatedValue.negate(v2));
        Assert.assertEquals("(null == a and not (null == b))", andValue.toString());
        Map<Variable, Boolean> nullClausesAnd = andValue.individualNullClauses();
        Assert.assertEquals(2, nullClausesAnd.size());
        Assert.assertEquals(true, nullClausesAnd.get(va));
        Assert.assertEquals(false, nullClausesAnd.get(vb));
    }

    @Test
    public void testIsNotNull() {
        Value v = NegatedValue.negate(new EqualsValue(NullValue.NULL_VALUE, a));
        Assert.assertEquals("not (null == a)", v.toString());
        Map<Variable, Boolean> nullClauses = v.individualNullClauses();
        Assert.assertEquals(1, nullClauses.size());
        Assert.assertEquals(false, nullClauses.get(va));
    }

    public static final String EXPECTED = "((a or c) and (a or d) and (b or c) and (b or d))";
    public static final String EXPECTED2 = "((a or not (c)) and (a or d) and (not (b) or not (c)) and (not (b) or d))";

    @Test
    public void testCNF() {
        // (a && b) || (c && d)
        Value or = new OrValue().append(new AndValue().append(a, b), new AndValue().append(c, d));
        Assert.assertEquals(EXPECTED, or.toString());
        or = new OrValue().append(new AndValue().append(b, a), new AndValue().append(d, c));
        Assert.assertEquals(EXPECTED, or.toString());
        or = new OrValue().append(new AndValue().append(d, c), new AndValue().append(b, a));
        Assert.assertEquals(EXPECTED, or.toString());
    }

    @Test
    public void testCNFWithNot() {
        Value notB = NegatedValue.negate(b);
        Value notC = NegatedValue.negate(c);
        Value or = new OrValue().append(new AndValue().append(a, notB), new AndValue().append(notC, d));
        Assert.assertEquals(EXPECTED2, or.toString());
        or = new OrValue().append(new AndValue().append(notB, a), new AndValue().append(d, notC));
        Assert.assertEquals(EXPECTED2, or.toString());
        or = new OrValue().append(new AndValue().append(d, notC), new AndValue().append(notB, a));
        Assert.assertEquals(EXPECTED2, or.toString());
    }

    // (not ('a' == c (parameter 0)) and not ('b' == c (parameter 0)) and ('a' == c (parameter 0) or 'b' == c (parameter 0)))
    // not a and not b and (a or b)

    @Test
    public void testForSwitchStatement() {
        Value v = new AndValue().append(NegatedValue.negate(a), NegatedValue.negate(b), new OrValue().append(a, b));
        Assert.assertEquals(BoolValue.FALSE, v);

        Value cIsA = EqualsValue.equals(new CharValue('a'), c);
        Value cIsABis = EqualsValue.equals(new CharValue('a'), c);
        Assert.assertEquals(cIsA, cIsABis);

        Value cIsB = EqualsValue.equals(new CharValue('b'), c);

        Value v2 = new AndValue().append(NegatedValue.negate(cIsA), NegatedValue.negate(cIsB), new OrValue().append(cIsA, cIsB));
        Assert.assertEquals(BoolValue.FALSE, v2);
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
