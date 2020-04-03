/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class TestLazy {

    @Test
    public void test1() {
        AtomicInteger counter = new AtomicInteger();
        Lazy<String> lazy = new Lazy<>(() -> {
            counter.getAndIncrement();
            return "abc";
        });
        Assert.assertFalse(lazy.hasBeenEvaluated());

        String content = lazy.get();
        Assert.assertEquals("abc", content);
        Assert.assertEquals(1, counter.get());
        Assert.assertTrue(lazy.hasBeenEvaluated());

        // 2nd evaluation
        content = lazy.get();
        Assert.assertEquals("abc", content);
        Assert.assertEquals(1, counter.get());
        Assert.assertTrue(lazy.hasBeenEvaluated());
    }

    @Test
    public void test2() {
        try {
            new Lazy<String>(null);
            Assert.fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
    }

    @Test
    public void test3() {
        Lazy<String> lazy = new Lazy<>(() -> null);
        try {
            lazy.get();
            Assert.fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
    }
}
