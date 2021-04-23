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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_28_InnerClass extends CommonTestRunner {
    public Test_28_InnerClass() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("InnerClass_0".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId()) && "outerField".equals(d.variableName())) {
                assertTrue(d.variable() instanceof ParameterInfo);
                int notNull = d.properties().getOrDefault(VariableProperty.NOT_NULL_EXPRESSION, Level.DELAY);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("nonPrivateNonFinal".equals(d.fieldInfo().name)) {
                assertNotNull(d.haveError(Message.Label.NON_PRIVATE_FIELD_NOT_FINAL));
            }
            if ("unusedInnerField".equals(d.fieldInfo().name)) {
                assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("doAssignmentIntoNestedType".equals(d.methodInfo().name)) {
                assertNotNull(d.haveError(Message.Label.METHOD_SHOULD_BE_MARKED_STATIC));
            }
        };


        testClass("InnerClass_0", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
