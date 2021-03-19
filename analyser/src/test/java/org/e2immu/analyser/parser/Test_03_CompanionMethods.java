
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

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.testexample.BasicCompanionMethods_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_03_CompanionMethods extends CommonTestRunner {

    public Test_03_CompanionMethods() {
        super(true);
    }

    public static final String NEW_LIST_SIZE = "new ArrayList<>()/*0==this.size()*/";

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("0".equals(d.statementId())) {
                assertEquals("true", d.state().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "list".equals(d.variableName())) {
                assertEquals(NEW_LIST_SIZE, d.currentValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ChangeData valueChangeData = d.evaluationResult().getExpressionChangeStream()
                        .filter(e -> "list".equals(e.getKey().fullyQualifiedName())).map(Map.Entry::getValue).findFirst().orElseThrow();
                assertEquals(NEW_LIST_SIZE, valueChangeData.value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("false", d.evaluationResult().value().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertEquals("4", d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            MethodInfo size = collection.findUniqueMethod("size", 0);
            int modified = size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD);
            assertEquals(Level.FALSE, modified);

            TypeInfo list = typeMap.get(List.class);
            MethodInfo listSize = list.findUniqueMethod("size", 0);
            assertEquals(Level.FALSE, listSize.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        // two errors: two unused parameters
        testClass(List.of("BasicCompanionMethods_0"), 2, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder()
                        .setReportWarnings(true)
                        .build());
    }


    public static final String INSTANCE_SIZE_1_CONTAINS = "instance type ArrayList<String>/*this.contains(\"a\")&&1==this.size()*/";

    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertTrue(d.haveValueChange("list")); // because of a modification
                assertEquals(INSTANCE_SIZE_1_CONTAINS, d.findValueChange("list").value().toString());
                assertTrue(d.haveLinkVariable("list", Set.of()));
            }
            if ("test".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertTrue(d.haveValueChange("b"));
                EvaluationResult.ChangeData valueChangeData = d.findValueChange("b");
                assertEquals("true", valueChangeData.value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".compareTo(d.statementId()) <= 0 && "list".equals(d.variableName())) {
                assertEquals(INSTANCE_SIZE_1_CONTAINS, d.currentValue().toString());
            }
            if ("test".equals(d.methodInfo().name) && "3".compareTo(d.statementId()) <= 0 && "b".equals(d.variableName())) {
                assertEquals("true", d.currentValue().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertTrue(d.statusesAsMap().values().stream().allMatch(as -> as == AnalysisStatus.DONE || as == AnalysisStatus.RUN_AGAIN));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
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
                assertEquals("instance type ArrayList<String>/*this.contains(\"a\")&&1==this.size()*/",
                        d.currentValue().toString());
            }
            if ("test".equals(d.methodInfo().name) && "2".equals(d.statementId()) && "list".equals(d.variableName())) {
                assertEquals("instance type ArrayList<String>/*this.contains(\"a\")&&this.contains(\"b\")&&2==this.size()*/",
                        d.currentValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }

        };
        testClass("BasicCompanionMethods_2", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test3() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo charSequence = typeMap.get(CharSequence.class);
            MethodInfo length = charSequence.findUniqueMethod("length", 0);
            assertTrue(length.methodAnalysis.isSet());
            assertEquals(Set.of(CompanionMethodName.Action.ASPECT, CompanionMethodName.Action.INVARIANT),
                    length.methodInspection.get().getCompanionMethods().keySet().stream().map(CompanionMethodName::action).collect(Collectors.toSet()));

            TypeInfo intTypeInfo = typeMap.getPrimitives().intTypeInfo;
            TypeInfo stringBuilder = typeMap.get(StringBuilder.class);
            MethodInfo appendInt = stringBuilder.typeInspection.get().methods().stream().filter(methodInfo -> "append".equals(methodInfo.name) &&
                    intTypeInfo == methodInfo.methodInspection.get().getParameters().get(0).parameterizedType.typeInfo).findFirst().orElseThrow();
            MethodInfo appendIntCompanion = appendInt.methodInspection.get().getCompanionMethods().values().stream().findFirst().orElseThrow();
            ReturnStatement returnStatement = (ReturnStatement) appendIntCompanion.methodInspection.get().getMethodBody().structure.statements().get(0);
            assertEquals("return post==prev+Integer.toString(i).length();", returnStatement.minimalOutput());

            TypeInfo string = typeMap.getPrimitives().stringTypeInfo;
            MethodInfo stringLength = string.findUniqueMethod("length", 0);

            if (returnStatement.expression instanceof BinaryOperator eq &&
                    eq.rhs instanceof BinaryOperator plus &&
                    plus.rhs instanceof MethodCall lengthCall &&
                    lengthCall.object instanceof MethodCall toString &&
                    toString.object instanceof TypeExpression integer) {
                // check we have the same Integer type
                assertSame(integer.parameterizedType.typeInfo, typeMap.getPrimitives().integerTypeInfo);
                // check the length method
                assertSame(lengthCall.methodInfo, stringLength);
            }
            CompanionAnalysis appendCa = appendInt.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("append", CompanionMethodName.Action.MODIFICATION, "Len"));
            Expression appendCompanionValue = appendCa.getValue();
            assertEquals("Integer.toString(i).length()+pre==this.length()",
                    appendCa.getValue().toString());
            if (appendCompanionValue instanceof Equals eq && eq.lhs instanceof Sum sum && sum.lhs instanceof MethodCall lengthCall) {
                assertSame(lengthCall.methodInfo, stringLength);
            } else fail();

            MethodInfo appendStr = stringBuilder.typeInspection.get().methods().stream().filter(methodInfo -> "append".equals(methodInfo.name) &&
                    string == methodInfo.methodInspection.get().getParameters().get(0).parameterizedType.typeInfo).findFirst().orElseThrow();
            MethodInfo appendStringCompanion = appendStr.methodInspection.get().getCompanionMethods().values().stream().findFirst().orElseThrow();
            ReturnStatement returnStatementStr = (ReturnStatement) appendStringCompanion.methodInspection.get().getMethodBody().structure.statements().get(0);
            assertEquals("return post==prev+(str==null?4:str.length());", returnStatementStr.minimalOutput());

            MethodInfo sbToString = stringBuilder.findUniqueMethod("toString", 0);
            CompanionAnalysis sbToStringCa = sbToString.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("toString", CompanionMethodName.Action.TRANSFER, "Len"));
            assertEquals("this.length()", sbToStringCa.getValue().toString());
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "sb".equals(d.variableName())) {
                assertEquals("instance type StringBuilder/*5==this.length()*/",
                        d.currentValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("false", d.evaluationResult().value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        testClass("BasicCompanionMethods_3", 2, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "5".equals(d.statementId())) {
                assertTrue(d.statementAnalysis().flowData.isUnreachable());
            }
        };
        testClass("BasicCompanionMethods_4", 2, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setAddModificationHelper".equals(d.methodInfo().name)) {
                assertEquals("AnnotatedAPI.isFact(containsE)?containsE?i==j:1+j==i:AnnotatedAPI.isKnown(true)?1+j==i:1+j>=i&&i>=j",
                        d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals("new HashSet()/*this.isKnown(true)&&0==this.size()*/",
                            d.currentValue().toString());
                }
                if (Set.of("1", "4").contains(d.statementId())) {
                    assertEquals("In statement " + d.statementId(),
                            "instance type HashSet/*this.contains(\"a\")&&this.isKnown(true)&&1==this.size()*/",
                            d.currentValue().toString());
                }
                if ("7".equals(d.statementId())) {
                    assertEquals("instance type HashSet/*this.contains(\"a\")&&this.contains(\"b\")" +
                                    "&&this.isKnown(true)&&2==this.size()*/",
                            d.currentValue().toString());
                }
            }
        };


        testClass("BasicCompanionMethods_5", 0, 7, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if ("set".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new HashSet<>(strings)/*this.size()==strings.size()*/",
                                d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                                d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && d.iteration() > 0) {
                ParameterAnalysis param = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                        param.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.DELAY, param.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(Level.FALSE, param.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(MultiLevel.DELAY, param.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        TypeContext typeContext = testClass("BasicCompanionMethods_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
        TypeInfo bc6 = typeContext.getFullyQualified(BasicCompanionMethods_6.class);
        MethodInfo test = bc6.findUniqueMethod("test", 1);

        MethodAnalysis methodAnalysis = test.methodAnalysis.get();
        assertEquals(0, methodAnalysis.getComputedCompanions().size());
        // See Precondition_4 for a similar example with precondition
    }


    @Test
    public void test7() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            MethodInfo clear = collection.findUniqueMethod("clear", 0);
            CompanionAnalysis clearCompanion = clear.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("clear", CompanionMethodName.Action.CLEAR, "Size"));
            assertNotNull(clearCompanion);
            assertEquals(Level.TRUE, clear.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo set = typeMap.get(Set.class);
            MethodInfo setClear = set.findUniqueMethod("clear", 0);
            assertEquals(Level.TRUE, setClear.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo annotatedAPI = typeMap.get("org.e2immu.annotatedapi.AnnotatedAPI");
            assertNotNull(annotatedAPI);
            MethodInfo isKnown = annotatedAPI.findUniqueMethod("isKnown", 1);
            assertTrue(isKnown.methodInspection.get().isStatic());
        };

        final String PARAM = "org.e2immu.analyser.testexample.BasicCompanionMethods_7.test(Set<java.lang.String>):0:strings";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals("new HashSet<>(strings)/*this.size()==strings.size()*/",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*this.contains(\"a\")&&1+strings.size()>=this.size()&&this.size()>=strings.size()*/",
                            d.currentValue().toString());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                            d.currentValue().toString());
                }
            }
        };

        testClass("BasicCompanionMethods_7", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    private static final Pattern PATTERN = Pattern.compile("(\\d+)[^\\d]*");

    @Test
    public void test8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {

                // there are 10+ statements, so numbers are two digits
                Matcher matcher = PATTERN.matcher(d.statementId());
                assertTrue(matcher.matches());
                assertEquals(2, matcher.group(0).length());

                if ("00".equals(d.statementId())) {
                    assertEquals("new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                            d.currentValue().toString());
                }
            }
            if ("test".equals(d.methodInfo().name) && "added1".equals(d.variableName())) {
                if ("02".equals(d.statementId())) {
                    assertEquals("true", d.currentValue().toString());
                }
            }
            if ("test".equals(d.methodInfo().name) && "added3".equals(d.variableName())) {
                if ("02".equals(d.statementId())) {
                    assertEquals("false", d.currentValue().toString());
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "04".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        testClass("BasicCompanionMethods_8", 0, 8, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    assertEquals("instance type HashSet/*this.contains(\"a\")&&this.isKnown(true)&&1==this.size()*/",
                            d.currentValue().toString());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals("instance type HashSet/*!this.contains(\"a\")&&this.isKnown(true)&&0==this.size()*/",
                            d.currentValue().toString());
                }
                if ("9".equals(d.statementId())) {
                    assertEquals("instance type HashSet/*this.contains(\"c\")&&!this.contains(\"a\")&&" +
                            "this.isKnown(true)&&1==this.size()*/", d.currentValue().toString());
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "7".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        testClass("BasicCompanionMethods_9", 0, 8, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("5".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*this.size()>=in.size()*/", d.currentValue().toString());
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "8".equals(d.statementId())) {
                assertEquals("set.size()", d.evaluationResult().value().toString());
            }
        };
        testClass("BasicCompanionMethods_10", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
