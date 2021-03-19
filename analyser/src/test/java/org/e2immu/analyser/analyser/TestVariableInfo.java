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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestVariableInfo extends CommonVariableInfo {

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    @Test
    public void testNoneNoOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl vii = viB.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, List.of());

        assertSame(four, vii.getValue());
        vii.mergeProperties(false, viB, List.of());
        assertEquals(MultiLevel.MUTABLE, vii.getProperty(VariableProperty.CONTEXT_NOT_NULL));
    }

    @Test
    public void testNoneOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl overwritten = new VariableInfoImpl(viB.variable());
        try {
            overwritten.mergeIntoMe(minimalEvaluationContext, TRUE, true, viB, List.of());
            fail();
        } catch (UnsupportedOperationException e) {
            // OK
        }
    }

    @Test
    public void testOneOverwrite() {
        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation:
        // int c = a;
        // try { ... c = b; } or synchronized(...) { c = b; }

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        List<StatementAnalysis.ConditionAndVariableInfo> list = List.of(new StatementAnalysis.ConditionAndVariableInfo(TRUE, viB));
        VariableInfoImpl viC2 = viC.mergeIntoNewObject(minimalEvaluationContext, TRUE, true, list);

        Expression res = viC2.getValue();
        assertEquals("4", res.toString());
        viC2.mergeProperties(true, viA, list);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, viC2.getProperty(VariableProperty.CONTEXT_NOT_NULL));
    }

    @Test
    public void testOneCisAIfXThenB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        // situation: boolean x = ...; int c = a; if(x) c = b;
        List<StatementAnalysis.ConditionAndVariableInfo> xViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viB));

        VariableInfoImpl viC = viA.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, xViB);
        assertNotSame(viA, viC);

        Expression res = viC.getValue();
        assertEquals("instance type boolean?4:3", res.toString());

        viC.mergeProperties(true, viA, xViB);
        assertEquals(MultiLevel.MUTABLE, viC.getProperty(VariableProperty.CONTEXT_NOT_NULL));

        // in a second iteration, we may encounter:
        viC.mergeIntoMe(minimalEvaluationContext, TRUE, false, viA, xViB);
        // this should execute without raising exceptions
    }


    @Test
    public void testOneIfXThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);
        ret.setValue(new UnknownExpression(primitives.booleanParameterizedType, UnknownExpression.RETURN_VALUE), false);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation: if(x) return b;
        List<StatementAnalysis.ConditionAndVariableInfo> xViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viB));

        VariableInfoImpl ret2 = ret.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, xViB);
        assertNotSame(ret, ret2);

        Expression value2 = ret2.getValue();
        assertEquals("instance type boolean?4:<return value:boolean>", value2.debugOutput());
        ret2.mergeProperties(false, ret, xViB);
        assertEquals(MultiLevel.MUTABLE, ret2.getProperty(VariableProperty.CONTEXT_NOT_NULL));

        // OK let's continue
        // situation:  if(x) return b; return a;

        Expression state = Negation.negate(minimalEvaluationContext, x);

        // this is not done in the merge, but in the level 3 evaluation of the return value

        Expression ret3 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext,
                state, three, value2, ObjectFlow.NO_FLOW).getExpression();

        assertEquals("instance type boolean?4:3", ret3.toString());
    }


    @Test
    public void testOneIfXThenReturnIfYThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.setValue(new UnknownExpression(primitives.booleanParameterizedType, UnknownExpression.RETURN_VALUE), false);
        ret.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalIntVar("x"));
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        Expression xEquals3 = Equals.equals(minimalEvaluationContext, x, three, ObjectFlow.NO_FLOW);
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation: if(x==3) return b;
        List<StatementAnalysis.ConditionAndVariableInfo> x3ViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(xEquals3, viB));

        VariableInfoImpl ret2 = ret.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, x3ViB);
        assertNotSame(ret2, ret);
        assertEquals("3==instance type int?4:<return value:boolean>", ret2.getValue().debugOutput());

        ret2.mergeProperties(false, ret, x3ViB);
        assertEquals(MultiLevel.MUTABLE, ret2.getProperty(VariableProperty.CONTEXT_NOT_NULL));

        // OK let's continue, but with another if in between

        // situation: if(x==3) return b; if(x==4) return a;

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        Expression xEquals4 = Equals.equals(minimalEvaluationContext, x, four, ObjectFlow.NO_FLOW);
        assertEquals("4==instance type int", xEquals4.debugOutput());
        viA.setValue(three, false);
        viA.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        Expression state = Negation.negate(minimalEvaluationContext, xEquals3);
        List<StatementAnalysis.ConditionAndVariableInfo> x4ViA = List.of(new StatementAnalysis.ConditionAndVariableInfo(xEquals4, viA));

        VariableInfoImpl ret3 = ret2.mergeIntoNewObject(minimalEvaluationContext, state, false, x4ViA);
        assertNotSame(ret3, ret2);
        assertEquals("4==instance type int?3:3==instance type int?4:<return value:boolean>",
                ret3.getValue().debugOutput());
        ret3.mergeProperties(false, ret2, x4ViA);
        assertEquals(MultiLevel.MUTABLE, ret3.getProperty(VariableProperty.CONTEXT_NOT_NULL));

        // situation:
        // if(x==3) return b;
        // if(x==4) return a;
        // return c;  (which has state added: not (x))

        Expression combinedState =
                new And(minimalEvaluationContext.getPrimitives()).append(minimalEvaluationContext,
                        Negation.negate(minimalEvaluationContext, xEquals3),
                        Negation.negate(minimalEvaluationContext, xEquals4));

        Expression ret4 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext,
                combinedState, two, ret3.getValue(), ObjectFlow.NO_FLOW).getExpression();

        // IMPROVE actually the value should be 4 == x?3:3 == x?4:2
        assertEquals("3!=instance type int&&4!=instance type int?2:4==instance type int?3" +
                ":3==instance type int?4:<return value:boolean>", ret4.debugOutput());
    }


    @Test
    public void testOneCisAIfUnclearThenB() {
        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        // situation: boolean x = ...; int c = a; if(some obscure condition) c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.setValue(NewObject.forTesting(primitives, viA.variable().parameterizedType()), false);

        Expression unknown = new UnknownExpression(primitives.booleanParameterizedType, "no idea");
        List<StatementAnalysis.ConditionAndVariableInfo> uViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(unknown, viB));

        VariableInfoImpl viC2 = viC.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, uViB);
        assertNotSame(viA, viC2);
        assertEquals("<no idea>?4:instance type int", viC2.getValue().toString());

        viC2.mergeProperties(false, viA, uViB);
        assertEquals(MultiLevel.MUTABLE, viC2.getProperty(VariableProperty.CONTEXT_NOT_NULL));
    }


    @Test
    public void testTwoOverwriteCisIfXThenAElseB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.MUTABLE);

        // situation: boolean x = ...; int c; if(x) c = a; else c = b;

        List<StatementAnalysis.ConditionAndVariableInfo> list = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viA),
                new StatementAnalysis.ConditionAndVariableInfo(Negation.negate(minimalEvaluationContext, x), viB));

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.mergeIntoMe(minimalEvaluationContext, TRUE, true, viC, list);
        assertEquals("instance type boolean?3:4", viC.getValue().toString());

        viC.mergeProperties(true, null, list);
        assertEquals(MultiLevel.MUTABLE, viC.getProperty(VariableProperty.CONTEXT_NOT_NULL));
    }

    // slight variant, showing the strength of ConditionalValue's factory method
    @Test
    public void testTwoOverwriteCisIfXThenAElseA() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar("x"));
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(three, false);
        viB.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);

        // situation: boolean x = ...; int c; if(x) c = a; else c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        List<StatementAnalysis.ConditionAndVariableInfo> list = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viA),
                new StatementAnalysis.ConditionAndVariableInfo(Negation.negate(minimalEvaluationContext, x), viB));

        viC.mergeIntoMe(minimalEvaluationContext, TRUE, true, viC, list);

        Expression res = viC.getValue();
        assertEquals("3", res.toString());

        viC.mergeProperties(true, null, list);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, viC.getProperty(VariableProperty.CONTEXT_NOT_NULL));
    }
}
