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

import org.e2immu.analyser.model.Level;
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

        VariableInfoImpl overwritten = new VariableInfoImpl(viB.variable);
        overwritten.merge(minimalEvaluationContext, viB, false, List.of());

        Assert.assertSame(four, overwritten.value.get());
        Assert.assertEquals(MultiLevel.MUTABLE, overwritten.getProperty(VariableProperty.NOT_NULL));
    }

    @Test
    public void testNoneOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl overwritten = new VariableInfoImpl(viB.variable);
        try {
            overwritten.merge(minimalEvaluationContext, viB, true, List.of());
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
        viC.merge(minimalEvaluationContext, viA, true, List.of(viB));

        Value res = viC.getValue();
        Assert.assertEquals("4", res.toString());
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

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.merge(minimalEvaluationContext, viA, false, List.of(viB));

        Value res = viC.getValue();
        Assert.assertEquals("x?4:3", res.toString());
        Assert.assertEquals(MultiLevel.MUTABLE, viC.getProperty(VariableProperty.NOT_NULL));
    }


    @Test
    public void testOneIfXThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.value.set(UnknownValue.RETURN_VALUE); // uni

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Value x = new VariableValue(viX.variable);
        viX.value.set(x);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.value.set(four);
        viB.stateOnAssignment.set(x);
        viB.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation:
        // if(x) return b;

        VariableInfoImpl ret2 = new VariableInfoImpl(retVar);
        ret2.merge(minimalEvaluationContext, ret, false, List.of(viB));

        Value res = ret2.getValue();
        Assert.assertEquals("x?4:<return value>", res.toString());
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret2.getProperty(VariableProperty.NOT_NULL));

        // OK let's continue

        // situation:
        // if(x) return b;
        // return a;  (which has state added: not (x))

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.value.set(three);
        viA.stateOnAssignment.set(NegatedValue.negate(minimalEvaluationContext, x));
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl ret3 = new VariableInfoImpl(retVar);
        ret3.merge(minimalEvaluationContext, ret2, false, List.of(viA));
        Assert.assertEquals("x?4:3", ret3.getValue().toString());

        // TODO how can we fix this??
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret3.getProperty(VariableProperty.NOT_NULL));
    }


    @Test
    public void testOneIfXThenReturnIfYThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.value.set(UnknownValue.RETURN_VALUE); // uni

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

        VariableInfoImpl ret2 = new VariableInfoImpl(retVar);
        ret2.merge(minimalEvaluationContext, ret, false, List.of(viB));

        Value res = ret2.getValue();
        Assert.assertEquals("3 == x?4:<return value>", res.toString());
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret2.getProperty(VariableProperty.NOT_NULL));

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

        VariableInfoImpl ret3 = new VariableInfoImpl(retVar);
        ret3.merge(minimalEvaluationContext, ret2, false, List.of(viA));
        Assert.assertEquals("4 == x?3:3 == x?4:<return value>", ret3.getValue().toString());

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

        VariableInfoImpl ret4 = new VariableInfoImpl(retVar);
        ret4.merge(minimalEvaluationContext, ret3, false, List.of(viC));

        // IMPROVE actually the value should be 4 == x?3:3 == x?4:2
        Assert.assertEquals("(not (3 == x) and not (4 == x))?2:4 == x?3:3 == x?4:<return value>", ret4.getValue().toString());

        // TODO how can we fix this??
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret4.getProperty(VariableProperty.NOT_NULL));
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
        viC.merge(minimalEvaluationContext, viA, false, List.of(viB));

        Value res = viC.getValue();
        Assert.assertEquals("c", res.toString());
        Assert.assertEquals(MultiLevel.MUTABLE, viC.getProperty(VariableProperty.NOT_NULL));
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

        Value res = viC.getValue();
        Assert.assertEquals("x?3:4", res.toString());
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
        viC.merge(minimalEvaluationContext, viC, true, List.of(viA, viB));

        Value res = viC.getValue();
        Assert.assertEquals("3", res.toString());
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, viC.getProperty(VariableProperty.NOT_NULL));
    }
}
