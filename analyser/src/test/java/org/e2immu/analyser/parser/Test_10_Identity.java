package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
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
        assertEquals(Level.FALSE, debug.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

        MethodInfo debug1 = logger.findUniqueMethod("debug", 1);
        assertEquals(Level.FALSE, debug1.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, debug1.methodInspection.get().getParameters().get(0)
                .parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL_PARAMETER));
    };

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals(d.iteration() > 0,
                        d.statementAnalysis().methodAnalysis.methodLevelData().linksHaveBeenEstablished.isSet());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    assertTrue(d.variableInfo().isRead());
                    if (d.iteration() > 0) {
                        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                        assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTAINER));

                        // there is an explicit @NotNull on the first parameter of debug
                    } // else: nothing much happening in the first iteration, because LOGGER is still unknown!

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                } else if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                    assertEquals("1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                    String expectValue = d.iteration() == 0 ? "<p:s>" : "nullable instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());

                    int expectNotNullExpression = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNotNullExpression, d.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                } else fail();
            }
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ReturnVariable) {
                if ("1".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("LOGGER".equals(d.fieldInfo().name) && "Identity_0".equals(d.fieldInfo().owner.simpleName)) {
                assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem".equals(d.methodInfo().name)) {
                    VariableInfo vi = d.getReturnAsVariable();
                    assertFalse(vi.hasProperty(VariableProperty.MODIFIED_VARIABLE));

                    if (d.iteration() > 1) {
                        assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());
                        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                                methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
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
            if ("idem2".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.getProperty(VariableProperty.CONTEXT_NOT_NULL) != Level.DELAY);
                }
                if ("1".equals(d.statementId())) {
                    // because the @NotNull situation of the parameter of idem has not been resolved yet, there cannot be a
                    // delay resolved here
                    int expectContextNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectContextNotNull, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem2".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertNull(d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION), "iteration " + d.iteration());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectIdentity, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));
                if (d.iteration() > 0) {
                    assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());
                } else {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                }
                int expectParamNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectParamNotNull, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
            if ("idem2".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectIdentity, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));
                if (d.iteration() > 0) {
                    assertEquals("s/*@Immutable,@NotNull*/", d.methodAnalysis().getSingleReturnValue().toString());
                } else {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                }
                int expectParamNotNull = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectParamNotNull, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL_PARAMETER));
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
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem3".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && d.iteration() > 0) {
                Expression value = d.statementAnalysis().stateData.valueOfExpression.get();
                assertTrue(value instanceof PropertyWrapper);
                Expression valueInside = ((PropertyWrapper) value).expression;
                assertTrue(valueInside instanceof PropertyWrapper);
                Expression valueInside2 = ((PropertyWrapper) valueInside).expression;
                assertTrue(valueInside2 instanceof VariableExpression);
                // check that isInstanceOf bypasses the wrappers
                assertTrue(value.isInstanceOf(VariableExpression.class));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(value, VariableProperty.NOT_NULL_EXPRESSION));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem3".equals(d.methodInfo().name)) {
                    assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                    assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

                    VariableInfo vi = d.getReturnAsVariable();
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                    assertEquals("s/*@Immutable,@NotNull*//*@Immutable,@NotNull*/",
                            d.methodAnalysis().getSingleReturnValue().toString());

                    // combining both, we obtain:
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("idem2".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("idem".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
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
                String expect = d.iteration() == 0 ? "<m:equals>?<m:idem>:<p:s>" : "s/*@Immutable,@NotNull*//*@Immutable,@NotNull*/";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if ("idem4".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
                int expectIdentity = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectIdentity, methodAnalysis.getProperty(VariableProperty.IDENTITY));
            }
        };

        testClass("Identity_3", 0, 0, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
