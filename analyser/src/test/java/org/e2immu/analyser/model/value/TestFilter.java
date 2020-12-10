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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestFilter extends CommonAbstractValue {

    // remove a variable from an AndValue

    @Test
    public void test() {
        And andValue = (And) newAndAppend(a, b);
        Assert.assertEquals("a&&b", andValue.toString());

        Filter.FilterResult<Variable> filterResult = Filter.filter(minimalEvaluationContext, andValue,
                Filter.FilterMode.ALL, value -> new Filter.FilterResult<>(Map.of(), value));
        Assert.assertEquals(filterResult.rest(), andValue);

        Filter.FilterResult<Variable> filterResult2 = Filter.filter(minimalEvaluationContext, andValue, Filter.FilterMode.ALL, value -> {
            if (value instanceof VariableExpression variableValue && variableValue.variable() == b.variable()) {
                return new Filter.FilterResult<>(Map.of(b.variable(), b), EmptyExpression.EMPTY_EXPRESSION);
            }
            return null;
        });
        Assert.assertEquals(a, filterResult2.rest());
    }

    // OrValue, AndValue, NegatedValue are collecting filters, but EqualsValue is NOT. So every ad-hoc filter that needs to be able
    // to deal with equality must implement it. The same applies to MethodValues.

    @Test
    public void testWithEquals() {
        Expression sNotNull = negate(equals(NullConstant.NULL_CONSTANT, s));
        And andValue = (And) newAndAppend(a, sNotNull);
        Assert.assertEquals("a&&null!=s", andValue.toString());

        Filter.FilterResult<Variable> filterResult = Filter.filter(minimalEvaluationContext, andValue, Filter.FilterMode.ALL, value -> {
            if (value instanceof Equals equalsValue) {
                if (equalsValue.rhs instanceof VariableExpression && ((VariableExpression) equalsValue.rhs).variable() == s.variable()) {
                    return new Filter.FilterResult<>(Map.of(s.variable(), s), EmptyExpression.EMPTY_EXPRESSION);
                }
            }
            return null;
        });
        Assert.assertEquals(a, filterResult.rest());
    }
}
