package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.IOException;

public class Test_10_Identity extends CommonTestRunner {
    public Test_10_Identity() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo logger = typeMap.get(Logger.class);
        MethodInfo debug = logger.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "org.slf4j.Logger.debug(java.lang.String,java.lang.Object...)".equals(m.fullyQualifiedName))
                .findFirst().orElseThrow();
        Assert.assertEquals(Level.FALSE, debug.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

        MethodInfo debug1 = logger.findUniqueMethod("debug", 1);
        Assert.assertEquals(Level.FALSE, debug1.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, debug1.methodInspection.get().getParameters().get(0)
                .parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL_VARIABLE));
    };

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() > 0,
                        d.statementAnalysis().methodAnalysis.methodLevelData().linksHaveBeenEstablished.isSet());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("idem") && d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                if ("0".equals(d.statementId())) {

                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    Assert.assertTrue(d.variableInfo().isRead());
                    if (d.iteration() > 0) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTAINER));

                        // there is an explicit @NotNull on the first parameter of debug
                    } // else: nothing much happening in the first iteration, because LOGGER is still unknown!

                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY));
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY_RESOLVED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                } else if ("1".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isRead());
                    Assert.assertEquals("1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());

                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                    String expectValue = d.iteration() == 0 ?
                            "<parameter:org.e2immu.analyser.testexample.Identity_0.idem(java.lang.String):0:s>" :
                            "nullable? instance type String";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                } else Assert.fail();
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("LOGGER".equals(d.fieldInfo().name) && "Identity_0".equals(d.fieldInfo().owner.simpleName)) {
                Assert.assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem".equals(d.methodInfo().name)) {
                    VariableInfo vi = d.getReturnAsVariable();
                    Assert.assertFalse(vi.hasProperty(VariableProperty.MODIFIED_VARIABLE));

                    Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
                    Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                    Assert.assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());
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
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem2".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                // false because static method
                Assert.assertEquals(Level.FALSE, d.getThisAsVariable().getProperty(VariableProperty.METHOD_CALLED));
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem2".equals(d.methodInfo().name)) {
                    Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
                    Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                    Assert.assertEquals("s/*@Immutable,@NotNull*/", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        testClass("Identity_1", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
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
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_VARIABLE));
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                } else if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("idem3".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && d.iteration() > 0) {
                Expression value = d.statementAnalysis().stateData.getValueOfExpression();
                Assert.assertTrue(value instanceof PropertyWrapper);
                Expression valueInside = ((PropertyWrapper) value).expression;
                Assert.assertTrue(valueInside instanceof PropertyWrapper);
                Expression valueInside2 = ((PropertyWrapper) valueInside).expression;
                Assert.assertTrue(valueInside2 instanceof VariableExpression);
                // check that isInstanceOf bypasses the wrappers
                Assert.assertTrue(value.isInstanceOf(VariableExpression.class));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(value, VariableProperty.NOT_NULL_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem3".equals(d.methodInfo().name)) {
                    Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                    Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

                    VariableInfo vi = d.getReturnAsVariable();
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL_VARIABLE));

                    Assert.assertEquals("s/*@Immutable,@NotNull*//*@Immutable,@NotNull*/",
                            d.methodAnalysis().getSingleReturnValue().toString());

                    // combining both, we obtain:
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("idem2".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("idem".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
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
                String expect = d.iteration() == 0 ? "<method:java.lang.String.equals(java.lang.Object)>?<method:org.e2immu.analyser.testexample.Identity_3.idem(java.lang.String)>:<parameter:org.e2immu.analyser.testexample.Identity_3.idem4(java.lang.String):0:s>"
                        : "s/*@Immutable,@NotNull*//*@Immutable,@NotNull*/";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            if (d.iteration() > 0) {
                if ("idem4".equals(d.methodInfo().name)) {
                    Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
                    Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                }
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
