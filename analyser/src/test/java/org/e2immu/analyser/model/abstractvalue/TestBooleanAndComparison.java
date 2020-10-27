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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestBooleanAndComparison extends CommonAbstractValue {

    // this test verifies that combining preconditions will work.

    @Test
    public void test1() {
        GreaterThanZeroValue iGe0 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(minimalEvaluationContext, i, new IntValue(0), true);
        GreaterThanZeroValue iLt0 = (GreaterThanZeroValue) GreaterThanZeroValue.less(minimalEvaluationContext, i, new IntValue(0), false);
        GreaterThanZeroValue jGe0 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(minimalEvaluationContext, j, new IntValue(0), true);

        Value iGe0_and__iLt0_or_jGe0 = new AndValue().append(iGe0, new OrValue().append(iLt0, jGe0));
        Assert.assertEquals("(i >= 0 and j >= 0)", iGe0_and__iLt0_or_jGe0.toString());

        Value addIGe0Again = new AndValue().append(iGe0_and__iLt0_or_jGe0, iGe0);
        Assert.assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again);

        Value addIGe0Again2 = new AndValue().append(iGe0, iGe0_and__iLt0_or_jGe0);
        Assert.assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again2);
    }

}
