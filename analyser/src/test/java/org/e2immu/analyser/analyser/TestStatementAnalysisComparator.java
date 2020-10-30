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

import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;

public class TestStatementAnalysisComparator {
    Primitives primitives = new Primitives();
    Statement emptyStatement = new ExpressionAsStatement(EmptyExpression.EMPTY_EXPRESSION);

    @Test
    public void test() {
        StatementAnalysis ns0 = newStatementAnalysis("0");
        StatementAnalysis ns1 = newStatementAnalysis("1");
        StatementAnalysis ns01 = newStatementAnalysis("0.1");
        StatementAnalysis ns10 = newStatementAnalysis("1.0");

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

    private StatementAnalysis newStatementAnalysis(String s) {
        return new StatementAnalysis(primitives, null, emptyStatement, null, s, false);
    }
}
