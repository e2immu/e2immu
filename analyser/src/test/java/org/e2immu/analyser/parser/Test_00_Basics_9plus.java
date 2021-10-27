
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
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
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.evaluationResult().evaluationContext().getProperty(d.evaluationResult().value(),
                                VariableProperty.NOT_NULL_EXPRESSION, false, false));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isFact".equals(d.methodInfo().name) || "isKnown".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER),
                        "Method: " + d.methodInfo().name);
            }
            if ("setContainsValueHelper".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));

                assertEquals("Basics_9.isFact(containsE)?containsE:!Basics_9.isKnown(true)&&retVal&&size>=1",
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                        "class is " + d.methodAnalysis().getSingleReturnValue().getClass());
            }
            if ("test1".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));


                assertEquals("Basics_9.isFact(contains)?contains:!Basics_9.isKnown(true)&&isEmpty",
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                        "class is " + d.methodAnalysis().getSingleReturnValue().getClass());

                ParameterAnalysis contains = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.NOT_INVOLVED, contains.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo contains && "contains".equals(contains.name)) {
                    assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                    assertEquals("contains:0,return test1:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        testClass("Basics_9", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("getString".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                    String expectValue = d.iteration() == 0 ? "<f:string>" : "instance type String";
                    assertEquals(expectValue, d.currentValue().toString());

                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                ParameterAnalysis in = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, in.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals("in", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        };
        testClass("Basics_10", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_11() throws IOException {
        final String NULLABLE_INSTANCE = "nullable instance type String/*@Identity*/";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                String value = d.currentValue().toString();
                int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);

                if (d.variable() instanceof ParameterInfo in1 && "in".equals(in1.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals(NULLABLE_INSTANCE, value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        assertEquals(expectValue, value);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        assertEquals(expectValue, value);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                }
                if ("s1".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("in", value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s1>" : "in";
                        assertEquals(expectValue, value);
                    }
                }
                if ("s2".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertEquals("in", value);
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s2>" : "in";
                        assertEquals(expectValue, value);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                }
            }
        };
        // warning: out potential null pointer (x1) and assert always true (x1)
        testClass("Basics_11", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_12() throws IOException {
        testClass("Basics_12", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    linked variables is empty all around because String is @E2Immutable
     */
    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                int enn = d.getProperty(VariableProperty.EXTERNAL_NOT_NULL);
                int nne = d.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
                int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);
                int cm = d.getProperty(VariableProperty.CONTEXT_MODIFIED);
                String value = d.currentValue().toString();
                String linkedVariables = d.variableInfo().getLinkedVariables().toString();

                if (d.variable() instanceof ParameterInfo in1 && "in1".equals(in1.name)) {
                    if ("0".equals(d.statementId())) {
                        // means: there are no fields, we have no opinion, right from the start ->
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(MultiLevel.NULLABLE, nne);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(Level.FALSE, cm);
                        assertEquals("nullable instance type String/*@Identity*/", value);
                        assertEquals("a:0,in1:0", linkedVariables); // symmetrical!
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(Level.FALSE, cm);
                        assertEquals("b:0,in1:0,return test:0", linkedVariables);
                    }
                }
                if (d.variable() instanceof ParameterInfo in2 && "in2".equals(in2.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(MultiLevel.NULLABLE, nne);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(Level.FALSE, cm);
                        assertEquals("in2:0", linkedVariables);
                        assertEquals("nullable instance type String", value);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                        assertEquals(Level.FALSE, cm);
                        assertEquals("a:0,in2:0", linkedVariables);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("<return value>", value);
                        assertEquals("return test:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(MultiLevel.NULLABLE, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(Level.FALSE, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("in1", value);
                        assertEquals("b:0,in1:0,return test:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(MultiLevel.NULLABLE, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(Level.FALSE, cm);
                    }
                }
                if ("a".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("in1", value);
                        assertTrue(d.variableInfo().valueIsSet());
                        assertEquals("a:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(MultiLevel.NULLABLE, nne);
                        assertEquals(Level.FALSE, cm);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                        assertTrue(d.variableInfo().valueIsSet());
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(Level.FALSE, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                    }
                }
                if ("b".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("a:0,b:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(MultiLevel.NULLABLE, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(Level.FALSE, cm);
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("b:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        assertEquals(Level.FALSE, cm);
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("b:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(Level.FALSE, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE, cnn);
                        assertEquals(Level.FALSE, cm);
                        assertEquals("in1", value);
                        assertEquals("b:0,in1:0,return test:0", linkedVariables);
                        assertEquals(MultiLevel.NOT_INVOLVED, enn);
                    }
                }
            }
        };
        testClass("Basics_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    copy of the SetOnce delay problems (20210304)
     */
    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);
            int enn = d.getProperty(VariableProperty.EXTERNAL_NOT_NULL);
            if ("setT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "t".equals(p.name)) {
                    assertEquals(Level.TRUE, d.getProperty(VariableProperty.IDENTITY));
                    int expectContainer;
                    if ("0.0.0".equals(d.statementId())) {
                        expectContainer = Level.TRUE;
                    } else {
                        expectContainer = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    }
                    assertEquals(expectContainer, d.getProperty(VariableProperty.CONTAINER), "Statement: " + d.statementId());
                }
            }
            if ("getT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference t && t.fieldInfo.name.equals("t")) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(MultiLevel.NULLABLE, initial.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        assertEquals(MultiLevel.NULLABLE, eval.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }

                    String expectValue = d.iteration() == 0 ? "<f:t>" : "nullable instance type T";
                    assertEquals(expectValue, d.currentValue().toString());

                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectEnn, enn);

                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    } else {
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectCnn, cnn);
                    }
                }
                if ("t$0".equals(d.variableInfo().variable().simpleName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                    assertEquals(MultiLevel.NULLABLE, enn);
                }
            }
            if (d.iteration() > 1) {
                assertTrue(enn != Level.DELAY);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(Level.TRUE, p0.getProperty(VariableProperty.CONTAINER));
            }
        };

        testClass("Basics_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
