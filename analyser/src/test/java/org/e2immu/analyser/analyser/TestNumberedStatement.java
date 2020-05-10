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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.SideEffectContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestNumberedStatement {
    @Test
    public void test() {
        Statement emptyStatement = new ExpressionAsStatement(EmptyExpression.EMPTY_EXPRESSION);
        SideEffectContext sideEffectContext = new SideEffectContext(new MethodInfo(new TypeInfo("?"), List.of()));

        NumberedStatement ns0 = new NumberedStatement(sideEffectContext, emptyStatement, new int[]{0});
        NumberedStatement ns1 = new NumberedStatement(sideEffectContext, emptyStatement, new int[]{1});
        NumberedStatement ns01 = new NumberedStatement(sideEffectContext, emptyStatement, new int[]{0, 1});
        NumberedStatement ns10 = new NumberedStatement(sideEffectContext, emptyStatement, new int[]{1, 0});

        Assert.assertTrue(ns0.compareTo(ns0) == 0);
        Assert.assertTrue(ns0.compareTo(ns1) < 0);
        Assert.assertTrue(ns0.compareTo(ns01) < 0);
        Assert.assertTrue(ns0.compareTo(ns10) < 0);

        Assert.assertTrue(ns01.compareTo(ns0) > 0);
        Assert.assertTrue(ns01.compareTo(ns1) < 0);
        Assert.assertTrue(ns01.compareTo(ns01) == 0);
        Assert.assertTrue(ns01.compareTo(ns10) < 0);

        Assert.assertTrue(ns1.compareTo(ns0) > 0);
        Assert.assertTrue(ns1.compareTo(ns01) > 0);
        Assert.assertTrue(ns1.compareTo(ns1) == 0);
        Assert.assertTrue(ns1.compareTo(ns10) < 0);

        Assert.assertTrue(ns10.compareTo(ns0) > 0);
        Assert.assertTrue(ns10.compareTo(ns01) > 0);
        Assert.assertTrue(ns10.compareTo(ns1) > 0);
        Assert.assertTrue(ns10.compareTo(ns10) == 0);
    }
}
