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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

public class TestConditionalValue extends CommonAbstractValue {

    @Test
    public void test1() {
        Value cv1 = new ConditionalValue(PRIMITIVES, a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Value cv2 = new ConditionalValue(PRIMITIVES, a, newInt(3), newInt(4), ObjectFlow.NO_FLOW);
        Assert.assertEquals("a?3:4", cv1.toString());
        Assert.assertEquals("a?3:4", cv2.toString());
        Assert.assertEquals(cv1, cv2);
    }
}
