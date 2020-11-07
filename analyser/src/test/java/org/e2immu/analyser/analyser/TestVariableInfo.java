/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class TestVariableInfo extends CommonVariableInfo {

    @BeforeClass
    public static void beforeClass() {
        Logger.activate();
    }

    @Test
    public void testNoneNoOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl vii = viB.merge(minimalEvaluationContext, null, false, List.of());
        Assert.assertSame(viB, vii);

        Assert.assertSame(four, viB.value.get());
        viB.mergeProperties(false, viB, List.of());
        Assert.assertEquals(MultiLevel.MUTABLE, viB.getProperty(VariableProperty.NOT_NULL));
    }

    @Test
    public void testNoneOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl overwritten = new VariableInfoImpl(viB.variable);
        try {
            viB.merge(minimalEvaluationContext, overwritten, true, List.of());
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // OK
        }
    }

    @Test
    public void testOneOverwrite() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(UnknownValue.EMPTY);
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.stateOnAssignment.set(x);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation:
        // int c = a;
        // try { ... c = b; } or synchronized(...) { c = b; }

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        VariableInfoImpl viC2 = viC.merge(minimalEvaluationContext, null, true, List.of(viB));
        Assert.assertSame(viC, viC2);

        Value res = viC.getValue();
        Assert.assertEquals("4", res.toString());
        viC.mergeProperties(true, viA, List.of(viB));
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, viC.getProperty(VariableProperty.NOT_NULL));
    }

    @Test
    public void testOneCisAIfXThenB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(UnknownValue.EMPTY);
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.stateOnAssignment.set(x);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        // situation:
        // boolean x = ...;
        // int c = a;
        // if(x) c = b;

        VariableInfoImpl viC = new VariableInfoImpl(viA);
        VariableInfoImpl viC2 = viC.merge(minimalEvaluationContext, null, false, List.of(viB));
        Assert.assertNotSame(viC, viC2);

        Value res = viC2.getValue();
        Assert.assertEquals("x?4:3", res.toString());

        viC2.mergeProperties(true, viA, List.of(viB));
        Assert.assertEquals(MultiLevel.MUTABLE, viC2.getProperty(VariableProperty.NOT_NULL));

        // in a second iteration, we may encounter:

        VariableInfoImpl viC3 = new VariableInfoImpl(viA);
        VariableInfoImpl viC4 = viC3.merge(minimalEvaluationContext, viC2, false, List.of(viB));
        Assert.assertSame(viC2, viC4);
    }


    @Test
    public void testOneIfXThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);
        ret.value.set(UnknownValue.RETURN_VALUE);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.stateOnAssignment.set(x);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation:
        // if(x) return b;

        VariableInfoImpl ret2 = ret.merge(minimalEvaluationContext, null, false, List.of(viB));
        Assert.assertNotSame(ret, ret2);

        Value value2 = ret2.getValue();
        Assert.assertEquals("x?4:<return value>", value2.toString());
        ret2.mergeProperties(false, ret, List.of(viB));
        Assert.assertEquals(MultiLevel.MUTABLE, ret2.getProperty(VariableProperty.NOT_NULL));

        // OK let's continue

        // situation:
        // if(x) return b;
        // return a;  (which has state added: not (x), so we effectively execute if(!x) return a;, and then merge)

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(NegatedValue.negate(minimalEvaluationContext, x));
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl ret3 = ret2.merge(minimalEvaluationContext, null, false, List.of(viA));
        Assert.assertNotSame(ret3, ret2);
        Assert.assertEquals("x?4:3", ret3.getValue().toString());

        ret3.mergeProperties(false, ret2, List.of(viA));
        Assert.assertEquals(MultiLevel.MUTABLE, ret3.getProperty(VariableProperty.NOT_NULL));
        // but this is not the correct, final value, but correction takes place in VariableInfoContainer
    }


    @Test
    public void testOneIfXThenReturnIfYThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.value.set(UnknownValue.RETURN_VALUE); // uni
        ret.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalIntVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        Value xEquals3 = EqualsValue.equals(minimalEvaluationContext, x, three, ObjectFlow.NO_FLOW);
        viB.stateOnAssignment.set(xEquals3);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation:
        // if(x==3) return b;

        VariableInfoImpl ret2 = ret.merge(minimalEvaluationContext, null, false, List.of(viB));
        Assert.assertNotSame(ret2, ret);
        Assert.assertEquals("3 == x?4:<return value>", ret2.getValue().toString());

        ret2.mergeProperties(false, ret, List.of(viB));
        Assert.assertEquals(MultiLevel.MUTABLE, ret2.getProperty(VariableProperty.NOT_NULL));

        // OK let's continue, bun with another if in between

        // situation:
        // if(x==3) return b;
        // if(x==4) return a;

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        Value xEquals4 = new AndValue(minimalEvaluationContext.getPrimitives()).append(minimalEvaluationContext,
                NegatedValue.negate(minimalEvaluationContext, xEquals3),
                EqualsValue.equals(minimalEvaluationContext, x, four, ObjectFlow.NO_FLOW));
        Assert.assertEquals("4 == x", xEquals4.toString());
        viA.stateOnAssignment.set(xEquals4);
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl ret3 = ret2.merge(minimalEvaluationContext, null, false, List.of(viA));
        Assert.assertNotSame(ret3, ret2);
        Assert.assertEquals("4 == x?3:3 == x?4:<return value>", ret3.getValue().toString());
        ret3.mergeProperties(false, ret2, List.of(viA));
        Assert.assertEquals(MultiLevel.MUTABLE, ret3.getProperty(VariableProperty.NOT_NULL));

        // situation:
        // if(x==3) return b;
        // if(x==4) return a;
        // return c;  (which has state added: not (x))

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.value.set(two);
        Value combinedState =
                new AndValue(minimalEvaluationContext.getPrimitives()).append(minimalEvaluationContext,
                        NegatedValue.negate(minimalEvaluationContext, xEquals3),
                        NegatedValue.negate(minimalEvaluationContext, xEquals4));
        viC.stateOnAssignment.set(combinedState);
        viC.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl ret4 = ret3.merge(minimalEvaluationContext, null, false, List.of(viC));
        Assert.assertNotSame(ret3, ret4);
        // IMPROVE actually the value should be 4 == x?3:3 == x?4:2
        Assert.assertEquals("(not (3 == x) and not (4 == x))?2:4 == x?3:3 == x?4:<return value>", ret4.getValue().toString());

        ret4.mergeProperties(false, ret3, List.of(viC));
        Assert.assertEquals(MultiLevel.MUTABLE, ret4.getProperty(VariableProperty.NOT_NULL));
        // NOTE: but this is not the correct, final value, but correction takes place in VariableInfoContainer
    }


    @Test
    public void testOneCisAIfUnclearThenB() {
        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(UnknownValue.EMPTY);
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.stateOnAssignment.set(UnknownValue.EMPTY); // no real idea what the condition is
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        // situation:
        // boolean x = ...;
        // int c = a;
        // if(some obscure condition) c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.value.set(new VariableValue(viA.variable));
        viC.stateOnAssignment.set(UnknownValue.EMPTY);
        VariableInfoImpl viC2 = viC.merge(minimalEvaluationContext, null, false, List.of(viB));
        Assert.assertNotSame(viA, viC2);
        Assert.assertEquals("c", viC2.getValue().toString());

        viC2.mergeProperties(false, viA, List.of(viB));
        Assert.assertEquals(MultiLevel.MUTABLE, viC2.getProperty(VariableProperty.NOT_NULL));
    }


    @Test
    public void testTwoOverwriteCisIfXThenAElseB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(x);
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.stateOnAssignment.set(NegatedValue.negate(minimalEvaluationContext, x));
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        // situation:
        // boolean x = ...;
        // int c;
        // if(x) c = a; else c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.merge(minimalEvaluationContext, viC, true, List.of(viA, viB));
        Assert.assertEquals("x?3:4", viC.getValue().toString());

        viC.mergeProperties(true, null, List.of(viA, viB));
        Assert.assertEquals(MultiLevel.MUTABLE, viC.getProperty(VariableProperty.NOT_NULL));
    }

    // slight variant, showing the strength of ConditionalValue's factory method
    @Test
    public void testTwoOverwriteCisIfXThenAElseA() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(x);
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(three);
        viB.stateOnAssignment.set(NegatedValue.negate(minimalEvaluationContext, x));
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation:
        // boolean x = ...;
        // int c;
        // if(x) c = a; else c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        VariableInfoImpl viC2 = viC.merge(minimalEvaluationContext, viC, true, List.of(viA, viB));
        Assert.assertSame(viC2, viC);

        Value res = viC.getValue();
        Assert.assertEquals("3", res.toString());

        viC.mergeProperties(true, null, List.of(viA, viB));
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, viC.getProperty(VariableProperty.NOT_NULL));
    }
}
