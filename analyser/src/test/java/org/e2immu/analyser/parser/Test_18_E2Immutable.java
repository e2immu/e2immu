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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_18_E2Immutable extends CommonTestRunner {
    public Test_18_E2Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("E2Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.E2Immutable_1";
        final String LEVEL2 = TYPE + ".level2";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_1".equals(d.methodInfo().name) &&
                    d.methodInfo().methodInspection.get().getParameters().size() == 2) {
                if (LEVEL2.equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        // we never know in the first iteration...
                        String expectValue = d.iteration() == 0
                                ? "2+<field:org.e2immu.analyser.testexample.E2Immutable_1.level2#parent2Param>"
                                : "2+parent2Param.level2";
                        assertEquals(expectValue, d.currentValue().debugOutput());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && pi.name.equals("parent2Param")) {
                    int expectImmu = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    assertEquals(expectImmu, d.getProperty(VariableProperty.IMMUTABLE));
                }
            }
        };
        testClass("E2Immutable_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("E2Immutable_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("strings4".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.CONTAINER));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {

            if ("E2Immutable_3".equals(d.methodInfo().name)) {
                FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                VariableInfo vi = d.getFieldAsVariable(strings4);
                assert vi != null;
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }

            if ("mingle".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                    VariableInfo vi = d.getFieldAsVariable(strings4);
                    assert vi != null;

                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                            vi.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                // this method returns the input parameter
                int expectMethodNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectMethodNN, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if ("getStrings4".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                    VariableInfo vi = d.getFieldAsVariable(strings4);
                    assert vi != null;
                }
                // method not null
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectNN, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("strings4", d.methodAnalysis().getSingleReturnValue().toString());
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }

                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_3".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fieldReference &&
                    "strings4".equals(fieldReference.fieldInfo.name)) {
                assertEquals("Set.copyOf(input4)", d.currentValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
            }

            if ("getStrings4".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr &&
                    "strings4".equals(fr.fieldInfo.name)) {
                int expectExtImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            assertEquals(MultiLevel.MUTABLE, set.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        };
        
        testClass("E2Immutable_3", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("E2Immutable_4", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("E2Immutable_5", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("E2Immutable_6", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                String expectValue = d.iteration() <= 1 ? "<m:setI>" : "<no return value>";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getMap7".equals(d.methodInfo().name) && "incremented".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:HashMap<String,SimpleContainer>>"
                            : "new HashMap<>(map7)/*this.size()==map7.size()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:HashMap<String,SimpleContainer>>" : "new HashMap<>(map7)/*this.size()==map7.size()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setI".equals(d.methodInfo().name)) {
                assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("i$0", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        testClass("E2Immutable_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("E2Immutable_8", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_9() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_9".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass("E2Immutable_9", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "sub".equals(fieldReference.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 2 ? "<f:sub>" : "new Sub()";
                    assertEquals(expectValue, d.currentValue().toString());

                    // no linked variables, but initially delayed
                    assertEquals(d.iteration() <= 2, d.variableInfo().getLinkedVariables().isDelayed());
                    String expectLinked = d.iteration() <= 2 ? "*" : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toDetailedString());

                    int expectBreakDelay = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectBreakDelay, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE_BREAK_DELAY));

                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                    int imm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    assertEquals(imm, d.getProperty(VariableProperty.IMMUTABLE));
                }
                if (d.variable() instanceof ReturnVariable) {
                    // no linked variables
                    assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                    assertEquals("", d.variableInfo().getLinkedVariables().toDetailedString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Sub".equals(d.typeInfo().simpleName)) {
                int imm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(imm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("sub".equals(d.fieldInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                } else {
                    assertEquals("new Sub()", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
            }
        };

        testClass("E2Immutable_10", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }
}
