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
import org.e2immu.analyser.model.value.NullValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestFilter extends CommonAbstractValue {

    // remove a variable from an AndValue

    @Test
    public void test() {
        AndValue andValue = (AndValue) new AndValue().append(a, b);
        Assert.assertEquals("(a and b)", andValue.toString());

        Value.FilterResult filterResult = andValue.filter(Value.FilterMode.ALL, value -> new Value.FilterResult(Map.of(), value));
        Assert.assertNotSame(filterResult.rest, andValue);
        Assert.assertEquals(filterResult.rest, andValue);

        Value.FilterResult filterResult2 = andValue.filter(Value.FilterMode.ALL, value -> {
            if (value instanceof ValueWithVariable && ((ValueWithVariable) value).variable == b.variable) {
                return new Value.FilterResult(Map.of(b.variable, b), UnknownValue.EMPTY);
            }
            return new Value.FilterResult(Map.of(), value);
        });
        Assert.assertEquals(a, filterResult2.rest);
    }

    // OrValue, AndValue, NegatedValue are collecting filters, but EqualsValue is NOT. So every ad-hoc filter that needs to be able
    // to deal with equality must implement it. The same applies to MethodValues.

    @Test
    public void testWithEquals() {
        Value sNotNull = NegatedValue.negate(EqualsValue.equals(NullValue.NULL_VALUE, s));
        AndValue andValue = (AndValue) new AndValue().append(a, sNotNull);
        Assert.assertEquals("(a and not (null == s))", andValue.toString());

        Value.FilterResult filterResult = andValue.filter(Value.FilterMode.ALL, value -> {
            if (value instanceof EqualsValue) {
                EqualsValue equalsValue = (EqualsValue) value;
                if (equalsValue.rhs instanceof ValueWithVariable && ((ValueWithVariable) equalsValue.rhs).variable == s.variable) {
                    return new Value.FilterResult(Map.of(s.variable, s), UnknownValue.EMPTY);
                }
            }
            return new Value.FilterResult(Map.of(), value);
        });
        Assert.assertEquals(a, filterResult.rest);
    }
}
