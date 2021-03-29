/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.SwitchExpression;

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
