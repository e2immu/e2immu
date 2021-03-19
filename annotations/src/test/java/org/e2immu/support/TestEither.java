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

import org.e2immu.support.Either;
import org.junit.Assert;
import org.junit.Test;

public class TestEither {

    @Test
    public void test1() {
        Either<String, Integer> a = Either.left("Hello");
        Assert.assertEquals("Hello", a.getLeft());
        Assert.assertEquals("Hello", a.getLeftOrElse("There"));
        Assert.assertEquals((Integer)34, a.getRightOrElse(34));
        try {
            a.getRight();
            Assert.fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
        Assert.assertTrue(a.isLeft());
        Assert.assertFalse(a.isRight());

        Either<String, Integer> b = Either.right(42);
        Assert.assertEquals((Integer)42, b.getRight());
        Assert.assertEquals("There", b.getLeftOrElse("There"));
        Assert.assertEquals((Integer)42, b.getRightOrElse(34));
        try {
            b.getLeft();
            Assert.fail();
        } catch (NullPointerException e) {
            // normal behaviour
        }
        Assert.assertFalse(b.isLeft());
        Assert.assertTrue(b.isRight());

        Assert.assertNotEquals(null, b);
        Assert.assertNotEquals("string", b);

        Assert.assertNotEquals(a, b);
        Either<String, Integer> b2 = Either.right(42);
        Assert.assertEquals(b, b2);
        Assert.assertEquals(b.hashCode(), b2.hashCode());
    }
}
