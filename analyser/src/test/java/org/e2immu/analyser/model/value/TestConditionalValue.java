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
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestConditionalValue extends CommonAbstractValue {

    @Test
    public void test1() {
        Value cv1 = new ConditionalValue(PRIMITIVES, a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Value cv2 = new ConditionalValue(PRIMITIVES, a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Assert.assertEquals("a?3:4", cv1.toString());
        Assert.assertEquals("a?3:4", cv2.toString());
        Assert.assertEquals(cv1, cv2);
    }

    @Test
    public void test2() {
        Value cv1 = ConditionalValue.conditionalValueConditionResolved(minimalEvaluationContext, a, TRUE, b, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(a or b)", cv1.toString());
        Value cv2 = ConditionalValue.conditionalValueConditionResolved(minimalEvaluationContext, a, FALSE, b, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(not (a) and b)", cv2.toString());
        Value cv3 = ConditionalValue.conditionalValueConditionResolved(minimalEvaluationContext, a, b, TRUE, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(not (a) or b)", cv3.toString());
        Value cv4 = ConditionalValue.conditionalValueConditionResolved(minimalEvaluationContext, a, b, FALSE, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("(a and b)", cv4.toString());
    }

    @Test
    public void test3() {
        TypeInfo annotatedAPI = new TypeInfo("org.e2immu.annotatedapi", "AnnotatedAPI");
        ParameterizedType annotatedAPIPt = new ParameterizedType(annotatedAPI, 0);
        MethodInfo isFact = new MethodInfo(annotatedAPI, "isFact", ShallowTypeAnalyser.IS_FACT_FQN, ShallowTypeAnalyser.IS_FACT_FQN, false);
        Value isFactA = new MethodValue(isFact, new TypeValue(annotatedAPIPt, ObjectFlow.NO_FLOW), List.of(a), ObjectFlow.NO_FLOW);
        Assert.assertEquals("org.e2immu.annotatedapi.AnnotatedAPI.isFact(a)", isFactA.toString());

        Assert.assertSame(UnknownValue.EMPTY, minimalEvaluationContext.getConditionManager().state);
        Value cv1 = ConditionalValue.conditionalValueConditionResolved(minimalEvaluationContext, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(b, cv1);

        EvaluationContext child = minimalEvaluationContext.child(a);
        Assert.assertSame(a, child.getConditionManager().state);
        Value cv2 = ConditionalValue.conditionalValueConditionResolved(child, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(a, cv2);

        EvaluationContext child2 = minimalEvaluationContext.child(new AndValue(PRIMITIVES).append(minimalEvaluationContext, a, b));
        Assert.assertEquals("(a and b)", child2.getConditionManager().state.toString());
        Value cv3 = ConditionalValue.conditionalValueConditionResolved(child2, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(a, cv3);

        EvaluationContext child3 = minimalEvaluationContext.child(
                new OrValue(PRIMITIVES).append(minimalEvaluationContext, c,
                new AndValue(PRIMITIVES).append(minimalEvaluationContext, a, b)));
        Assert.assertEquals("((a or c) and (b or c))", child3.getConditionManager().state.toString());
        Value cv4 = ConditionalValue.conditionalValueConditionResolved(child3, isFactA, a, b, ObjectFlow.NO_FLOW).value;
        Assert.assertSame(b, cv4);
    }
}
