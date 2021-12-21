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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_10_Identity extends CommonTestRunner {
    public Test_10_Identity() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo logger = typeMap.get(Logger.class);
        MethodInfo debug = logger.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "org.slf4j.Logger.debug(java.lang.String,java.lang.Object...)".equals(m.fullyQualifiedName))
                .findFirst().orElseThrow();
        assertEquals(Level.FALSE_DV, debug.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

        MethodInfo debug1 = logger.findUniqueMethod("debug", 1);
        assertEquals(Level.FALSE_DV, debug1.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, debug1.methodInspection.get().getParameters().get(0)
                .parameterAnalysis.get().getProperty(Property.NOT_NULL_PARAMETER));
    };

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertTrue(d.statementAnalysis().methodAnalysis.methodLevelData().linksHaveBeenEstablished());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    assertTrue(d.variableInfo().isRead());
                    if (d.iteration() > 0) {
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                        assertEquals(Level.TRUE_DV, d.getProperty(Property.CONTAINER));

                        // there is an explicit @NotNull on the first parameter of debug
                    } // else: nothing much happening in the first iteration, because LOGGER is still unknown!

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));

                } else if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                    assertEquals("1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));

                    String expectValue = d.iteration() == 0 ? "<p:s>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("return idem:0,s:0", d.variableInfo().getLinkedVariables().toString());

                    int expectNotNullExpression = d.iteration() <= 2 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNotNullExpression, d.getProperty(Property.NOT_NULL_PARAMETER));
                } else fail();
            }
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ReturnVariable) {
                if ("1".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("LOGGER".equals(d.fieldInfo().name) && "Identity_0".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("LOGGER:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem".equals(d.methodInfo().name)) {
                    VariableInfo vi = d.getReturnAsVariable();
                    assertFalse(vi.hasProperty(Property.MODIFIED_VARIABLE));

                    if (d.iteration() > 1) {
                        assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());
                        assertEquals(Level.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                                methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
                        assertEquals(Level.TRUE_DV, methodAnalysis.getProperty(Property.IDENTITY));
                    }
                }
            }
        };

        testClass("Identity_0", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
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
                        String expectLv = d.iteration() == 0 ? "return idem2:0,s:-1" : "return idem2:0,s:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.getProperty(Property.CONTEXT_NOT_NULL).isDone());
                    }
                    if ("1".equals(d.statementId())) {
                        // because the @NotNull situation of the parameter of idem has not been resolved yet, there cannot be a
                        // delay resolved here
                        int expectContextNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectContextNotNull, d.getProperty(Property.CONTEXT_NOT_NULL));
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
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectIdentity, d.methodAnalysis().getProperty(Property.IDENTITY));
                if (d.iteration() > 0) {
                    assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());
                } else {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                }
                int expectParamNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectParamNotNull, d.parameterAnalyses().get(0).getProperty(Property.NOT_NULL_PARAMETER));
            }
            if ("idem2".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectIdentity, d.methodAnalysis().getProperty(Property.IDENTITY));
                if (d.iteration() >= 1) {
                    assertEquals("s/*@NotNull*/", d.methodAnalysis().getSingleReturnValue().toString());
                } else {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                }
                int expectParamNotNull = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectParamNotNull, d.parameterAnalyses().get(0).getProperty(Property.NOT_NULL_PARAMETER));
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
            if (d.methodInfo().name.equals("idem3") && d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                // there is an explicit @NotNull on the first parameter of debug
                if ("0".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                }
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem3".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && d.iteration() > 1) {
                Expression value = d.statementAnalysis().stateData.valueOfExpression.get();
                assertTrue(value instanceof PropertyWrapper);
                Expression valueInside = ((PropertyWrapper) value).expression();
                assertTrue(valueInside instanceof PropertyWrapper);
                Expression valueInside2 = ((PropertyWrapper) valueInside).expression();
                assertTrue(valueInside2 instanceof VariableExpression);
                // check that isInstanceOf bypasses the wrappers
                assertTrue(value.isInstanceOf(VariableExpression.class));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(value, Property.NOT_NULL_EXPRESSION));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() >= 1) {
                if ("idem3".equals(d.methodInfo().name)) {
                    assertEquals(Level.TRUE_DV, methodAnalysis.getProperty(Property.IDENTITY));
                    assertEquals(Level.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));

                    VariableInfo vi = d.getReturnAsVariable();
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.NOT_NULL_EXPRESSION));

                    assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());

                    // combining both, we obtain:
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                            methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
                }
            }
            if ("idem2".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, p0.getProperty(Property.MODIFIED_VARIABLE));
            }
            if ("idem".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, p0.getProperty(Property.MODIFIED_VARIABLE));
            }
        };

        testClass("Identity_2", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
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
                String expect = d.iteration() == 0 ? "<m:equals>?<m:idem>:<p:s>" : "s";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if ("idem4".equals(d.methodInfo().name)) {
                assertEquals(d.falseFrom1(), methodAnalysis.getProperty(Property.MODIFIED_METHOD));
                int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectIdentity, methodAnalysis.getProperty(Property.IDENTITY));
            }
        };

        testClass("Identity_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("log".equals(d.methodInfo().name)) {
                if ("LogMe".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertEquals(Level.TRUE_DV, d.methodAnalysis().getProperty(Property.IDENTITY));
                    assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));

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
