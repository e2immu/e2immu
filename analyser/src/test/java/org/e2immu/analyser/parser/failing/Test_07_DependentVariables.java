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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_07_DependentVariables extends CommonTestRunner {
    public Test_07_DependentVariables() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {

                String read = d.variableInfo().getReadId();
                String assigned = d.variableInfo().getAssignmentIds().getLatestAssignment();

                if ("1".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertEquals("12", d.currentValue().toString());
                }
                if ("2".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) > 0);
                    assertEquals("12", d.variableInfo().getValue().toString());
                }
                if ("2".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                    assertEquals("13", d.currentValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("12", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("13", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[2]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("31", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array".equals(d.variableName())) {
                    assertEquals("4" + VariableInfoContainer.Level.EVALUATION, read);
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo) {
                    assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                    if ("1".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                }
                if ("b".equals(d.variableName())) {
                    assertEquals("a", d.variableInfo().getValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    if ("array[a]".equals(d.variableName())) {
                        fail("This variable should not be produced");
                    }
                    if ("array[b]".equals(d.variableName())) {
                        fail("This variable should not be produced");
                    }
                    if (("array[org.e2immu.analyser.testexample.DependentVariables.method2(int):0:a]").equals(d.variableName())) {
                        assertEquals("12", d.variableInfo().getValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("56", tv.getValue().toString());
            }
            if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("12", tv.getValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals("12", d.evaluationResult().value().toString());

                }
            }
        };

        // unused parameter in method1
        testClass("DependentVariables_0", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                int vars = d.iteration() == 0 ? 4 : 5;
                assertEquals(vars, d.evaluationResult().changeData().keySet().size());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getI".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:i>" : "instance type int";
                    assertEquals(expectValue, d.currentValue().minimalOutput());
                    String expectLv = d.iteration() == 0 ? "return getI:0,this.i:0" : "i$0:1,return getI:0,this.i:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<f:i>" : "i$0";
                    assertEquals(expectValue, d.currentValue().minimalOutput());
                    String expectLv = d.iteration() == 0 ? "return getI:0,this.i:0" : "i$0:1,return getI:0,this.i:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
            }
            if ("getX".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "xs".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<v:<f:xs>[index]>" : "nullable instance type X/*{L xs:0}*/";
                    assertEquals(expectValue, d.currentValue().minimalOutput());

                    String expectLv = switch (d.iteration()) {
                        case 0 -> "<f:xs>[index]:-1,return getX:0";
                        case 1 -> "return getX:0,this.xs:-1,xs[index]:-1";
                        default -> "return getX:0,this.xs:1,xs[index]:1";
                    };
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 0, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                }
                if (d.variable() instanceof ParameterInfo) {
                    assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if (d.variable() instanceof DependentVariable dv) {
                    assertEquals("xs[index]", dv.simpleName);
                    assertTrue(d.iteration() > 0);
                    assertEquals("nullable instance type X", d.currentValue().toString());
                    String expectLv = d.iteration() == 1 ? "return getX:-1,this.xs:0,xs[index]:0"
                            : "return getX:1,this.xs:0,xs[index]:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
            }
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "xs".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "p:-1,this.xs:0" : "p:3,this.xs:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("XS".equals(d.typeInfo().simpleName)) {
                assertEquals("Type org.e2immu.analyser.testexample.DependentVariables_1.X",
                        d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("xs".equals(d.fieldInfo().name)) {
                String expectLinked = d.iteration() == 0 ? "" : "p:3";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("i".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
            }
        };

        testClass("DependentVariables_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "xs".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expectLinked = d.iteration() == 0 ? "this.xs:0" : "xs";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("getX".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectLinked = d.iteration() == 0 ? "<f:xs>[index]:0,return getX:0" : "this.xs";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("xs".equals(d.fieldInfo().name)) {
                assertLinked(d, 1, "??", "xs");
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("XS".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        testClass("DependentVariables_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}