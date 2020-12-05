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
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.junit.Assert;
import org.junit.Test;

public class TestBooleanAndComparison extends CommonAbstractValue {

    // this test verifies that combining preconditions will work.

    @Test
    public void test1() {
        GreaterThanZero iGe0 = (GreaterThanZero) GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), true);
        GreaterThanZero iLt0 = (GreaterThanZero) GreaterThanZero.less(minimalEvaluationContext, i, newInt(0), false);
        GreaterThanZero jGe0 = (GreaterThanZero) GreaterThanZero.greater(minimalEvaluationContext, j, newInt(0), true);

        Expression iGe0_and__iLt0_or_jGe0 = newAndAppend(iGe0, newOrAppend(iLt0, jGe0));
        Assert.assertEquals("(i >= 0 and j >= 0)", iGe0_and__iLt0_or_jGe0.toString());

        Expression addIGe0Again = newAndAppend(iGe0_and__iLt0_or_jGe0, iGe0);
        Assert.assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again);

        Expression addIGe0Again2 = newAndAppend(iGe0, iGe0_and__iLt0_or_jGe0);
        Assert.assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again2);
    }

}
