
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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.start.testexample.BasicCompanionMethods_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("since 20230911, focus on stability first")
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
            DV modified = size.methodAnalysis.get().getProperty(MODIFIED_METHOD);
            assertEquals(DV.FALSE_DV, modified);

            TypeInfo list = typeMap.get(List.class);
            MethodInfo listSize = list.findUniqueMethod("size", 0);
            assertEquals(DV.FALSE_DV, listSize.methodAnalysis.get().getProperty(MODIFIED_METHOD));
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
                        .addAnnotatedAPISourceDirs(CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS)
                        .setWriteMode(AnnotatedAPIConfiguration.WriteMode.DO_NOT_WRITE)
                        .build());
    }


    public static final String INSTANCE_SIZE_1_CONTAINS = "instance type ArrayList<String>/*1==this.size()&&this.contains(\"a\")*/";

    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertTrue(d.haveValueChange("list")); // because of a modification
                EvaluationResult.ChangeData cd = d.findValueChange("list");
                assertEquals(INSTANCE_SIZE_1_CONTAINS, cd.value().toString());
                assertEquals("", cd.linkedVariables().toString());
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
                assertEquals(d.iteration() > 0,
                        d.statusesAsMap().values().stream().allMatch(as -> as == AnalysisStatus.DONE || as == AnalysisStatus.RUN_AGAIN));
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
            if ("test".equals(d.methodInfo().name) && "list".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    assertEquals("instance type ArrayList<String>/*1==this.size()&&this.contains(\"a\")*/",
                            d.currentValue().toString());
                    assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(IMMUTABLE));
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("instance type ArrayList<String>/*2==this.size()&&this.contains(\"a\")&&this.contains(\"b\")*/",
                            d.currentValue().toString());
                }
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

            TypeInfo intTypeInfo = typeMap.getPrimitives().intTypeInfo();
            TypeInfo stringBuilder = typeMap.get(StringBuilder.class);
            MethodInfo appendInt = stringBuilder.typeInspection.get().methods().stream().filter(methodInfo -> "append".equals(methodInfo.name) &&
                    intTypeInfo == methodInfo.methodInspection.get().getParameters().get(0).parameterizedType.typeInfo).findFirst().orElseThrow();
            MethodInfo appendIntCompanion = appendInt.methodInspection.get().getCompanionMethods().values().stream().findFirst().orElseThrow();
            ReturnStatement returnStatement = (ReturnStatement) appendIntCompanion.methodInspection.get().getMethodBody().structure.statements().get(0);
            assertEquals("return post==prev+Integer.toString(i).length();", returnStatement.minimalOutput());

            TypeInfo string = typeMap.getPrimitives().stringTypeInfo();
            MethodInfo stringLength = string.findUniqueMethod("length", 0);

            if (returnStatement.expression instanceof BinaryOperator eq &&
                    eq.rhs instanceof BinaryOperator plus &&
                    plus.rhs instanceof MethodCall lengthCall &&
                    lengthCall.object instanceof MethodCall toString &&
                    toString.object instanceof TypeExpression integer) {
                // check we have the same Integer type
                assertSame(integer.parameterizedType.typeInfo, typeMap.getPrimitives().integerTypeInfo());
                // check the length method
                assertSame(lengthCall.methodInfo, stringLength);
            }
            CompanionAnalysis appendCa = appendInt.methodAnalysis.get().getCompanionAnalyses()
                    .get(new CompanionMethodName("append", CompanionMethodName.Action.MODIFICATION, "Len"));
            Expression appendCompanionValue = appendCa.getValue();
            assertEquals("this.length()==Integer.toString(i).length()+pre",
                    appendCa.getValue().toString());
            if (appendCompanionValue instanceof Equals eq && eq.rhs instanceof Sum sum && sum.lhs instanceof MethodCall lengthCall) {
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
                assertTrue(d.statementAnalysis().flowData().isUnreachable());
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
                assertEquals("AnnotatedAPI.isFact(containsE)?containsE?i==j:1==i-j:AnnotatedAPI.isKnown(true)?null==j?i>=1:1==i-j:null==j?i>=1:1-i+j>=0&&i>=j",
                        d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("00".equals(d.statementId())) {
                    assertEquals("new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                            d.currentValue().toString());
                }
                if (Set.of("01", "04").contains(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&1==this.size()&&this.contains(\"a\")*/",
                            d.currentValue().toString(), "statement " + d.statementId());
                }
                if ("07".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&2==this.size()&&this.contains(\"a\")&&this.contains(\"b\")*/",
                            d.currentValue().toString());
                }
                if ("11".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&2==this.size()&&this.contains(\"a\")&&this.contains(\"b\")*/",
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
                        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                                d.getProperty(CONTEXT_NOT_NULL));

                        assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && d.iteration() > 0) {
                ParameterAnalysis param = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, param.getProperty(CONTEXT_NOT_NULL));
                assertDv(d.p(0), 0, DV.FALSE_DV, CONTEXT_MODIFIED);
            }
        };

        TypeContext typeContext = testClass("BasicCompanionMethods_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
        TypeInfo bc6 = typeContext.getFullyQualified(BasicCompanionMethods_6.class);
        MethodInfo test = bc6.findUniqueMethod("test", 1);

        MethodAnalysis methodAnalysis = test.methodAnalysis.get();
        assertEquals(1, methodAnalysis.getComputedCompanions().size());

        MethodInfo companion = methodAnalysis.getComputedCompanions().values().stream().findFirst().orElseThrow();
        Block block = companion.methodInspection.get().getMethodBody();
        ReturnStatement returnStatement = (ReturnStatement) block.structure.statements().get(0);
        Expression expression = returnStatement.expression;
        assertEquals("!(new HashSet<>(strings)/*this.size()==strings.size()*/).contains(\"a\")",
                expression.toString());
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
            assertEquals(DV.TRUE_DV, clear.methodAnalysis.get().getProperty(MODIFIED_METHOD));

            TypeInfo set = typeMap.get(Set.class);
            MethodInfo setClear = set.findUniqueMethod("clear", 0);
            assertEquals(DV.TRUE_DV, setClear.methodAnalysis.get().getProperty(MODIFIED_METHOD));

            TypeInfo annotatedAPI = typeMap.get("org.e2immu.annotatedapi.AnnotatedAPI");
            assertNotNull(annotatedAPI);
            MethodInfo isKnown = annotatedAPI.findUniqueMethod("isKnown", 1);
            assertTrue(isKnown.methodInspection.get().isStatic());
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "set".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals("new HashSet<>(strings)/*this.size()==strings.size()*/",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*this.contains(\"a\")&&1-this.size()+strings.size()>=0&&this.size()>=strings.size()*/",
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
                if ("01".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&1==this.size()&&this.contains(\"a\")*/",
                            d.currentValue().toString());
                }
                if ("04".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&0==this.size()&&!this.contains(\"a\")*/",
                            d.currentValue().toString());
                }
                if ("09".equals(d.statementId())) {
                    assertEquals("instance type HashSet<String>/*AnnotatedAPI.isKnown(true)&&1==this.size()&&!this.contains(\"a\")&&this.contains(\"c\")*/", d.currentValue().toString());
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "04".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
            if ("test".equals(d.methodInfo().name) && "07".equals(d.statementId())) {
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
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals(1L, d.statementAnalysis().messageStream().count());
                }
                if ("3".equals(d.statementId())) {
                    assertEquals(1L, d.statementAnalysis().messageStream().count());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals(1L, d.statementAnalysis().messageStream().count());
                }
            }
        };
        testClass("BasicCompanionMethods_10", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // tests multiple things:
    // 1. correct generification into the lambda
    // 2. no application of add companion on instance without isKnown(true)
    @Test
    public void test11() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(2, d.evaluationResult().changeData().size());
                    assertEquals("instance type boolean", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("set".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        // with state!
                        assertEquals("new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/", d.currentValue().toString());
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("set".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        // with state, but the state based on pre == null (>=1, rather than == 1)
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("instance type HashSet<String>/*this.size()>=1&&this.contains(s)*/", eval.getValue().toString());
                        // no change after block
                        String expected = d.iteration() == 0
                                ? "instance type boolean?<v:set>:instance type HashSet<String>"
                                : "instance type boolean?instance type HashSet<String>/*this.size()>=1&&this.contains(s)*/:instance type HashSet<String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("BasicCompanionMethods_11", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
