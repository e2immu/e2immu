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

package org.e2immu.support;

import org.e2immu.support.FirstThen;
import org.junit.Assert;
import org.junit.Test;

public class TestFirstThen {

    @Test
    public void test1() {
        FirstThen<String, Integer> a = new FirstThen<>("Hello");
        Assert.assertEquals("Hello", a.getFirst());
        try {
            a.get();
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        Assert.assertTrue(a.isFirst());
        Assert.assertFalse(a.isSet());
        a.set(34);
        Assert.assertEquals((Integer)34, a.get());
        try {
            a.getFirst();
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        Assert.assertFalse(a.isFirst());
        Assert.assertTrue(a.isSet());
        try {
            a.set(42);
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        Assert.assertNotEquals(null, a);
        Assert.assertNotEquals("string", a);
        FirstThen<String, Integer> b = new FirstThen<>("Hello");
        Assert.assertNotEquals(b, a);
        b.set(34);
        Assert.assertEquals(b, a);
    }
}
