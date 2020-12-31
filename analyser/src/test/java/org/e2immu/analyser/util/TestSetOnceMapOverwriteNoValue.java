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

package org.e2immu.analyser.util;

import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

public class TestSetOnceMapOverwriteNoValue {

    @Test
    public void test() {
        SetOnceMapOverwriteNoValue<String> map = new SetOnceMapOverwriteNoValue<>();

        map.put("1", EmptyExpression.EMPTY_EXPRESSION);
        Assert.assertTrue(map.isSet("1"));
        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, map.get("1"));
        try {
            map.put("1", EmptyExpression.EMPTY_EXPRESSION);
            Assert.fail("Duplicating");
        } catch (RuntimeException re) {
            // OK
        }

        map.put("2", EmptyExpression.NO_VALUE);
        Assert.assertFalse(map.isSet("2"));

        // but it is in the map!
        Assert.assertEquals(2L, map.stream().count());

        map.put("2", EmptyExpression.EMPTY_EXPRESSION);
        Assert.assertTrue(map.isSet("2"));
        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, map.get("2"));
        try {
            map.put("2", EmptyExpression.EMPTY_EXPRESSION);
            Assert.fail("Duplicating");
        } catch (RuntimeException re) {
            // OK
        }
    }
}
