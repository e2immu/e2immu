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
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_10_Identity extends CommonTestRunner {
    public Test_10_Identity() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo logger = typeMap.get(Logger.class);
        MethodInfo debug = logger.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "org.slf4j.Logger.debug(String,Object...)".equals(m.fullyQualifiedName))
                .findFirst().orElseThrow();
        assertEquals(DV.FALSE_DV, debug.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

        MethodInfo debug1 = logger.findUniqueMethod("debug", 1);
        assertEquals(DV.FALSE_DV, debug1.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, debug1.methodInspection.get().getParameters().get(0)
                .parameterAnalysis.get().getProperty(Property.NOT_NULL_PARAMETER));
    };

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertTrue(d.statementAnalysis().methodAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                if ("0".equals(d.statementId())) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertTrue(d.variableInfo().isRead());
                    ParameterizedType stringPt = d.variable().parameterizedType();
                    assertEquals("Type String", stringPt.toString());
                    assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV,
                            d.context().getAnalyserContext().typeImmutable(stringPt));

                    String expect = d.iteration() == 0 ? "<p:s>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expect, d.currentValue().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, IMMUTABLE);
                    assertDv(d, 1, MultiLevel.CONTAINER_DV, CONTAINER);
                    assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);

                } else if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                    assertEquals("1" + Stage.EVALUATION, d.variableInfo().getReadId());

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    String expectValue = d.iteration() == 0 ? "<p:s>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                } else fail();
            }
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ReturnVariable) {
                if ("1".equals(d.statementId())) {
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("LOGGER".equals(d.fieldInfo().name) && "Identity_0".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                // means: not linked to parameters of setters/constructors
                assertDv(d, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name)) {
                String srv = d.iteration() == 0 ? "<m:idem>" : "s";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 1, DV.TRUE_DV, Property.IDENTITY);
                assertDv(d, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Identity_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
        };

        testClass("Identity_0", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("idem2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:idem>" : "s/*@NotNull*/";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "s:-1" : "s:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, CONTAINER_RESTRICTION);
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, CONTAINER);
                    }
                }
                if (d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.getProperty(Property.CONTEXT_NOT_NULL).isDone());
                    }
                    if ("1".equals(d.statementId())) {
                        // because the @NotNull situation of the parameter of idem has not been resolved yet, there cannot be a
                        // delay resolved here
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem2".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION), "iteration " + d.iteration());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d, 1, DV.TRUE_DV, Property.IDENTITY);
                String expect = d.iteration() == 0 ? "<m:idem>" : "s";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("idem2".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 2, DV.TRUE_DV, Property.IDENTITY);

                String expect = d.iteration() < 2 ? "<m:idem2>" : "s/*@NotNull*/";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };

        testClass("Identity_1", 0, 0, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test_2() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("idem3")) {
                if (d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                    // there is an explicit @NotNull on the first parameter of debug
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("s:0", d.variableInfo().getLinkedVariables().toString());
                        assertValue(d, "<m:equals>?<m:idem>:<p:s>", "\"a\".equals(s)?<m:idem>:s", "s");
                        assertDv(d, 2, DV.TRUE_DV, IDENTITY);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("idem3".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 3 ? "<m:idem3>" : "s";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, DV.TRUE_DV, Property.IDENTITY);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("idem2".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("idem".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        testClass("Identity_2", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("idem4".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                // double property wrapper
                String expect = d.iteration() == 0 ? "<m:equals>?<m:idem>:<p:s>"
                        : d.iteration() == 1 ? "\"a\".equals(s)?<m:idem>:s": "s";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("idem2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "s:-1" : "s:1";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("idem4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        // 0 because of the "?...:" s in the ifFalse part of the inline conditional
                        assertEquals("s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("idem2".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 2, DV.TRUE_DV, Property.IDENTITY);
            }
            if ("idem4".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 3, DV.TRUE_DV, Property.IDENTITY);
            }
        };

        testClass("Identity_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("log".equals(d.methodInfo().name)) {
                if ("LogMe".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.IDENTITY));
                    assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.methodAnalysis().getProperty(Property.NOT_NULL_EXPRESSION));
                    assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(Property.INDEPENDENT));

                    ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
                    assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));
                }
            }
        };
        testClass("Identity_4", 1, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());

    }
}
