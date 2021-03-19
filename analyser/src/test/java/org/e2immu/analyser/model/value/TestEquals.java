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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEquals extends CommonAbstractValue {
    @BeforeAll
    public static void beforeClass() {
        CommonAbstractValue.beforeClass();
    }

    @Test
    public void test() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        assertEquals("false", Equals.equals(minimalEvaluationContext, int3, int5, ObjectFlow.NO_FLOW).toString());
    }

    @Test
    public void testCommonTerms() {
        Expression int3 = newInt(3);
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, int3, i, ObjectFlow.NO_FLOW);
        Expression right = Sum.sum(minimalEvaluationContext, int5, i, ObjectFlow.NO_FLOW);
        assertEquals("false", Equals.equals(minimalEvaluationContext, left, right, ObjectFlow.NO_FLOW).toString());
    }

    @Test
    public void testCommonTerms2() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, i, int5, ObjectFlow.NO_FLOW);
        Expression right = Sum.sum(minimalEvaluationContext, int5, i, ObjectFlow.NO_FLOW);
        assertEquals("true", Equals.equals(minimalEvaluationContext, left, right, ObjectFlow.NO_FLOW).toString());
    }

    @Test
    public void testCommonTerms3() {
        Expression int5 = newInt(5);
        Expression left = Sum.sum(minimalEvaluationContext, i, int5, ObjectFlow.NO_FLOW);
        Expression right = i;
        assertEquals("false", Equals.equals(minimalEvaluationContext, left, right, ObjectFlow.NO_FLOW).toString());
    }
}
