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

package org.e2immu.analyser.analyser;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStatementAnalysisComparator {

    @BeforeAll
    public static void beforeAll() {
        Logger.activate();
    }

    Primitives primitives;
    Statement emptyStatement;

    @BeforeEach
    public void before() {
        primitives = new Primitives();
        emptyStatement = new ExpressionAsStatement(Identifier.generate(), EmptyExpression.EMPTY_EXPRESSION);
    }

    @Test
    public void test() {
        StatementAnalysis ns0 = newStatementAnalysis("0");
        StatementAnalysis ns1 = newStatementAnalysis("1");
        StatementAnalysis ns01 = newStatementAnalysis("0.1");
        StatementAnalysis ns10 = newStatementAnalysis("1.0");

        assertEquals(ns0.compareTo(ns0), 0);
        assertTrue(ns0.compareTo(ns1) < 0);
        assertTrue(ns0.compareTo(ns01) < 0);
        assertTrue(ns0.compareTo(ns10) < 0);

        assertTrue(ns01.compareTo(ns0) > 0);
        assertTrue(ns01.compareTo(ns1) < 0);
        assertEquals(ns01.compareTo(ns01), 0);
        assertTrue(ns01.compareTo(ns10) < 0);

        assertTrue(ns1.compareTo(ns0) > 0);
        assertTrue(ns1.compareTo(ns01) > 0);
        assertEquals(ns1.compareTo(ns1), 0);
        assertTrue(ns1.compareTo(ns10) < 0);

        assertTrue(ns10.compareTo(ns0) > 0);
        assertTrue(ns10.compareTo(ns01) > 0);
        assertTrue(ns10.compareTo(ns1) > 0);
        assertEquals(ns10.compareTo(ns10), 0);
    }

    private StatementAnalysis newStatementAnalysis(String s) {
        MethodInfo operator = primitives.lessOperatorInt;
        return new StatementAnalysis(primitives, MethodAnalysis.createEmpty(operator, primitives),
                emptyStatement, null, s, false);
    }
}
