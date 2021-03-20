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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.e2immu.analyser.analyser.VariableProperty.IDENTITY;
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
        viB.setProperty(IDENTITY, Level.TRUE);

        VariableInfoImpl vii = viB.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, List.of());

        assertSame(four, vii.getValue());
        vii.mergeProperties(false, viB, List.of());
        assertEquals(Level.TRUE, vii.getProperty(IDENTITY));
    }

    @Test
    public void testNoneOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.FALSE);

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
        viA.setProperty(IDENTITY, Level.TRUE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.FALSE);

        // situation:
        // int c = a;
        // try { ... c = b; } or synchronized(...) { c = b; }

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        List<StatementAnalysis.ConditionAndVariableInfo> list = List.of(new StatementAnalysis.ConditionAndVariableInfo(TRUE, viB));
        VariableInfoImpl viC2 = viC.mergeIntoNewObject(minimalEvaluationContext, TRUE, true, list);

        Expression res = viC2.getValue();
        assertEquals("4", res.toString());
        viC2.mergeProperties(true, viA, list);
        assertEquals(Level.FALSE, viC2.getProperty(IDENTITY));
    }

    @Test
    public void testOneCisAIfXThenB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(IDENTITY, Level.TRUE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.FALSE);

        // situation: boolean x = ...; int c = a; if(x) c = b;
        List<StatementAnalysis.ConditionAndVariableInfo> xViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viB));

        VariableInfoImpl viC = viA.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, xViB);
        assertNotSame(viA, viC);

        Expression res = viC.getValue();
        assertEquals("instance type boolean?4:3", res.toString());

        viC.mergeProperties(true, viA, xViB);
        assertEquals(Level.FALSE, viC.getProperty(IDENTITY));

        // in a second iteration, we may encounter:
        viC.mergeIntoMe(minimalEvaluationContext, TRUE, false, viA, xViB);
        // this should execute without raising exceptions
    }


    @Test
    public void testOneIfXThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.setProperty(IDENTITY, Level.FALSE);
        ret.setValue(new UnknownExpression(primitives.booleanParameterizedType, UnknownExpression.RETURN_VALUE), false);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.TRUE);

        // situation: if(x) return b;
        List<StatementAnalysis.ConditionAndVariableInfo> xViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viB));

        VariableInfoImpl ret2 = ret.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, xViB);
        assertNotSame(ret, ret2);

        Expression value2 = ret2.getValue();
        assertEquals("instance type boolean?4:<return value:boolean>", value2.debugOutput());
        ret2.mergeProperties(false, ret, xViB);
        assertEquals(Level.FALSE, ret2.getProperty(IDENTITY));

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
        ret.setProperty(IDENTITY, Level.FALSE);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalIntVar("x"));
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        Expression xEquals3 = Equals.equals(minimalEvaluationContext, x, three, ObjectFlow.NO_FLOW);
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.TRUE);

        // situation: if(x==3) return b;
        List<StatementAnalysis.ConditionAndVariableInfo> x3ViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(xEquals3, viB));

        VariableInfoImpl ret2 = ret.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, x3ViB);
        assertNotSame(ret2, ret);
        assertEquals("3==instance type int?4:<return value:boolean>", ret2.getValue().debugOutput());

        ret2.mergeProperties(false, ret, x3ViB);
        assertEquals(Level.FALSE, ret2.getProperty(IDENTITY));

        // OK let's continue, but with another if in between

        // situation: if(x==3) return b; if(x==4) return a;

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        Expression xEquals4 = Equals.equals(minimalEvaluationContext, x, four, ObjectFlow.NO_FLOW);
        assertEquals("4==instance type int", xEquals4.debugOutput());
        viA.setValue(three, false);
        viA.setProperty(IDENTITY, Level.TRUE);

        Expression state = Negation.negate(minimalEvaluationContext, xEquals3);
        List<StatementAnalysis.ConditionAndVariableInfo> x4ViA = List.of(new StatementAnalysis.ConditionAndVariableInfo(xEquals4, viA));

        VariableInfoImpl ret3 = ret2.mergeIntoNewObject(minimalEvaluationContext, state, false, x4ViA);
        assertNotSame(ret3, ret2);
        assertEquals("4==instance type int?3:3==instance type int?4:<return value:boolean>",
                ret3.getValue().debugOutput());
        ret3.mergeProperties(false, ret2, x4ViA);
        assertEquals(Level.FALSE, ret3.getProperty(IDENTITY));

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
        viA.setProperty(IDENTITY, Level.TRUE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.FALSE);

        // situation: boolean x = ...; int c = a; if(some obscure condition) c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.setValue(NewObject.forTesting(primitives, viA.variable().parameterizedType()), false);

        Expression unknown = new UnknownExpression(primitives.booleanParameterizedType, "no idea");
        List<StatementAnalysis.ConditionAndVariableInfo> uViB = List.of(new StatementAnalysis.ConditionAndVariableInfo(unknown, viB));

        VariableInfoImpl viC2 = viC.mergeIntoNewObject(minimalEvaluationContext, TRUE, false, uViB);
        assertNotSame(viA, viC2);
        assertEquals("<no idea>?4:instance type int", viC2.getValue().toString());

        viC2.mergeProperties(false, viA, uViB);
        assertEquals(Level.FALSE, viC2.getProperty(IDENTITY));
    }


    @Test
    public void testTwoOverwriteCisIfXThenAElseB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(IDENTITY, Level.TRUE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four, false);
        viB.setProperty(IDENTITY, Level.FALSE);

        // situation: boolean x = ...; int c; if(x) c = a; else c = b;

        List<StatementAnalysis.ConditionAndVariableInfo> list = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viA),
                new StatementAnalysis.ConditionAndVariableInfo(Negation.negate(minimalEvaluationContext, x), viB));

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.mergeIntoMe(minimalEvaluationContext, TRUE, true, viC, list);
        assertEquals("instance type boolean?3:4", viC.getValue().toString());

        viC.mergeProperties(true, new VariableInfoImpl(viA.variable()), list);
        assertEquals(Level.FALSE, viC.getProperty(IDENTITY));
    }

    // slight variant, showing the strength of ConditionalValue's factory method
    @Test
    public void testTwoOverwriteCisIfXThenAElseA() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = NewObject.forTesting(primitives, viX.variable().parameterizedType());
        viX.setValue(x, false);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three, false);
        viA.setProperty(IDENTITY, Level.TRUE);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(three, false);
        viB.setProperty(IDENTITY, Level.TRUE);

        // situation: boolean x = ...; int c; if(x) c = a; else c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        List<StatementAnalysis.ConditionAndVariableInfo> list = List.of(new StatementAnalysis.ConditionAndVariableInfo(x, viA),
                new StatementAnalysis.ConditionAndVariableInfo(Negation.negate(minimalEvaluationContext, x), viB));

        viC.mergeIntoMe(minimalEvaluationContext, TRUE, true, viC, list);

        Expression res = viC.getValue();
        assertEquals("3", res.toString());

        viC.mergeProperties(true, new VariableInfoImpl(viA.variable()), list);
        assertEquals(Level.TRUE, viC.getProperty(IDENTITY));
    }
}
