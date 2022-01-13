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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.CommonVariableInfo;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.e2immu.analyser.analyser.Property.CONTAINER;
import static org.e2immu.analyser.analyser.Property.IDENTITY;
import static org.junit.jupiter.api.Assertions.*;

public class TestVariableInfo extends CommonVariableInfo {

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    @Test
    public void testNoneNoOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(CONTAINER, DV.TRUE_DV);

        VariableInfoImpl vii = new MergeHelper(minimalEvaluationContext, viB).mergeIntoNewObject(TRUE, TRUE, false, List.of());

        assertSame(four, vii.getValue());
        new MergeHelper(minimalEvaluationContext, vii)
                .mergePropertiesIgnoreValue(false, viB, List.of());
        assertEquals(DV.TRUE_DV, vii.getProperty(CONTAINER));
    }

    @Test
    public void testNoneOverwrite() {
        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.FALSE_DV);

        VariableInfoImpl overwritten = new VariableInfoImpl(viB.variable());
        try {
            new MergeHelper(minimalEvaluationContext, overwritten).mergeIntoMe(TRUE, TRUE, null, true, viB, List.of());
            fail();
        } catch (UnsupportedOperationException e) {
            // OK
        }
    }

    @Test
    public void testOneOverwrite() {
        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three);
        viA.setProperty(IDENTITY, DV.TRUE_DV);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.FALSE_DV);

        // situation:
        // int c = a;
        // try { ... c = b; } or synchronized(...) { c = b; }

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        List<ConditionAndVariableInfo> list = List.of(newConditionAndVariableInfo(TRUE, viB));
        VariableInfoImpl viC2 = new MergeHelper(minimalEvaluationContext, viC).mergeIntoNewObject(TRUE, TRUE, true, list);

        Expression res = viC2.getValue();
        assertEquals("4", res.toString());
        new MergeHelper(minimalEvaluationContext, viC2)
                .mergePropertiesIgnoreValue(true, viA, list);
        assertEquals(DV.FALSE_DV, viC2.getProperty(IDENTITY));
    }

    private ConditionAndVariableInfo newConditionAndVariableInfo(Expression e, VariableInfoImpl vi) {
        return new ConditionAndVariableInfo(e, vi, minimalEvaluationContext);
    }

    @Test
    public void testOneCisAIfXThenB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = Instance.forTesting(viX.variable().parameterizedType());
        viX.setValue(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three);
        viA.setProperty(IDENTITY, DV.TRUE_DV);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.FALSE_DV);

        // situation: boolean x = ...; int c = a; if(x) c = b;
        List<ConditionAndVariableInfo> xViB = List.of(newConditionAndVariableInfo(x, viB));

        VariableInfoImpl viC = new MergeHelper(minimalEvaluationContext, viA).mergeIntoNewObject(TRUE, TRUE, false, xViB);
        assertNotSame(viA, viC);

        Expression res = viC.getValue();
        assertEquals("instance type boolean?4:3", res.toString());

        MergeHelper mhC = new MergeHelper(minimalEvaluationContext, viC);
        mhC.mergePropertiesIgnoreValue(true, viA, xViB);
        assertEquals(DV.FALSE_DV, viC.getProperty(IDENTITY));

        // in a second iteration, we may encounter:
        mhC.mergeIntoMe(TRUE, TRUE, null, false, viA, xViB);
        // this should execute without raising exceptions
    }


    @Test
    public void testOneIfXThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.setProperty(IDENTITY, DV.FALSE_DV);
        ret.setValue(new UnknownExpression(primitives.booleanParameterizedType(), UnknownExpression.RETURN_VALUE));

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = Instance.forTesting(viX.variable().parameterizedType());
        viX.setValue(x);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.TRUE_DV);

        // situation: if(x) return b;
        List<ConditionAndVariableInfo> xViB = List.of(newConditionAndVariableInfo(x, viB));

        VariableInfoImpl ret2 = new MergeHelper(minimalEvaluationContext, ret)
                .mergeIntoNewObject(TRUE, TRUE, false, xViB);
        assertNotSame(ret, ret2);

        Expression value2 = ret2.getValue();
        assertEquals("instance type boolean?4:<return value:boolean>", value2.debugOutput());
        new MergeHelper(minimalEvaluationContext, ret2).mergePropertiesIgnoreValue(false, ret, xViB);
        assertEquals(DV.FALSE_DV, ret2.getProperty(IDENTITY));

        // OK let's continue
        // situation:  if(x) return b; return a;

        Expression state = Negation.negate(minimalEvaluationContext, x);

        // this is not done in the merge, but in the level 3 evaluation of the return value

        Expression ret3 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext,
                state, three, value2).getExpression();

        assertEquals("instance type boolean?4:3", ret3.toString());
    }


    @Test
    public void testOneIfXThenReturnIfYThenReturn() {
        Variable retVar = makeReturnVariable();
        VariableInfoImpl ret = new VariableInfoImpl(retVar);
        ret.setValue(new UnknownExpression(primitives.booleanParameterizedType(), UnknownExpression.RETURN_VALUE));
        ret.setProperty(IDENTITY, DV.FALSE_DV);

        VariableInfoImpl viX = new VariableInfoImpl(makeLocalIntVar("x"));
        Expression x = Instance.forTesting(viX.variable().parameterizedType());
        viX.setValue(x);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        Expression xEquals3 = Equals.equals(minimalEvaluationContext, x, three);
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.TRUE_DV);

        // situation: if(x==3) return b;
        List<ConditionAndVariableInfo> x3ViB = List.of(newConditionAndVariableInfo(xEquals3, viB));

        VariableInfoImpl ret2 = new MergeHelper(minimalEvaluationContext, ret)
                .mergeIntoNewObject(TRUE, TRUE, false, x3ViB);
        assertNotSame(ret2, ret);
        assertEquals("3==instance type int?4:<return value:boolean>", ret2.getValue().debugOutput());

        new MergeHelper(minimalEvaluationContext, ret2)
                .mergePropertiesIgnoreValue(false, ret, x3ViB);
        assertEquals(DV.FALSE_DV, ret2.getProperty(IDENTITY));

        // OK let's continue, but with another if in between

        // situation: if(x==3) return b; if(x==4) return a;

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        Expression xEquals4 = Equals.equals(minimalEvaluationContext, x, four);
        assertEquals("4==instance type int", xEquals4.debugOutput());
        viA.setValue(three);
        viA.setProperty(IDENTITY, DV.TRUE_DV);

        Expression state = Negation.negate(minimalEvaluationContext, xEquals3);
        List<ConditionAndVariableInfo> x4ViA = List.of(newConditionAndVariableInfo(xEquals4, viA));

        MergeHelper mergeHelper = new MergeHelper(minimalEvaluationContext, ret2);
        VariableInfoImpl ret3 = mergeHelper.mergeIntoNewObject(state, TRUE, false, x4ViA);
        assertNotSame(ret3, ret2);
        assertEquals("4==instance type int?3:3==instance type int?4:<return value:boolean>",
                ret3.getValue().debugOutput());
        new MergeHelper(minimalEvaluationContext, ret3)
                .mergePropertiesIgnoreValue(false, ret2, x4ViA);
        assertEquals(DV.FALSE_DV, ret3.getProperty(IDENTITY));

        // situation:
        // if(x==3) return b;
        // if(x==4) return a;
        // return c;  (which has state added: not (x))

        Expression combinedState = And.and(minimalEvaluationContext, Negation.negate(minimalEvaluationContext, xEquals3),
                Negation.negate(minimalEvaluationContext, xEquals4));

        Expression ret4 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext,
                combinedState, two, ret3.getValue()).getExpression();

        // IMPROVE actually the value should be 4 == x?3:3 == x?4:2
        assertEquals("3!=instance type int&&4!=instance type int?2:4==instance type int?3" +
                ":3==instance type int?4:<return value:boolean>", ret4.debugOutput());
    }


    @Test
    public void testOneCisAIfUnclearThenB() {
        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three);
        viA.setProperty(IDENTITY, DV.TRUE_DV);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.FALSE_DV);

        // situation: boolean x = ...; int c = a; if(some obscure condition) c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        viC.setValue(Instance.forTesting(viA.variable().parameterizedType()));

        Expression unknown = new UnknownExpression(primitives.booleanParameterizedType(), "no idea");
        List<ConditionAndVariableInfo> uViB = List.of(newConditionAndVariableInfo(unknown, viB));

        try {
            MergeHelper mergeHelper = new MergeHelper(minimalEvaluationContext, viC);
            VariableInfoImpl viC2 = mergeHelper.mergeIntoNewObject(TRUE, TRUE, false, uViB);
            assertNotSame(viA, viC2);
            assertEquals("<no idea>?4:instance type int", viC2.getValue().toString());

            MergeHelper mergeHelper2 = new MergeHelper(minimalEvaluationContext, viC2);
            mergeHelper2.mergePropertiesIgnoreValue(false, viA, uViB);
            assertEquals(DV.FALSE_DV, viC2.getProperty(IDENTITY));
        } catch (NullPointerException nullPointerException) {
            // OK! there is no current type
        }
    }


    @Test
    public void testTwoOverwriteCisIfXThenAElseB() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = Instance.forTesting(viX.variable().parameterizedType());
        viX.setValue(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three);
        viA.setProperty(IDENTITY, DV.TRUE_DV);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(four);
        viB.setProperty(IDENTITY, DV.FALSE_DV);

        // situation: boolean x = ...; int c; if(x) c = a; else c = b;

        List<ConditionAndVariableInfo> list = List.of(newConditionAndVariableInfo(x, viA),
                newConditionAndVariableInfo(Negation.negate(minimalEvaluationContext, x), viB));

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        MergeHelper mergeHelper = new MergeHelper(minimalEvaluationContext, viC);
        mergeHelper.mergeIntoMe(TRUE, TRUE, null, true, viC, list);
        assertEquals("instance type boolean?3:4", viC.getValue().toString());

        mergeHelper.mergePropertiesIgnoreValue(true, new VariableInfoImpl(viA.variable()), list);
        assertEquals(DV.FALSE_DV, viC.getProperty(IDENTITY));
    }

    // slight variant, showing the strength of ConditionalValue's factory method
    @Test
    public void testTwoOverwriteCisIfXThenAElseA() {
        VariableInfoImpl viX = new VariableInfoImpl(makeLocalBooleanVar());
        Expression x = Instance.forTesting(viX.variable().parameterizedType());
        viX.setValue(x);

        VariableInfoImpl viA = new VariableInfoImpl(makeLocalIntVar("a"));
        viA.setValue(three);
        viA.setProperty(CONTAINER, DV.TRUE_DV);

        VariableInfoImpl viB = new VariableInfoImpl(makeLocalIntVar("b"));
        viB.setValue(three);
        viB.setProperty(CONTAINER, DV.TRUE_DV);

        // situation: boolean x = ...; int c; if(x) c = a; else c = b;

        VariableInfoImpl viC = new VariableInfoImpl(makeLocalIntVar("c"));
        List<ConditionAndVariableInfo> list = List.of(newConditionAndVariableInfo(x, viA),
                newConditionAndVariableInfo(Negation.negate(minimalEvaluationContext, x), viB));

        MergeHelper mergeHelper = new MergeHelper(minimalEvaluationContext, viC);
        mergeHelper.mergeIntoMe(TRUE, TRUE, null, true, viC, list);

        Expression res = viC.getValue();
        assertEquals("3", res.toString());

        mergeHelper.mergePropertiesIgnoreValue(true, new VariableInfoImpl(viA.variable()), list);
        assertEquals(DV.TRUE_DV, viC.getProperty(CONTAINER));
    }
}
