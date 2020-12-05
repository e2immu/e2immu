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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestConditionalValue extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression cv1 = new InlineConditionalOperator(a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Expression cv2 = new InlineConditionalOperator(a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Assert.assertEquals("a?3:4", cv1.toString());
        Assert.assertEquals("a?3:4", cv2.toString());
        Assert.assertEquals(cv1, cv2);
    }

    @Test
    public void test2() {
        Expression cv1 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext, a, TRUE, b, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(a or b)", cv1.toString());
        Expression cv2 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext, a, FALSE, b, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(not (a) and b)", cv2.toString());
        Expression cv3 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext, a, b, TRUE, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(not (a) or b)", cv3.toString());
        Expression cv4 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext, a, b, FALSE, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(a and b)", cv4.toString());
    }

    @Test
    public void test3() {
        TypeInfo annotatedAPI = new TypeInfo("org.e2immu.annotatedapi", "AnnotatedAPI");
        ParameterizedType annotatedAPIPt = new ParameterizedType(annotatedAPI, 0);
        MethodInfo isFact = new MethodInfo(annotatedAPI, "isFact", ShallowTypeAnalyser.IS_FACT_FQN, ShallowTypeAnalyser.IS_FACT_FQN, false);
        Expression isFactA = new MethodCall(new TypeExpression(annotatedAPIPt, ObjectFlow.NO_FLOW), isFact, List.of(a), ObjectFlow.NO_FLOW);
        Assert.assertEquals("org.e2immu.annotatedapi.AnnotatedAPI.isFact(a)", isFactA.toString());
        Expression isFactB = new MethodCall(new TypeExpression(annotatedAPIPt, ObjectFlow.NO_FLOW), isFact, List.of(b), ObjectFlow.NO_FLOW);
        Assert.assertEquals("org.e2immu.annotatedapi.AnnotatedAPI.isFact(b)", isFactB.toString());

        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, minimalEvaluationContext.getConditionManager().state);
        Expression cv1 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(b, cv1);

        EvaluationContext child = minimalEvaluationContext.child(a);
        Assert.assertSame(a, child.getConditionManager().state);
        Expression cv2 = EvaluateInlineConditional.conditionalValueConditionResolved(child, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(a, cv2);

        EvaluationContext child2 = minimalEvaluationContext.child(new AndExpression(PRIMITIVES).append(minimalEvaluationContext, a, b));
        Assert.assertEquals("(a and b)", child2.getConditionManager().state.toString());
        Expression cv3 = EvaluateInlineConditional.conditionalValueConditionResolved(child2, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(a, cv3);

        Expression cv3b = EvaluateInlineConditional.conditionalValueConditionResolved(child2, isFactB, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(a, cv3b);

        EvaluationContext child3 = minimalEvaluationContext.child(
                new OrExpression(PRIMITIVES).append(minimalEvaluationContext, c,
                        new AndExpression(PRIMITIVES).append(minimalEvaluationContext, a, b)));
        Assert.assertEquals("((a or c) and (b or c))", child3.getConditionManager().state.toString());
        Expression cv4 = EvaluateInlineConditional.conditionalValueConditionResolved(child3, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(b, cv4);
    }

    @Test
    public void test4() {
        Expression cv1 = EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext, a, b, c, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("a?b:c", cv1.toString());
        Expression and1 = new AndExpression(PRIMITIVES).append(minimalEvaluationContext, a, cv1);
        Assert.assertEquals("(a and b)", and1.toString());
        Expression and2 = new AndExpression(PRIMITIVES).append(minimalEvaluationContext, negate(a), cv1);
        Assert.assertEquals("(not (a) and c)", and2.toString());
    }
}
