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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestVariableInfoContainer extends CommonVariableInfo {

    @Test
    public void test1() {
        Variable a = makeLocalIntVar("a");
        VariableInfoContainer vic = new VariableInfoContainerImpl(a);
        VariableInfo vi = vic.current();
        Assert.assertFalse(vi.hasProperty(VariableProperty.INDEPENDENT));
        Assert.assertEquals(VariableInfoContainer.LEVEL_1_INITIALISER, vic.getCurrentLevel());

        // we set a property
        // given that we started at level 1, we keep writing there (there has not yet been an assignment)

        vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.INDEPENDENT, Level.FALSE);
        Assert.assertEquals(VariableInfoContainer.LEVEL_1_INITIALISER, vic.getCurrentLevel());
        Assert.assertTrue(vi.hasProperty(VariableProperty.INDEPENDENT));
        Assert.assertSame(vi, vic.current());
        Assert.assertEquals(Level.FALSE, vi.getProperty(VariableProperty.INDEPENDENT));

        // we set the property again -- nothing changes

        vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.INDEPENDENT, Level.FALSE);
        Assert.assertEquals(VariableInfoContainer.LEVEL_1_INITIALISER, vic.getCurrentLevel());
        Assert.assertSame(vi, vic.current());
        Assert.assertTrue(vi.hasProperty(VariableProperty.INDEPENDENT));
        Assert.assertEquals(Level.FALSE, vi.getProperty(VariableProperty.INDEPENDENT));

        // we increase the property -- only the value goes up

        vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.INDEPENDENT, Level.TRUE);
        Assert.assertEquals(VariableInfoContainer.LEVEL_1_INITIALISER, vic.getCurrentLevel());
        Assert.assertSame(vi, vic.current());
        Assert.assertTrue(vi.hasProperty(VariableProperty.INDEPENDENT));
        Assert.assertEquals(Level.TRUE, vi.getProperty(VariableProperty.INDEPENDENT));

        // decreasing the property is not allowed

        try {
            vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.INDEPENDENT, Level.FALSE);
            Assert.fail();
        } catch (UnsupportedOperationException unsupportedOperationException) {
            // OK
        }
    }

    @Test
    public void test2() {
        Variable a = makeLocalIntVar("a");
        VariableInfoImpl previous = new VariableInfoImpl(a);
        previous.setProperty(VariableProperty.INDEPENDENT, Level.FALSE);

        VariableInfoContainer vic = new VariableInfoContainerImpl(previous);
        VariableInfo vi = vic.current();
        Assert.assertEquals(VariableInfoContainer.LEVEL_0_PREVIOUS, vic.getCurrentLevel());
        Assert.assertEquals(Level.FALSE, vi.getProperty(VariableProperty.INDEPENDENT));

        // we set a property
        // because the value doesn't change, the level stays the same

        vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.INDEPENDENT, Level.FALSE);
        Assert.assertEquals(VariableInfoContainer.LEVEL_0_PREVIOUS, vic.getCurrentLevel());
        Assert.assertSame(vi, vic.current());
        Assert.assertEquals(Level.FALSE, vi.getProperty(VariableProperty.INDEPENDENT));

        // we write the property to true

        vic.setProperty(VariableInfoContainer.LEVEL_2_UPDATER, VariableProperty.INDEPENDENT, Level.TRUE);
        Assert.assertEquals(VariableInfoContainer.LEVEL_2_UPDATER, vic.getCurrentLevel());
        Assert.assertNotSame(vi, vic.current());
        VariableInfo vi2 = vic.current();
        Assert.assertEquals(Level.TRUE, vi2.getProperty(VariableProperty.INDEPENDENT));

        // then, make an assignment

        IntValue three = new IntValue(primitives, 3, ObjectFlow.NO_FLOW);
        try {
            vic.setValueOnAssignment(VariableInfoContainer.LEVEL_4_SUMMARY, three, Map.of());
            Assert.fail();
        } catch (RuntimeException rte) {
            // OK
        }
        Assert.assertFalse(vic.current().valueIsSet());

        vic.assignment(VariableInfoContainer.LEVEL_4_SUMMARY);
        Assert.assertEquals(VariableInfoContainer.LEVEL_4_SUMMARY, vic.getCurrentLevel());
        Assert.assertSame(three, vic.current().getValue());

    }
}