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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
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

public class Test_04_NotNull_AAPI extends CommonTestRunner {
    public Test_04_NotNull_AAPI() {
        super(true);
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("isStrictPrefix".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:goTo>"
                                : "-1-(instance type int)+prefix.length>=0&&(null==``node`.map`.get(nullable instance type String)||null==``node`.map`)?null:-1-(instance type int)+prefix.length>=0?``node`.map`.get(nullable instance type String):`root`";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 2 ? "<m:goTo>"
                        : "/*inline goTo*/-1-(instance type int)+upToPosition>=0&&(null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1.get(nullable instance type String)||null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1)?null:-1-(instance type int)+upToPosition>=0?null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String):root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
            }
        };
        testClass("NotNull_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .build());
    }


    // the normal Analyser setting would be computeFieldAnalyserAcrossAllMethods, rather
    // than ComputeContextPropertiesOverAllMethods. (used to crash before better logic in GreaterThanZero and And...)
    @Test
    public void test_3_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("newTrieNode".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        String linked = d.iteration() <= 1 ? "node.map:-1,node:0,s:-1,this.root:-1" : "node:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }

                    if ("1.0.2".equals(d.statementId())) {
                        String linked = d.iteration() <= 1 ? "newTrieNode:0,node.map:-1,s:-1,this.root:-1" : "newTrieNode:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("1.0.1.1.1".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 2, d.statementAnalysis().flowData().isUnreachable());
                }
            }
        };
        testClass("NotNull_3", 7, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)
                .build());
    }


    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() <= 2 ? "<null-check>?null:<f:node.data>" : "null";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    EvaluationResult.ChangeData cd = d.findValueChangeByToString("node");
                    DV cnn = cd.getProperty(Property.CONTEXT_NOT_NULL);
                    assertNotEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:goTo>" : "upToPosition>=strings.length?null:`root`";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("isStrictPrefix".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:goTo>" : "null";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 2
                            ? "upToPosition>=strings.length?null:<f:root>"
                            : "upToPosition>=strings.length?null:root";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    // the contribution of "root" travels to the return variable
                    // it'll be used only
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 2 ? "<m:goTo>" : "/*inline goTo*/upToPosition>=strings.length?null:root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        testClass("NotNull_4", 1, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }


    @Test
    public void test_4_1() throws IOException {
        testClass("NotNull_4_1", 2, 1, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

}
