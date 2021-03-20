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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.model.expression.Filter.FilterMode.ALL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFilter extends CommonAbstractValue {

    // remove a variable from an AndValue

    @Test
    public void test() {
        And andValue = (And) newAndAppend(a, b);
        assertEquals("a&&b", andValue.toString());
        Filter filter = new Filter(minimalEvaluationContext, ALL);
        Filter.FilterResult<Variable> filterResult = filter.filter(andValue, value -> new Filter.FilterResult<>(Map.of(), value));
        assertEquals(filterResult.rest(), andValue);

        Filter.FilterResult<Variable> filterResult2 = filter.filter(andValue, value -> {
            if (value instanceof VariableExpression variableValue && variableValue.variable() == b.variable()) {
                return new Filter.FilterResult<>(Map.of(b.variable(), b), filter.getDefaultRest());
            }
            return null;
        });
        assertEquals(a, filterResult2.rest());
    }

    // OrValue, AndValue, NegatedValue are collecting filters, but EqualsValue is NOT. So every ad-hoc filter that needs to be able
    // to deal with equality must implement it. The same applies to MethodValues.

    @Test
    public void testWithEquals() {
        Expression sNotNull = negate(equals(NullConstant.NULL_CONSTANT, s));
        And andValue = (And) newAndAppend(a, sNotNull);
        assertEquals("a&&null!=s", andValue.toString());
        Filter filter = new Filter(minimalEvaluationContext, ALL);
        Filter.FilterResult<Variable> filterResult = filter.filter(andValue, value -> {
            if (value instanceof Equals equalsValue) {
                if (equalsValue.rhs instanceof VariableExpression && ((VariableExpression) equalsValue.rhs).variable() == s.variable()) {
                    return new Filter.FilterResult<>(Map.of(s.variable(), s), filter.getDefaultRest());
                }
            }
            return null;
        });
        assertEquals(a, filterResult.rest());
    }
}
