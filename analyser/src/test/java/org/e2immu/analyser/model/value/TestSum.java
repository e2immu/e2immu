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
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

public class TestSum extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression s = Sum.sum(minimalEvaluationContext, newInt(1), i, ObjectFlow.NO_FLOW);
        Assert.assertEquals("1+i", s.toString());
        Expression s2 = Sum.sum(minimalEvaluationContext, newInt(2), s, ObjectFlow.NO_FLOW);
        Assert.assertEquals("3+i", s2.toString());
    }
}
