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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.testexample.FormatterSimplified_9;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// sub-records in records; an issue because of JavaParser 3.22.1

public class Test_62_FormatterSimplified extends CommonTestRunner {

    public Test_62_FormatterSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ForwardInfo".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInspection().isStatic());
            }
        };
        testClass("FormatterSimplified_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("writer.apply(new ForwardInfo(start,9,null,false))",
                            d.evaluationResult().getExpression().toString());
                    assertEquals("Type java.lang.Boolean", d.evaluationResult().getExpression().returnType().toString());
                }
            }
        };
        testClass("FormatterSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        // one method must be static (returns null)
        // 2x overwriting previous assignment... we can live with that
        testClass("FormatterSimplified_2", 3, 5, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("FormatterSimplified_3", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("4.0.0.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<m:combine>" : "lastOneWasSpace$4";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if ("lastOneWasSpace$4".equals(d.variableName())) {
                    if ("4.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type ElementarySpace", d.currentValue().toString());
                    }
                    if ("4.0.1.0.0".equals(d.statementId())) {
                        assertEquals("null==lastOneWasSpace$4?null:lastOneWasSpace$4", d.currentValue().toString());
                    }
                }
            }
        };
        testClass("FormatterSimplified_4", 2, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("writeLine".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p0 && "list".equals(p0.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.NULLABLE_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
                assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
            }
        };

        testClass("FormatterSimplified_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "null==<f:guide>" : "false";
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 1, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() > 1, d.statementAnalysis().flowData().interruptsFlowIsSet());
                }
                if ("0.0.1".equals(d.statementId())) {
                    DV exec = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
                    if(d.iteration()<=1) {
                        assertTrue(exec.isDelayed());
                    } else {
                        assertEquals(FlowData.NEVER, exec);
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        DV expectCnn = switch (d.variableName()) {
                            case "org.e2immu.analyser.testexample.FormatterSimplified_6.ForwardInfo.guide#(new java.util.Stack<org.e2immu.analyser.testexample.FormatterSimplified_6.GuideOnStack>()).peek().forwardInfo" -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                            case "org.e2immu.analyser.testexample.FormatterSimplified_6.ForwardInfo.guide#(new java.util.Stack<org.e2immu.analyser.testexample.FormatterSimplified_6.GuideOnStack>()/*0==this.size()*/).peek().forwardInfo",
                                    "org.e2immu.analyser.testexample.FormatterSimplified_6.ForwardInfo.guide#forwardInfo" -> MultiLevel.NULLABLE_DV;
                            default -> throw new UnsupportedOperationException("? " + d.variableName());
                        };
                        assertEquals(expectCnn, d.getProperty(Property.CONTEXT_NOT_NULL), d.variableName());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("guide".equals(d.fieldInfo().name)) {
                assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        // guide becomes ENN, which is harsh, but for now we'll keep it as is
        testClass("FormatterSimplified_6", 2, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_7() throws IOException {
        testClass("FormatterSimplified_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // caused the changes for TestConditionalValue.testReturnType; then goes to the error of test_6
    @Test
    public void test_8() throws IOException {
        testClass("FormatterSimplified_8", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    FIXME to do
    - missing out on @NM on index() causes @PropMod to be activated, but if that's not on parameters then nothing happens and we end up in a delay loop
    - once index() is @NM: guide should be @NM in apply, then in lookAhead
     */
    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:forwardInfo>" :
                            "(new Stack<GuideOnStack>()/*0==this.size()*/).peek().forwardInfo";
                    assertEquals(expect, d.evaluationResult().value().toString());

                }
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "9==<m:index>&&null!=<f:forwardInfo>" :
                            "9==(new Stack<GuideOnStack>()/*0==this.size()*/).peek().forwardInfo.guide.index()";
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<instanceOf:Guide>" :
                            "list.get(forwardInfo.pos) instanceof Guide";
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                if ("fwdInfo".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:forwardInfo>" :
                                "(new Stack<GuideOnStack>()/*0==this.size()*/).peek().forwardInfo";
                        assertEquals(expect, d.currentValue().toString());

                        // the type is in the same primary type, so we ignore IMMUTABLE if we don't know it yet
                        String expectLv = d.iteration() == 0 ? "?"
                                : "(new java.util.Stack<org.e2immu.analyser.testexample.FormatterSimplified_9.GuideOnStack>()/*0==this.size()*/).peek().forwardInfo";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<instanceOf:Guide>" : "list.get(forwardInfo.pos) instanceof Guide";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "pos".equals(fr.fieldInfo.name)) {
                    assertEquals("forwardInfo", fr.scope.toString());
                    String expect = d.iteration() == 0 ? "<f:pos>" : "instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() == 0 ? "<f:guide>" : "instance type Guide";
                    assertEquals(expect, d.currentValue().toString());
                    if ("fwdInfo".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            fail();
                        }
                        if ("1".equals(d.statementId())) {
                            assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else if ("(new Stack<GuideOnStack>()/*0==this.size()*/).peek().forwardInfo".equals(fr.scope.toString())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    } else fail();
                }
                if (d.variable() instanceof FieldReference fr && "forwardInfo".equals(fr.fieldInfo.name)) {
                    if ("(new Stack<GuideOnStack>()).peek()".equals(fr.scope.toString())) {
                        String expect = d.iteration() == 0 ? "<f:forwardInfo>" : "instance type ForwardInfo";
                        assertEquals(expect, d.currentValue().toString());
                    } else if ("(new Stack<GuideOnStack>()/*0==this.size()*/).peek()".equals(fr.scope.toString())) {
                        assertEquals("instance type ForwardInfo", d.currentValue().toString());
                    } else fail("Scope is " + fr.scope);
                }
            }
            if ("lookAhead".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "pos".equals(fr.fieldInfo.name)) {
                    assertEquals("instance type ForwardInfo", fr.scope.toString());
                    assertEquals(d.iteration() == 0 ? "<f:pos>" : "instance type int", d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "forwardInfo".equals(fr.fieldInfo.name)) {
                    assertEquals("instance type GuideOnStack", fr.scope.toString());
                    String expect = d.iteration() == 0 ? "<f:forwardInfo>" : "instance type ForwardInfo";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    assertEquals("instance type ForwardInfo", fr.scope.toString());
                    String expect = d.iteration() == 0 ? "<f:guide>" : "instance type Guide";
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                    assertFalse(d.conditionManagerForNextStatement().isDelayed());
                    assertFalse(d.localConditionManager().isDelayed());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0,
                            d.statementAnalysis().stateData().valueOfExpressionIsDelayed() == null);

                    assertEquals(d.iteration() > 0, d.statementAnalysis().stateData().preconditionIsFinal());
                    assertEquals(d.iteration() > 0,
                            d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                    assertEquals(d.iteration() == 0, d.conditionManagerForNextStatement().isDelayed());
                }
            }
            if ("lookAhead".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    // 'this', parameter 'list', ret var; references to fields 'pos', 'guide', 'forwardInfo'
                    assertFalse(d.statementAnalysis().variableIsSet("fwdInfo"));
                    Map<String, VariableInfoContainer> map = d.statementAnalysis().rawVariableStream()
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                    assertEquals(6, map.size());
                    String values = map.keySet().stream()
                            .map(s -> s.replace(FormatterSimplified_9.class.getCanonicalName(), ""))
                            .sorted().collect(Collectors.joining(","));
                    assertEquals(".ForwardInfo.guide#instance type ForwardInfo," +
                            ".ForwardInfo.pos#instance type ForwardInfo," +
                            ".GuideOnStack.forwardInfo#instance type GuideOnStack," +
                            ".lookAhead(java.util.List<.OutputElement>)," +
                            ".lookAhead(java.util.List<.OutputElement>):0:list,.this", values);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("list.get(forwardInfo.pos) instanceof Guide", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("index".equals(d.methodInfo().name)) {
                fail();
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("forwardInfo".equals(d.fieldInfo().name)) {
                assertEquals("forwardInfo", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof VariableExpression);
            }
            if ("pos".equals(d.fieldInfo().name)) {
                assertEquals("pos", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof VariableExpression);
                assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE));
            }
            if ("guide".equals(d.fieldInfo().name)) {
                assertEquals("guide", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof VariableExpression);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputElement".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.MUTABLE_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
            }
            if ("Guide".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.MUTABLE_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
                MethodInfo index = d.typeInfo().findUniqueMethod("index", 0);
                MethodAnalysis indexAnalysis = d.analysisProvider().getMethodAnalysis(index);
                assertEquals(DV.FALSE_DV, indexAnalysis.getProperty(Property.MODIFIED_METHOD));
            }
            if ("ForwardInfo".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("GuideOnStack".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stack = typeMap.get(Stack.class);
            MethodInfo peek = stack.findUniqueMethod("peek", 0);
            assertEquals(DV.FALSE_DV, peek.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };

        testClass("FormatterSimplified_9", 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }
}