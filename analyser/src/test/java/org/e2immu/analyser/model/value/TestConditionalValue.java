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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.ShallowTypeAnalyser;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestConditionalValue extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression cv1 = new InlineConditional(a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Expression cv2 = new InlineConditional(a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Assert.assertEquals("a?3:4", cv1.toString());
        Assert.assertEquals("a?3:4", cv2.toString());
        Assert.assertEquals(cv1, cv2);
    }

    private static Expression inline(Expression c, Expression t, Expression f) {
        return EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext,
                c, t, f, ObjectFlow.NO_FLOW).value();
    }

    @Test
    public void test2() {
        Expression cv1 = inline(a, TRUE, b);
        Assert.assertEquals("a||b", cv1.toString());
        Expression cv2 = inline(a, FALSE, b);
        Assert.assertEquals("!a&&b", cv2.toString());
        Expression cv3 = inline(a, b, TRUE);
        Assert.assertEquals("!a||b", cv3.toString());
        Expression cv4 = inline(a, b, FALSE);
        Assert.assertEquals("a&&b", cv4.toString());
    }

    @Test
    public void test3() {
        TypeInfo annotatedAPI = new TypeInfo("org.e2immu.annotatedapi", "AnnotatedAPI");
        ParameterizedType annotatedAPIPt = new ParameterizedType(annotatedAPI, 0);
        MethodInfo isFact = new MethodInfo(annotatedAPI, "isFact", ShallowTypeAnalyser.IS_FACT_FQN, ShallowTypeAnalyser.IS_FACT_FQN, false);
        isFact.methodInspection.set(new MethodInspectionImpl.Builder(annotatedAPI)
                .setStatic(true)
                .setReturnType(PRIMITIVES.booleanParameterizedType).build(InspectionProvider.DEFAULT));
        Expression isFactA = new MethodCall(new TypeExpression(annotatedAPIPt, Diamond.NO, ObjectFlow.NO_FLOW), isFact, List.of(a), ObjectFlow.NO_FLOW);
        Assert.assertEquals("AnnotatedAPI.isFact(a)", isFactA.toString());
        Expression isFactB = new MethodCall(new TypeExpression(annotatedAPIPt, Diamond.NO, ObjectFlow.NO_FLOW), isFact, List.of(b), ObjectFlow.NO_FLOW);
        Assert.assertEquals("AnnotatedAPI.isFact(b)", isFactB.toString());

        Assert.assertTrue(minimalEvaluationContext.getConditionManager().state().isBoolValueTrue());
        Expression cv1 = inline(isFactA, a, b);
        Assert.assertSame(b, cv1);

        EvaluationContext child = minimalEvaluationContext.child(a);
        Assert.assertTrue(child.getConditionManager().state().isBoolValueTrue());
        Assert.assertEquals("a", child.getConditionManager().condition().toString());
        Expression cv2 = EvaluateInlineConditional.conditionalValueConditionResolved(child, isFactA, a, b, ObjectFlow.NO_FLOW).value();
        Assert.assertSame(a, cv2);

        EvaluationContext child2 = minimalEvaluationContext.child(new And(PRIMITIVES).append(minimalEvaluationContext, a, b));
        Assert.assertEquals("a&&b", child2.getConditionManager().condition().toString());
        Assert.assertTrue(child.getConditionManager().state().isBoolValueTrue());
        Assert.assertEquals("a&&b", child2.getConditionManager().absoluteState(child2).toString());

        Expression cv3 = EvaluateInlineConditional.conditionalValueConditionResolved(child2, isFactA, a, b, ObjectFlow.NO_FLOW).value();
        Assert.assertSame(a, cv3);

        Expression cv3b = EvaluateInlineConditional.conditionalValueConditionResolved(child2, isFactB, a, b, ObjectFlow.NO_FLOW).value();
        Assert.assertSame(a, cv3b);

        EvaluationContext child3 = minimalEvaluationContext.child(
                new Or(PRIMITIVES).append(minimalEvaluationContext, c,
                        new And(PRIMITIVES).append(minimalEvaluationContext, a, b)));
        Assert.assertEquals("(a||c)&&(b||c)", child3.getConditionManager().absoluteState(child3).toString());
        Expression cv4 = EvaluateInlineConditional.conditionalValueConditionResolved(child3, isFactA, a, b, ObjectFlow.NO_FLOW).value();
        Assert.assertSame(b, cv4);
    }

    @Test
    public void test4() {
        Expression cv1 = inline(a, b, c);
        Assert.assertEquals("a?b:c", cv1.toString());
        Expression and1 = new And(PRIMITIVES).append(minimalEvaluationContext, a, cv1);
        Assert.assertEquals("a&&b", and1.toString());
        Expression and2 = new And(PRIMITIVES).append(minimalEvaluationContext, negate(a), cv1);
        Assert.assertEquals("!a&&c", and2.toString());
    }

    @Test
    public void test5() {
        Expression cv1 = inline(a, b, c);
        Expression eq = Equals.equals(minimalEvaluationContext, b, cv1, ObjectFlow.NO_FLOW);
        Assert.assertSame(a, eq);
    }

    @Test
    public void test6() {
        Expression cv1 = inline(a, b, NullConstant.NULL_CONSTANT);
        Expression eq = Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals(Negation.negate(minimalEvaluationContext, a), eq);
    }

    @Test
    public void test7() {
        Expression cv1 = inline(a, inline(b, newInt(3), newInt(4)), inline(c, newInt(2), newInt(5)));
        Expression eq2 = Equals.equals(minimalEvaluationContext, newInt(2), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("!a&&c", eq2.toString());
        Expression eq3 = Equals.equals(minimalEvaluationContext, newInt(3), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("a&&b", eq3.toString());
        Expression eq4 = Equals.equals(minimalEvaluationContext, newInt(4), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("a&&!b", eq4.toString());
        Expression eq5 = Equals.equals(minimalEvaluationContext, newInt(5), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("!a&&!c", eq5.toString());
    }


    @Test
    public void test8() {
        Expression cv1 = inline(a, inline(b, newInt(3), NullConstant.NULL_CONSTANT), inline(c, NullConstant.NULL_CONSTANT, newInt(5)));
        Expression eqNull = Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("(a||c)&&(!a||!b)&&(!b||c)", eqNull.toString());
        Expression eq3 = Equals.equals(minimalEvaluationContext, newInt(3), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("a&&b", eq3.toString());
        Expression eq4 = Equals.equals(minimalEvaluationContext, newInt(4), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("4==(a?b?3:null:c?null:5)", eq4.toString());
        Expression eq5 = Equals.equals(minimalEvaluationContext, newInt(5), cv1, ObjectFlow.NO_FLOW);
        Assert.assertEquals("!a&&!c", eq5.toString());
    }

    @Test
    public void testIfStatements2() {
        Expression e1 = inline(a, newInt(3), inline(a, newInt(4), newInt(5)));
        Assert.assertEquals("a?3:5", e1.toString());
    }

    @Test
    public void testLoops4() {
        Expression e1 = inline(a, newInt(3), inline(negate(a), newInt(4), newInt(5)));
        Assert.assertEquals("a?3:4", e1.toString());
    }
}
