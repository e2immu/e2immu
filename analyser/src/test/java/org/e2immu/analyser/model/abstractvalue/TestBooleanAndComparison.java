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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestBooleanAndComparison extends CommonAbstractValue {

    // this test verifies that combining preconditions will work.

    @Test
    public void test1() {
        GreaterThanZeroValue iGe0 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(i, new IntValue(0), true);
        GreaterThanZeroValue iLt0 = (GreaterThanZeroValue) GreaterThanZeroValue.less(i, new IntValue(0), false);
        GreaterThanZeroValue jGe0 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(j, new IntValue(0), true);

        Value iGe0_and__iLt0_or_jGe0 = new AndValue().append(iGe0, new OrValue().append(iLt0, jGe0));
        Assert.assertEquals("(i >= 0 and j >= 0)", iGe0_and__iLt0_or_jGe0.toString());

        Value addIGe0Again = new AndValue().append(iGe0_and__iLt0_or_jGe0, iGe0);
        Assert.assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again);

        Value addIGe0Again2 = new AndValue().append(iGe0, iGe0_and__iLt0_or_jGe0);
        Assert.assertEquals(iGe0_and__iLt0_or_jGe0, addIGe0Again2);
    }

    // now do the same with -i
    @Test
    public void testPlaceHolder1() {
        GreaterThanZeroValue iLt0 = (GreaterThanZeroValue) GreaterThanZeroValue.less(i, new IntValue(0), true);
        GreaterThanZeroValue jGe0 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(j, new IntValue(0), false);

        GreaterThanZeroValue iPropWrapLt0 = (GreaterThanZeroValue) GreaterThanZeroValue.less(iPropertyWrapper, new IntValue(0), true);

        Value and1 = new AndValue().append(iLt0, jGe0);
        Assert.assertEquals("(((-1) + j) >= 0 and (-i) >= 0)", and1.toString());

        Assert.assertEquals(and1, new AndValue().append(and1, iPropWrapLt0));
        Assert.assertEquals(and1, new AndValue().append(iPropWrapLt0, and1));
    }

    public static final String I_0_1_I_0_J_0 = "[((-1) + (-i)) >= 0, ((-1) + (-i)) >= 0, j >= 0]";

    @Test
    public void testPlaceHolder2() {
        GreaterThanZeroValue iLt0 = (GreaterThanZeroValue) GreaterThanZeroValue.less(iPropertyWrapper, new IntValue(0), false);
        GreaterThanZeroValue jGe0 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(j, new IntValue(0), true);
        GreaterThanZeroValue iphLt0 = (GreaterThanZeroValue) GreaterThanZeroValue.less(i, new IntValue(0), false);

        Value and1 = new AndValue().append(iLt0, jGe0);
        Assert.assertEquals("(((-1) + (-i)) >= 0 and j >= 0)", and1.toString());

        Assert.assertEquals(and1, new AndValue().append(and1, iphLt0));
        Assert.assertEquals(and1, new AndValue().append(iphLt0, and1));

        List<Value> l1 = new ArrayList<>(List.of(iLt0, jGe0, iphLt0));
        Collections.sort(l1);
        Assert.assertEquals(I_0_1_I_0_J_0, l1.toString());
        List<Value> l2 = new ArrayList<>(List.of(iphLt0, jGe0, iLt0));
        Collections.sort(l2);
        Assert.assertEquals(I_0_1_I_0_J_0, l2.toString());
        List<Value> l3 = new ArrayList<>(List.of(jGe0, iphLt0,  iLt0));
        Collections.sort(l3);
        Assert.assertEquals(I_0_1_I_0_J_0, l3.toString());
    }
}
