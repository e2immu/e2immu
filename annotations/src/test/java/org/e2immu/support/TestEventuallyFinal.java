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

import org.e2immu.support.EventuallyFinal;
import org.junit.Assert;
import org.junit.Test;

public class TestEventuallyFinal {

    @Test
    public void test1() {
        EventuallyFinal<String> e = new EventuallyFinal<>();
        Assert.assertNull(e.get());
        Assert.assertTrue(e.isVariable());
        Assert.assertFalse(e.isFinal());
        e.setVariable("abc");
        Assert.assertEquals("abc", e.get());
        Assert.assertTrue(e.isVariable());
        Assert.assertFalse(e.isFinal());
        e.setVariable("xyz");
        Assert.assertEquals("xyz", e.get());
        Assert.assertTrue(e.isVariable());
        Assert.assertFalse(e.isFinal());

        e.setFinal("123");
        Assert.assertFalse(e.isVariable());
        Assert.assertTrue(e.isFinal());
        Assert.assertEquals("123", e.get());

        e.setFinal("123");
        Assert.assertFalse(e.isVariable());
        Assert.assertTrue(e.isFinal());
        Assert.assertEquals("123", e.get());

        try {
            e.setFinal("1234");
            Assert.fail();
        } catch (RuntimeException r) {
            // OK!
        }
        Assert.assertFalse(e.isVariable());
        Assert.assertTrue(e.isFinal());
        Assert.assertEquals("123", e.get());
    }
}
