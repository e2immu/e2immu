
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_20_CyclicReferences extends CommonTestRunner {
    public Test_20_CyclicReferences() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int numParameters = d.methodInfo().methodInspection.get().getParameters().size();
            if ("CyclicReferences_0".equals(d.methodInfo().name) && numParameters == 1) {
                if (d.variable() instanceof FieldReference fr && "field2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("\"cde\"", d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                }
            }
        };
        testClass("CyclicReferences_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("findTailRecursion".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("(list.get(0).equals(find)||!list.isEmpty())&&(list.get(0).equals(find)||CyclicReferences_1.findTailRecursion(find,list.subList(1,list.size())))",
                        d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("findTailRecursion".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p && p.name.equals("list")) {
                assertEquals("nullable instance type List<String>", d.currentValue().toString());
                assertEquals("list:0", d.variableInfo().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("findTailRecursion".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
        };

        testClass("CyclicReferences_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // TODO "a".equals("b") should be resolved?
    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("methodB".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("CyclicReferences_2.methodA(paramB)", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("\"a\".equals(paramB)?CyclicReferences_2.methodA(paramB):\"b\".equals(paramB)",
                                d.currentValue().toString());
                    }
                }
            }
            if ("methodA".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:methodB>" : "true";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "\"b\".equals(paramA)?<m:methodB>:\"a\".equals(paramA)"
                                : "\"a\".equals(paramA)||\"b\".equals(paramA)";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("methodB".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.methodsOfOwnClassReached().contains(d.methodInfo()));
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                String expected = "/*inline methodB*/\"a\".equals(paramB)?CyclicReferences_2.methodA(paramB):\"b\".equals(paramB)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 0) {
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }

            }
            if ("methodA".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.methodsOfOwnClassReached().contains(d.methodInfo()));
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
            }
        };

        testClass("CyclicReferences_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("methodC".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "paramC".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:paramC>" : "\"b\"";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type String/*@Identity*/",
                                d.currentValue().toString());
                        assertDv(d, DV.TRUE_DV, Property.IDENTITY);
                        assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                }
            }
        };
        testClass("CyclicReferences_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("methodE".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Expression expression = d.statementAnalysis().stateData().valueOfExpression.get();
                    assertEquals("instance type boolean", expression.toString());
                    if (expression instanceof Instance i) {
                        assertTrue(i.identifier instanceof Identifier.ListOfIdentifiers);
                    } else fail();
                }
            }
        };
        testClass("CyclicReferences_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
