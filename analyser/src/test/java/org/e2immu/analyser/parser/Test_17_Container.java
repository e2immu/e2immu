package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Test_17_Container extends CommonTestRunner {
    public Test_17_Container() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_0";
        final String S = TYPE + ".s";
        final String P = TYPE + ".setS(Set<String>,String):0:p";
        final String S0 = TYPE + ".s$0$0-E";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS".equals(d.methodInfo().name)) {
                if (P.equals(d.variableName()) && "0".equals(d.statementId())) {
                    int expect = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expect, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (P.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("0-E", d.variableInfo().getReadId());
                        Assert.assertTrue(d.variableInfoContainer().isReadInThisStatement());

                        Assert.assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals("0-E", d.variableInfo().getReadId());
                        Assert.assertFalse(d.variableInfoContainer().isReadInThisStatement());

                        Assert.assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                        Assert.assertEquals(Level.DELAY, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if (S.equals(d.variableName()) && "1".equals(d.statementId())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (S0.equals(d.variableName()) && "1".equals(d.statementId())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("p", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                int expect = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            Assert.assertEquals(MultiLevel.MUTABLE, set.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        };
        testClass("Container_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_1";
        final String S = TYPE + ".s";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name) && "Container_1".equals(d.fieldInfo().owner.simpleName)) {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (S.equals(d.variableName()) && "addToS".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            // POTENTIAL NULL POINTER EXCEPTION
            if ("addToS".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.iteration() > 0) {
                    Assert.assertNotNull(d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addToS".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        // warning to expect: the potential null pointer exception of s
        testClass("Container_1", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_2";
        final String S = TYPE + ".s";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (S.equals(d.variableName()) && "addToS".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                    Assert.assertEquals("Statement " + d.statementId(),
                            Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addToS".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertEquals("null!=<field:org.e2immu.analyser.testexample.Container_2.s>", d.condition().toString());
                } else {
                    Assert.assertEquals("null!=s", d.condition().toString());
                }
            }
        };

        testClass("Container_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_3";
        final String S = TYPE + ".s";
        final String S_0 = TYPE + ".s$0";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertEquals("<method:java.util.Set.add(E)>", d.evaluationResult().value().toString());
                } else {
                    Assert.assertEquals("org.e2immu.analyser.testexample.Container_3.s$0.add(s3)", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("set3".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        if (d.iteration() == 0) {
                            Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                        } else {
                            Assert.assertEquals("this.s,org.e2immu.analyser.testexample.Container_3.s$0",
                                    d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                    if ("1.0.0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        Assert.assertEquals("Statement " + d.statementId() + ", variable " + d.variableName(),
                                Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));

                        if (d.iteration() == 0) {
                            Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                        } else {
                            Assert.assertEquals("this.s,org.e2immu.analyser.testexample.Container_3.s$0",
                                    d.variableInfo().getLinkedVariables().toString());
                        }
                    }

                }
                // this one tests the linking mechanism from the field into the local copy
                if (S.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());
                        if (d.iteration() == 0) {
                            Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                        } else {
                            Assert.assertEquals("org.e2immu.analyser.testexample.Container_3.s$0",
                                    d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        if (d.iteration() == 0) {
                            Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                        } else {
                            Assert.assertEquals("org.e2immu.analyser.testexample.Container_3.s$0",
                                    d.variableInfo().getLinkedVariables().toString());
                            // set3 -> s$0 -> this.s (-> s$0)
                            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                        }
                    }
                    if ("1".equals(d.statementId())) {
                        // here we merge with the info in "0"
                        if (d.iteration() == 0) {
                            Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                        } else {
                            Assert.assertEquals("org.e2immu.analyser.testexample.Container_3.s$0",
                                    d.variableInfo().getLinkedVariables().toString());
                            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                        }
                    }
                }
                if (S_0.equals(d.variableName())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("this.s", d.variableInfo().getLinkedVariables().toString());
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                    } else if ("1.0.0".equals(d.statementId())) {
                        Assert.assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                    } else if ("1".equals(d.statementId())) {
                        Assert.assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("1".equals(d.statementId()) && d.iteration() > 0) {
                Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (S.equals(d.fieldInfo().fullyQualifiedName())) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Container_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo param0 = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE,
                    param0.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            final String TYPE = "org.e2immu.analyser.testexample.Container_4";
            final String IN = TYPE + ".crossModify(Set<String>,Set<String>):0:in";
            final String S = TYPE + ".s";

            if ("crossModify".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (IN.equals(d.variableName())) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }

            if ("m1".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p0 && "modified".equals(p0.name)) {
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }

            if ("m2".equals(d.methodInfo().name) && "toModifyM2".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "modified2";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("m2".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if ("m1".equals(d.methodInfo().name) && S.equals(d.variableName()) && "1".equals(d.statementId())) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("m1".equals(d.methodInfo().name)) {
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
            if ("m2".equals(d.methodInfo().name)) {
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
            if ("crossModify".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        testClass("Container_4", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Container_5".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p && "coll5".equals(p.name)) {
                Assert.assertEquals("nullable? instance type Collection<String>", d.currentValue().toString());
                Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY));
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY_RESOLVED));
                }
                if ("1".equals(d.statementId())) {
                    int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectModified, d.getProperty(VariableProperty.MODIFIED_VARIABLE));

                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY));
                    int expectDelayResolved = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectDelayResolved, d.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY_RESOLVED));
                }
            }
            if ("addAll5".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr
                    && "list".equals(fr.fieldInfo.name)) {
                Assert.assertEquals(d.iteration() == 0 ? Level.DELAY : Level.TRUE,
                        d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Container_5".equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().size() == 1) {
                ParameterAnalysis coll5 = d.parameterAnalyses().get(0);
                Assert.assertEquals(Level.FALSE, coll5.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("addAll5".equals(d.methodInfo().name)) {
                ParameterAnalysis collection = d.parameterAnalyses().get(0);
                int expectModifiedParam = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModifiedParam, collection.getProperty(VariableProperty.MODIFIED_VARIABLE));
                int expectModifiedMethod = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModifiedMethod, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            MethodInfo forEach = collection.findUniqueMethod("forEach", 1);
            Assert.assertSame(typeMap.getPrimitives().voidTypeInfo, forEach.returnType().typeInfo);

            TypeInfo hashSet = typeMap.get(HashSet.class);
            MethodInfo constructor1 = hashSet.typeInspection.get().constructors().stream()
                    .filter(m -> m.methodInspection.get().getParameters().size() == 1)
                    .filter(m -> m.methodInspection.get().getParameters().get(0).parameterizedType.typeInfo == collection)
                    .findAny().orElseThrow();
            ParameterInfo param1Constructor1 = constructor1.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE, param1Constructor1.parameterAnalysis.get()
                    .getProperty(VariableProperty.MODIFIED_VARIABLE));
        };


        testClass("Container_5", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


}
