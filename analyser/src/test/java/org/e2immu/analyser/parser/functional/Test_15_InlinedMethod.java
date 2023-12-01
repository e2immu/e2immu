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

package org.e2immu.analyser.parser.functional;

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_15_InlinedMethod extends CommonTestRunner {
    public Test_15_InlinedMethod() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        TypeMapVisitor typeMapVisitor = d -> {
            MethodInfo unaryMinusInt = d.typeMap().getPrimitives().unaryMinusOperatorInt();
            assertEquals("int.-(int)", unaryMinusInt.fullyQualifiedName());
        };
        testClass("InlinedMethod_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    /*
    tests that the inlined method leaves no parameter lingering (assert statement in StatementAnalysis.initialValueForReading)
     */
    @Test
    public void test_1() throws IOException {
        testClass("InlinedMethod_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // the final field "i" is linked to the parameter "rr"
    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("plus".equals(d.methodInfo().name)) {
                // IMPORTANT: current implementation (202310) does not accept fields
                String expected = d.iteration() == 0 ? "<m:plus>" : "i+r";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference31".equals(d.methodInfo().name) && d.iteration() > 1) {
                assertEquals("/*inline difference31*/-this.plus(1)+this.plus(3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name) && d.iteration() > 1) {
                assertEquals("0", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlinedMethod_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("sum".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:sum>" : "/*inline sum*/4+j";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlinedMethod_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("expand1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:sum5>" : "(new InlinedMethod_5(6)).sum(5)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("expand3".equals(d.methodInfo().name)) {
                if ("il5".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = "new InlinedMethod_5(a)";
                        assertEquals(expect, d.currentValue().toString());
                        if (d.iteration() > 1) {
                            if (d.currentValue() instanceof ConstructorCall newObject) {
                                assertEquals(1, newObject.parameterExpressions().size());
                            } else fail();
                        }
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("sum".equals(d.methodInfo().name)) {
                // IMPORTANT: current implementation (202310) does not accept fields
                String expect = d.iteration() == 0 ? "<m:sum>" : "i+j";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("sum5".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? "<m:sum5>" : "/*inline sum5*/this.sum(5)";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("expand3".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? "<m:expand3>" : "/*inline expand3*/(new InlinedMethod_5(a)).sum(b)";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlinedMethod_5", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("sum".equals(d.methodInfo().name)) {
                // IMPORTANT: current implementation (202310) does not accept fields
                String expect = d.iteration() == 0 ? "<m:sum>" : "i$0+j";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("expandSum".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? "<m:expandSum>" : "/*inline expandSum*/this.sum(3)*k";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }

            if ("expand".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    String expected = "constructor-to-instance@Method_expand_1-E;srv@Method_expand";
                    assertEquals(expected, d.methodAnalysis().getSingleReturnValue().causesOfDelay().toString());
                } else if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertFalse(inlinedMethod.containsVariableFields());
                    assertEquals("variableField$1.getI()", inlinedMethod.expression().toString());
                } else fail();

                String expect = d.iteration() <= 1 ? "<m:expand>" : "/*inline expand*/variableField$1.getI()";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlinedMethod_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // uses InlinedMethod_6
    @Test
    public void test_7() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expand".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("/*inline expand*/variableField$1.getI()",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("doNotExpand".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("/*inline doNotExpand*/variableField$1.getI()",
                        d.methodAnalysis().getSingleReturnValue().toString());
                // non-modifying, so inlined!
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
            }
        };
        testClass(List.of("InlinedMethod_6", "InlinedMethod_7"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "<m:get>+<m:get>"
                        : "`org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):0:input[org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):1:index]`+`org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):0:input[org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):1:index]`";
                assertEquals(expected, d.evaluationResult().value().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:get>" : "/*inline get*/input[index]$0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 0) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("index, input, input[index]$0", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:method>"
                        : "/*inline method*/`org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):0:input[org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):1:index]`+`org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):0:input[org.e2immu.analyser.parser.functional.testexample.InlinedMethod_9.get(String[],int):1:index]`";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 2) assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
            }
        };
        testClass("InlinedMethod_9", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
