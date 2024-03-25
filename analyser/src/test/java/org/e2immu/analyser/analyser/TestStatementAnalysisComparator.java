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

import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStatementAnalysisComparator {

    Primitives primitives;
    Statement emptyStatement;

    @BeforeEach
    public void before() {
        primitives = new PrimitivesImpl();
        emptyStatement = new ExpressionAsStatement(Identifier.generate("test"), EmptyExpression.EMPTY_EXPRESSION);
    }

    @Test
    public void test() {
        StatementAnalysisImpl ns0 = newStatementAnalysis("0");
        StatementAnalysisImpl ns1 = newStatementAnalysis("1");
        StatementAnalysisImpl ns01 = newStatementAnalysis("0.1");
        StatementAnalysisImpl ns10 = newStatementAnalysis("1.0");

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

    private StatementAnalysisImpl newStatementAnalysis(String s) {
        MethodInfo operator = primitives.lessOperatorInt();
        return new StatementAnalysisImpl(primitives, primitives.createMethodAnalysisForArrayConstructor(operator),
                emptyStatement, null, s, false);
    }
}
