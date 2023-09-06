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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.expression.StringConcat;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_23_TryStatement_AAPI extends CommonTestRunner {

    public Test_23_TryStatement_AAPI() {
        super(true);
    }

    @Test
    public void test_10() throws IOException {
        testClass("TryStatement_10", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Disabled("See Lambda_AAPI_18,Precondition_10; Runnable run is modifying, variable is immutable")
    @Test
    public void test_11() throws IOException {
        testClass("TryStatement_11", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    /*
    ensure that the 'throw' operation in the first catch-block is not part of the precondition.
     */
    @Test
    public void test_12() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("same1".equals(d.methodInfo().name)) {
                if ("0.1.1".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().stateData().isEscapeNotInPreOrPostConditions());
                    assertEquals("[0.1.1]", d.statementAnalysis().methodLevelData()
                            .getIndicesOfEscapesNotInPreOrPostConditions().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("same1".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]", d.methodAnalysis().getPrecondition().toString());
                assertEquals("[0.1.1]", d.methodAnalysis().indicesOfEscapesNotInPreOrPostConditions().toString());
            }
        };
        testClass("TryStatement_12", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
