
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BinaryOperator;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.EqualsValue;
import org.e2immu.analyser.model.value.MethodValue;
import org.e2immu.analyser.model.value.SumValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Test_16_BasicCompanionMethods extends CommonTestRunner {

    public Test_16_BasicCompanionMethods() {
        super(true);
    }

    public static final String LIST_SIZE = "instance type java.util.ArrayList[0 == java.util.Collection.this.size()]";

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("<empty>", d.state().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "list".equals(d.variableName())) {
                Assert.assertEquals(LIST_SIZE, d.currentValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ValueChangeData valueChangeData = d.evaluationResult().getValueChangeStream()
                        .filter(e -> "list".equals(e.getKey().fullyQualifiedName())).map(Map.Entry::getValue).findFirst().orElseThrow();
                Assert.assertEquals(LIST_SIZE, valueChangeData.value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("false", d.evaluationResult().value.toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("4", d.methodAnalysis().getSingleReturnValue().toString());
                Assert.assertTrue(d.methodInfo().methodInspection.get().isStatic());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            MethodInfo size = collection.findUniqueMethod("size", 0);
            int modified = size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            Assert.assertEquals(Level.FALSE, modified);

            TypeInfo list = typeMap.get(List.class);
            MethodInfo listSize = list.findUniqueMethod("size", 0);
            Assert.assertEquals(Level.FALSE, listSize.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        };

        // two errors: two unused parameters
        testClass(List.of("BasicCompanionMethods_0"), 2, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeContextVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder()
                        .setReportWarnings(true)
                        .build());
    }


    public static final String INSTANCE_SIZE_1_CONTAINS = "instance type java.util.ArrayList[(java.util.List.this.contains(a) and 1 == java.util.Collection.this.size())]";
    public static final String TEST_1_RETURN_VARIABLE = "org.e2immu.analyser.testexample.BasicCompanionMethods_1.test()";

    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ValueChangeData valueChangeData = d.evaluationResult().getValueChangeStream()
                        .filter(e -> "list".equals(e.getKey().fullyQualifiedName())).map(Map.Entry::getValue).findFirst().orElseThrow();
                Assert.assertEquals(LIST_SIZE, valueChangeData.value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertTrue(d.haveValueChange("list")); // because of a modification
                Assert.assertEquals(INSTANCE_SIZE_1_CONTAINS, d.findValueChange("list").value().toString());
                Assert.assertTrue(d.haveLinkVariable("list", Set.of()));
            }
            if ("test".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_1, d.step());
                Assert.assertTrue(d.haveValueChange("b"));
                EvaluationResult.ValueChangeData valueChangeData = d.findValueChange("b");
                Assert.assertEquals("true", valueChangeData.value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertTrue(d.haveValueChange(TEST_1_RETURN_VARIABLE));
                EvaluationResult.ValueChangeData valueChangeData = d.findValueChange(TEST_1_RETURN_VARIABLE);
                Assert.assertEquals("true", valueChangeData.value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".compareTo(d.statementId()) <= 0 && "list".equals(d.variableName())) {
                Assert.assertEquals(INSTANCE_SIZE_1_CONTAINS, d.currentValue().toString());
            }
            if ("test".equals(d.methodInfo().name) && "3".compareTo(d.statementId()) <= 0 && "b".equals(d.variableName())) {
                Assert.assertEquals("true", d.currentValue().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertTrue(d.statusesAsMap().values().stream().allMatch(as -> as == AnalysisStatus.DONE || as == AnalysisStatus.RUN_AGAIN));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        // 3.0.0 unreachable
        // 3 condition evaluates to constant
        // @Constant expect
        testClass("BasicCompanionMethods_1", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {


        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId()) && "list".equals(d.variableName())) {
                Assert.assertEquals("instance type java.util.ArrayList[(java.util.List.this.contains(a) and 1 == java.util.Collection.this.size())]",
                        d.currentValue().toString());
            }
            if ("test".equals(d.methodInfo().name) && "2".equals(d.statementId()) && "list".equals(d.variableName())) {
                Assert.assertEquals("instance type java.util.ArrayList[(java.util.List.this.contains(a) and java.util.List.this.contains(b)" +
                                " and 2 == java.util.Collection.this.size())]",
                        d.currentValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }

        };
        testClass("BasicCompanionMethods_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test3() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo charSequence = typeMap.get(CharSequence.class);
            MethodInfo length = charSequence.findUniqueMethod("length", 0);
            Assert.assertTrue(length.methodAnalysis.isSet());
            Assert.assertEquals(Set.of(CompanionMethodName.Action.ASPECT, CompanionMethodName.Action.INVARIANT),
                    length.methodInspection.get().getCompanionMethods().keySet().stream().map(CompanionMethodName::action).collect(Collectors.toSet()));

            TypeInfo intTypeInfo = typeMap.getPrimitives().intTypeInfo;
            TypeInfo stringBuilder = typeMap.get(StringBuilder.class);
            MethodInfo appendInt = stringBuilder.typeInspection.get().methods().stream().filter(methodInfo -> "append".equals(methodInfo.name) &&
                    intTypeInfo == methodInfo.methodInspection.get().getParameters().get(0).parameterizedType.typeInfo).findFirst().orElseThrow();
            MethodInfo appendIntCompanion = appendInt.methodInspection.get().getCompanionMethods().values().stream().findFirst().orElseThrow();
            ReturnStatement returnStatement = (ReturnStatement) appendIntCompanion.methodInspection.get().getMethodBody().structure.statements.get(0);
            Assert.assertEquals("return post == prev + Integer.toString(i).length();\n", returnStatement.statementString(0, null));

            TypeInfo string = typeMap.getPrimitives().stringTypeInfo;
            MethodInfo stringLength = string.findUniqueMethod("length", 0);

            if (returnStatement.expression instanceof BinaryOperator eq &&
                    eq.rhs instanceof BinaryOperator plus &&
                    plus.rhs instanceof MethodCall lengthCall &&
                    lengthCall.object instanceof MethodCall toString &&
                    toString.object instanceof TypeExpression integer) {
                // check we have the same Integer type
                Assert.assertSame(integer.parameterizedType.typeInfo, typeMap.getPrimitives().integerTypeInfo);
                // check the length method
                Assert.assertSame(lengthCall.methodInfo, stringLength);
            }
            CompanionAnalysis appendCa = appendInt.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("append", CompanionMethodName.Action.MODIFICATION, "Len"));
            Value appendCompanionValue = appendCa.getValue();
            Assert.assertEquals("(java.lang.Integer.toString(java.lang.StringBuilder.append(int):0:i).length() + pre) == java.lang.CharSequence.this.length()",
                    appendCa.getValue().toString());
            if (appendCompanionValue instanceof EqualsValue eq && eq.lhs instanceof SumValue sum && sum.lhs instanceof MethodValue lengthCall) {
                Assert.assertSame(lengthCall.methodInfo, stringLength);
            } else Assert.fail();

            MethodInfo appendStr = stringBuilder.typeInspection.get().methods().stream().filter(methodInfo -> "append".equals(methodInfo.name) &&
                    string == methodInfo.methodInspection.get().getParameters().get(0).parameterizedType.typeInfo).findFirst().orElseThrow();
            MethodInfo appendStringCompanion = appendStr.methodInspection.get().getCompanionMethods().values().stream().findFirst().orElseThrow();
            ReturnStatement returnStatementStr = (ReturnStatement) appendStringCompanion.methodInspection.get().getMethodBody().structure.statements.get(0);
            Assert.assertEquals("return post == prev + (str == null ? 4 : str.length());\n", returnStatementStr.statementString(0, null));

            MethodInfo sbToString = stringBuilder.findUniqueMethod("toString", 0);
            CompanionAnalysis sbToStringCa = sbToString.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("toString", CompanionMethodName.Action.TRANSFER, "Len"));
            Assert.assertEquals("java.lang.CharSequence.this.length()", sbToStringCa.getValue().toString());
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "sb".equals(d.variableName())) {
                Assert.assertEquals("instance type java.lang.StringBuilder(abc)[5 == java.lang.CharSequence.this.length()]",
                        d.currentValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("false", d.evaluationResult().value.toString());
            }
            if ("test".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("true", d.evaluationResult().value.toString());
            }
        };

        testClass("BasicCompanionMethods_3", 2, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        testClass("BasicCompanionMethods_4", 2, 1, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("instance type java.util.HashSet[(org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true)" +
                                    " and 0 == java.util.Collection.this.size())]",
                            d.currentValue().toString());
                }
                if (Set.of("1", "4").contains(d.statementId())) {
                    Assert.assertEquals("In statement " + d.statementId(),
                            "instance type java.util.HashSet[(java.util.Set.this.contains(a)" +
                                    " and org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true)" +
                                    " and 1 == java.util.Collection.this.size())]",
                            d.currentValue().toString());
                }
                if ("7".equals(d.statementId())) {
                    Assert.assertEquals("instance type java.util.HashSet[(java.util.Set.this.contains(a)" +
                                    " and java.util.Set.this.contains(b)" +
                                    " and org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true)" +
                                    " and 2 == java.util.Collection.this.size())]",
                            d.currentValue().toString());
                }
            }
        };


        testClass("BasicCompanionMethods_5", 0, 7, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        final String PARAM = "org.e2immu.analyser.testexample.BasicCompanionMethods_6.test(Set<java.lang.String>):0:strings";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("instance type java.util.HashSet(" + PARAM + ")" +
                                    "[java.util.Collection.this.size() == " + PARAM + ".size()]",
                            d.currentValue().toString());
                }
            }
        };

        testClass("BasicCompanionMethods_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test7() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            MethodInfo clear = collection.findUniqueMethod("clear", 0);
            CompanionAnalysis clearCompanion = clear.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("clear", CompanionMethodName.Action.CLEAR, null));
            Assert.assertNotNull(clearCompanion);
            Assert.assertEquals(Level.TRUE, clear.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            TypeInfo set = typeMap.get(Set.class);
            MethodInfo setClear = set.findUniqueMethod("clear", 0);
            Assert.assertEquals(Level.TRUE, setClear.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        };

        final String PARAM = "org.e2immu.analyser.testexample.BasicCompanionMethods_7.test(Set<java.lang.String>):0:strings";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("instance type java.util.HashSet(" + PARAM + ")" +
                                    "[java.util.Collection.this.size() == " + PARAM + ".size()]",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("instance type java.util.HashSet(" + PARAM + ")" +
                                    "[(java.util.Set.this.contains(a) and " +
                                    "((1 + " + PARAM + ".size()) + (-(java.util.Collection.this.size()))) >= 0 and " +
                                    "(java.util.Collection.this.size() + (-(" + PARAM + ".size()))) >= 0)]",
                            d.currentValue().toString());
                }
                if ("4".equals(d.statementId())) {
                    Assert.assertEquals("instance type java.util.HashSet(" + PARAM + ")" +
                                    "[java.util.Collection.this.size() == " + PARAM + ".size()]",
                            d.currentValue().toString());
                }
            }
        };

        testClass("BasicCompanionMethods_7", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeContextVisitor(typeMapVisitor)
                .build());
    }
}
