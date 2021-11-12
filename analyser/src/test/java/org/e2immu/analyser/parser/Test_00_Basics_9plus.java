
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

import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_9plus extends CommonTestRunner {

    public Test_00_Basics_9plus() {
        super(false);
    }

    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setContainsValueHelper".equals(d.methodInfo().name)) {
                assertEquals("Basics_9.isFact(containsE)?containsE:!Basics_9.isKnown(true)&&retVal&&size>=1",
                        d.evaluationResult().value().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.evaluationResult().evaluationContext().getProperty(d.evaluationResult().value(),
                                VariableProperty.NOT_NULL_EXPRESSION, false, false));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isFact".equals(d.methodInfo().name) || "isKnown".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.NOT_INVOLVED_DV, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER),
                        "Method: " + d.methodInfo().name);
            }
            if ("setContainsValueHelper".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));

                assertEquals("Basics_9.isFact(containsE)?containsE:!Basics_9.isKnown(true)&&retVal&&size>=1",
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                        "class is " + d.methodAnalysis().getSingleReturnValue().getClass());
            }
            if ("test1".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));


                assertEquals("Basics_9.isFact(contains)?contains:!Basics_9.isKnown(true)&&isEmpty",
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                        "class is " + d.methodAnalysis().getSingleReturnValue().getClass());

                ParameterAnalysis contains = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.NOT_INVOLVED_DV, contains.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo contains && "contains".equals(contains.name)) {
                    assertDvInitial(d, MultiLevel.NOT_INVOLVED_DV, VariableProperty.EXTERNAL_IMMUTABLE);
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                    assertEquals("contains:0,return test1:1", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        testClass("Basics_9", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
