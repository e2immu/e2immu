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
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.SwitchExpression;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class TestSwitchValue extends CommonAbstractValue {
/*
    @Test
    public void testCleanup1() {
        List<SwitchExpression.SwitchValueEntry> entries = List.of(
                newEntry(newString("b"), newInt(0)),
                newEntry(newString("a"), newInt(4), newInt(3)),
                newEntry(newString("a"), newInt(1), newInt(2), newInt(2)));
        List<SwitchValue.SwitchValueEntry> cleanedUp = SwitchValue.cleanUpEntries(entries);
        Assert.assertEquals("[case 0->b, case 1,2,3,4->a]", cleanedUp.toString());

        Expression value = SwitchValue.switchValue(minimalEvaluationContext, i, entries, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals("0 == i?b:a", value.toString());
    }

    @Test
    public void testCleanup2() {
        List<SwitchValue.SwitchValueEntry> entries = List.of(
                newEntry(newString("c"), EmptyExpression.EMPTY_EXPRESSION),
                newEntry(newString("b"), newInt(0)),
                newEntry(newString("a"), newInt(4), newInt(3)),
                newEntry(newString("a"), newInt(1), newInt(2), newInt(2)));
        List<SwitchValue.SwitchValueEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        Assert.assertEquals("[case 0->b, case 1,2->a, case 3,4->a, default->c]", sorted.toString());
        List<SwitchValue.SwitchValueEntry> cleanedUp = SwitchValue.cleanUpEntries(entries);
        Assert.assertEquals("[case 0->b, case 1,2,3,4->a, default->c]", cleanedUp.toString());

        Expression value = SwitchValue.switchValue(minimalEvaluationContext, i, entries, ObjectFlow.NO_FLOW).value;
        Assert.assertTrue(value instanceof SwitchValue);
        Assert.assertEquals("switch(i){case 0->b; case 1,2,3,4->a; default->c}", value.toString());
    }

    @Test
    public void testCleanup3() {
        List<SwitchValue.SwitchValueEntry> entries = List.of(
                newEntry(newString("a"), EmptyExpression.EMPTY_EXPRESSION),
                newEntry(newString("a"), newInt(0)),
                newEntry(newString("a"), newInt(4), newInt(3)),
                newEntry(newString("a"), newInt(1), newInt(2), newInt(2)));
        List<SwitchValue.SwitchValueEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        Assert.assertEquals("[case 0->a, case 1,2->a, case 3,4->a, default->a]", sorted.toString());
        List<SwitchValue.SwitchValueEntry> cleanedUp = SwitchValue.cleanUpEntries(entries);
        Assert.assertEquals("[default->a]", cleanedUp.toString());

        Expression value = SwitchValue.switchValue(minimalEvaluationContext, i, entries, ObjectFlow.NO_FLOW).value;
        Assert.assertEquals(newString("a"), value);
    }

    private SwitchValue.SwitchValueEntry newEntry(Expression value, Expression... labels) {
        Set<Expression> labelSet = new HashSet<>();
        Collections.addAll(labelSet, labels);
        return new SwitchValue.SwitchValueEntry(labelSet, value, ObjectFlow.NO_FLOW);
    }

 */
}
