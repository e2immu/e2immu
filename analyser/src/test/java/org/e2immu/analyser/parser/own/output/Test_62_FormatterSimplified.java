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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Stack;

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
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("nullable instance type Boolean",
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "lastOneWasSpace".equals(pi.name)) {
                    assertEquals("nullable instance type ElementarySpace/*@Identity*/", d.currentValue().toString());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if (d.variable() instanceof ParameterInfo pi && "elementarySpace".equals(pi.name)) {
                    assertEquals("nullable instance type ElementarySpace", d.currentValue().toString());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("null==lastOneWasSpace?elementarySpace:lastOneWasSpace", d.currentValue().toString());
                    String expected = d.iteration() == 0 ? "elementarySpace:-1,lastOneWasSpace:-1,return combine:0"
                            : "elementarySpace:1,lastOneWasSpace:1,return combine:0";
                    assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("forward".equals(d.methodInfo().name)) {
                if ("outputElement".equals(d.variableName())) {
                    if ("8".equals(d.statementId()) || "9".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("8.0.5".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
                if ("string".equals(d.variableName())) {
                    if ("8".equals(d.statementId()) || "9".equals(d.statementId())) {
                        fail("The variable 'string' should not exist here");
                    }
                }
                if ("chars".equals(d.variableName())) {
                    if ("8".equals(d.statementId()) || "9".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "!<instanceOf:Space>&&!<instanceOf:Guide>&&<m:length>>=1&&<v:end>><v:pos>&&<f:NEWLINE>!=<m:get>?<v:chars>+<m:length>+(<v:wroteOnce>&&!<m:apply>&&!<instanceOf:Guide>&&!<instanceOf:Space>&&<m:length>>=1&&<v:end>><v:pos>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>&&<f:NEWLINE>!=<m:get>?1:0):<vl:chars>";
                            case 1 -> "!<s:boolean>&&end$8>pos$8&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>?<s:boolean>?<vl:chars>:<m:length>>=1?<vl:chars>+<m:length>+(wroteOnce$8&&!<m:apply>&&!<s:boolean>&&!<s:boolean>&&(<s:boolean>?<m:combine>:nullable instance type ElementarySpace)!=<f:RELAXED_NONE>&&<m:length>>=1&&<vp:NONE:container@Class_ElementarySpace>!=(<s:boolean>?<m:combine>:nullable instance type ElementarySpace)&&end$8>pos$8&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>?1:0):instance type int:instance type int";
                            case 2 -> "!<s:boolean>&&end$8>pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;initial@Field_split;mom@Parameter_split>?<s:boolean>?<vl:chars>:<m:length>>=1?<vl:chars>+<m:length>+(wroteOnce$8&&!<m:apply>&&!<s:boolean>&&!<s:boolean>&&<m:length>>=1&&ElementarySpace.NONE!=(<s:boolean>?<m:combine>:nullable instance type ElementarySpace)&&ElementarySpace.RELAXED_NONE!=(<s:boolean>?<m:combine>:nullable instance type ElementarySpace)&&end$8>pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;initial@Field_split;mom@Parameter_split>?1:0):instance type int:instance type int";
                            default -> "!(outputElement instanceof Guide guide)&&!(outputElement instanceof Space space)&&end$8>pos$8&&list.get(pos$8)!=Space.NEWLINE&&(outputElement instanceof Symbol symbol?list.get(pos$8)/*(Symbol)*/.symbol:outputElement instanceof Guide?\"\":\"abc\").length()>=1?nullable instance type Boolean?chars$8:(([end$8,lastOneWasSpace,ElementarySpace.NONE,ElementarySpace.RELAXED_NONE,Space.NEWLINE,list.get(pos$8)/*(Symbol)*/.symbol,list,outputElement,pos$8,wroteOnce$8,instance type boolean])?1:0)+(outputElement instanceof Symbol symbol?list.get(pos$8)/*(Symbol)*/.symbol:outputElement instanceof Guide?\"\":\"abc\").length()+chars$8:instance type int";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("forward".equals(d.methodInfo().name)) {
                if ("8".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=(<v:allowBreak>||<instanceOf:Guide>||!<m:apply>||<m:length><=0)&&(!<v:allowBreak>||!<v:wroteOnce>||<instanceOf:Guide>||<m:length><=0||-<v:chars>+maxChars-<m:length>>=(<v:wroteOnce>&&!<instanceOf:Guide>&&!<instanceOf:Space>&&<m:length>>=1&&<v:end>><v:pos>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>&&<f:NEWLINE>!=<m:get>?1:0))&&(<v:wroteOnce>||<instanceOf:Guide>||!<m:apply>||<m:length><=0)&&(<instanceOf:Space>||!<m:apply>||!<instanceOf:Guide>||<v:chars>>=maxChars)&&(<instanceOf:Guide>||!<m:apply>||<m:length><=0||-1+<v:chars>-maxChars+<m:length>+(<v:wroteOnce>&&!<instanceOf:Guide>&&!<instanceOf:Space>&&<m:length>>=1&&<v:end>><v:pos>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>&&<f:NEWLINE>!=<m:get>?1:0)>=0)&&(<instanceOf:Space>||!<instanceOf:Guide>||-1-<v:chars>+maxChars>=0)&&(<v:pos>>=<v:end>||<f:NEWLINE>==<m:get>);parent=CM{}}";
                        case 1 -> "CM{state=[chars,end$8,<vp:NONE:container@Class_ElementarySpace>,<vp:NEWLINE:container@Record_Space>,<f:<m:left>.split>,list,maxChars,pos,wroteOnce,<vl:wroteOnce>,<too complex>];parent=CM{}}";
                        case 2 -> "CM{state=[chars,end$8,ElementarySpace.NONE,ElementarySpace.RELAXED_NONE,<vp:NEWLINE:cm@Parameter_split;initial@Field_split;mom@Parameter_split>,<f:<m:left>.split>,Split.NEVER,list,maxChars,pos,wroteOnce,<vl:wroteOnce>,<too complex>];parent=CM{}}";
                        default -> "CM{state=[chars,end,lastOneWasSpace,ElementarySpace.NONE,ElementarySpace.RELAXED_NONE,Space.NEWLINE,list.get(pos)/*(Symbol)*/.left().split,Split.NEVER,list.get(pos)/*(Symbol)*/.symbol,list,maxChars,outputElement,pos,wroteOnce,instance type boolean];parent=CM{}}";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                    assertEquals(d.iteration() >= 3, d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ElementarySpace".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("FormatterSimplified_2", 4, 7, new DebugConfiguration.Builder()
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
              //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
              //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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
                String expect = d.iteration() <= 2 ? "<m:combine>"
                        : "outputElement instanceof Symbol symbol&&null==lastOneWasSpace&&end$4>pos$4&&list.get(pos$4)!=Space.NEWLINE?null:lastOneWasSpace";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        testClass("FormatterSimplified_4", 2, 1, new DebugConfiguration.Builder()
            //    .addEvaluationResultVisitor(evaluationResultVisitor)
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
                    String expect = d.iteration() == 0 ? "<null-check>" : "null==forwardInfo.guide";
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 1, d.statementAnalysis().flowData().interruptsFlowIsSet());
                }
                if ("0.0.1".equals(d.statementId())) {
                    DV exec = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
                    if (d.iteration() == 0) {
                        assertTrue(exec.isDelayed());
                    } else {
                        assertEquals(FlowData.ALWAYS, exec);
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        DV expectCnn = switch (d.variableName()) {
                            case "org.e2immu.analyser.parser.own.output.testexample.FormatterSimplified_6.ForwardInfo.guide#(new java.util.Stack<org.e2immu.analyser.parser.own.output.testexample.FormatterSimplified_6.GuideOnStack>()).peek().forwardInfo" -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                            case "org.e2immu.analyser.parser.own.output.testexample.FormatterSimplified_6.ForwardInfo.guide#(new java.util.Stack<org.e2immu.analyser.parser.own.output.testexample.FormatterSimplified_6.GuideOnStack>()/*0==this.size()*/).peek().forwardInfo",
                                    "org.e2immu.analyser.parser.own.output.testexample.FormatterSimplified_6.ForwardInfo.guide#forwardInfo" -> MultiLevel.NULLABLE_DV;
                            default -> throw new UnsupportedOperationException("? " + d.variableName());
                        };
                        assertEquals(expectCnn, d.getProperty(Property.CONTEXT_NOT_NULL), d.variableName());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("guide".equals(d.fieldInfo().name)) {
                assertDv(d, 0, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        // guide becomes ENN, which is harsh, but for now we'll keep it as is
        testClass("FormatterSimplified_6", 0, 3, new DebugConfiguration.Builder()
          //      .addEvaluationResultVisitor(evaluationResultVisitor)
          //      .addStatementAnalyserVisitor(statementAnalyserVisitor)
          //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
          //      .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
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
        testClass("FormatterSimplified_8", 0, 3, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() <= 2
                            ? "<f:(new Stack<GuideOnStack>()).peek().forwardInfo>"
                            : "(new Stack<GuideOnStack>()).peek().forwardInfo";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0, 1, 2 -> "<null-check>&&9==<m:index>";
                        default -> "null!=(new Stack<GuideOnStack>()).peek().forwardInfo&&9==fwdInfo.guide.index()";
                    };
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 2, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<instanceOf:Guide>" :
                            "list.get(forwardInfo.pos) instanceof Guide";
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 2, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                if ("fwdInfo".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() <= 2
                                ? "<f:(new Stack<GuideOnStack>()).peek().forwardInfo>"
                                : "(new Stack<GuideOnStack>()).peek().forwardInfo";
                        assertEquals(expect, d.currentValue().toString());

                        // the type is in the same primary type, so we ignore IMMUTABLE if we don't know it yet
                        assertEquals("(new Stack<GuideOnStack>()).peek().forwardInfo:0,fwdInfo:0",
                                d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0, 1, 2 -> "<s:boolean>";
                        default -> "list.get(forwardInfo.pos) instanceof Guide";
                    };
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "pos".equals(fr.fieldInfo.name)) {
                    assertEquals("forwardInfo", fr.scope.toString());
                    String expect = d.iteration() <= 2 ? "<f:pos>" : "instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() <= 2 ? "<f:guide>" : "nullable instance type Guide";
                    assertEquals(expect, d.currentValue().toString());
                    if ("fwdInfo".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            fail();
                        }
                        if ("1".equals(d.statementId())) {
                            assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else if ("(new Stack<GuideOnStack>()/*0==this.size()*/).peek().forwardInfo".equals(fr.scope.toString())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    } else fail();
                }
                if (d.variable() instanceof FieldReference fr && "forwardInfo".equals(fr.fieldInfo.name)) {
                    if ("(new Stack<GuideOnStack>()).peek()".equals(fr.scope.toString())) {
                        String expect = d.iteration() <= 2 ? "<f:forwardInfo>" : "nullable instance type ForwardInfo";
                        assertEquals(expect, d.currentValue().toString());
                    } else if ("(new Stack<GuideOnStack>()/*0==this.size()*/).peek()".equals(fr.scope.toString())) {
                        assertEquals("nullable instance type ForwardInfo", d.currentValue().toString());
                    } else fail("Scope is " + fr.scope);
                }
            }
            if ("lookAhead".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "pos".equals(fr.fieldInfo.name)) {
                    assertEquals("forwardInfo", fr.scope.toString());
                    assertEquals(d.iteration() <= 2 ? "<f:pos>" : "instance type int", d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "forwardInfo".equals(fr.fieldInfo.name)) {
                    assertEquals("(new Stack<GuideOnStack>()).peek()", fr.scope.toString());
                    String expect = d.iteration() <= 2 ? "<f:forwardInfo>" : "nullable instance type ForwardInfo";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    assertEquals("fwdInfo", fr.scope.toString());
                    String expect = d.iteration() <= 2 ? "<f:guide>" : "nullable instance type Guide";
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId())) {
                    assertFalse(d.conditionManagerForNextStatement().isDelayed());
                    assertFalse(d.localConditionManager().isDelayed());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 2, d.conditionManagerForNextStatement().isDelayed());
                    assertFalse(d.localConditionManager().isDelayed());

                    assertEquals(d.iteration() >= 4,
                            d.statementAnalysis().stateData().valueOfExpressionIsDelayed() == null);

                    assertEquals(d.iteration() >= 3, d.statementAnalysis().stateData().preconditionIsFinal());
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 2, d.localConditionManager().isDelayed());
                    assertEquals(d.iteration() <= 2, d.conditionManagerForNextStatement().isDelayed());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                String expected = d.iteration() <= 2 ? "<m:apply>" : "/*inline apply*/list.get(forwardInfo.pos) instanceof Guide";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
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
                assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE));
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
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("GuideOnStack".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stack = typeMap.get(Stack.class);
            MethodInfo peek = stack.findUniqueMethod("peek", 0);
            assertEquals(DV.FALSE_DV, peek.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };

        testClass("FormatterSimplified_9", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
