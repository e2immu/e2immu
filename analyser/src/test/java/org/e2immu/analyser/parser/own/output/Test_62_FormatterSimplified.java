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
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.InlineConditional;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // the result of the method call causes CNN EffContentNotNull on writer, so no warning should be thrown
    // for the Boolean which has to be boolean
    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:apply>" : "writer.apply(new ForwardInfo(start,9,null,false))";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                    assertEquals("Type Boolean", d.evaluationResult().getExpression().returnType().toString());
                }
            }
        };
        testClass("FormatterSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Disabled("Overwriting condition manager's final value")
    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            assertFalse(d.allowBreakDelay());

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
                    String expected = "elementarySpace:0,lastOneWasSpace:0";
                    assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("forward".equals(d.methodInfo().name)) {
                if ("outputElement".equals(d.variableName())) {
                    if ("8".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("8.0.5".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 3, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("9".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if ("string".equals(d.variableName())) {
                    if ("8.0.3.0.2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2 -> "<m:symbol>";
                            default -> "`list.get(pos$8)/*(Symbol)*/.symbol`";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("8.0.3.1.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<instanceOf:Guide>?\"\":<m:write>";
                            case 1, 2 -> "<c:boolean>?\"\":\"abc\"";
                            default -> "outputElement instanceof Guide?\"\":\"abc\"";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("8.0.3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<instanceOf:Symbol>?<m:symbol>:<instanceOf:Guide>?\"\":<m:write>";
                            case 1, 2 -> "<c:boolean>?<m:symbol>:<c:boolean>?\"\":\"abc\"";
                            default ->
                                    "outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\"";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        if (d.iteration() == 0) {
                            if (d.currentValue() instanceof InlineConditional ic && ic.ifTrue instanceof DelayedExpression de) {
                                String symbol = d.iteration() == 0 ? "<oos:symbol>.symbol()" : "symbol.symbol()";
                                assertEquals(symbol, de.getDoneOriginal().toString());
                            } else fail();
                        }
                    }
                    if ("8.0.4.1.0.1.0.0.03".equals(d.statementId())) {
                        String expected = d.iteration() < 3
                                ? "<instanceOf:Symbol>?<m:symbol>:<instanceOf:Guide>?\"\":<m:write>"
                                : "outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\"";
                        assertEquals(expected, d.currentValue().toString());
                        assertFalse(d.variableInfo().getLinkedVariables().stream()
                                .anyMatch(e -> "symbol".equals(e.getKey().simpleName())));
                    }
                    if ("8".equals(d.statementId()) || "9".equals(d.statementId())) {
                        fail("The variable 'string' should not exist here");
                    }
                }
                if ("writeSpace".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("8.0.4.1.0.1.0.0"));
                    if ("8.0.4.1.0.1.0.0.03".equals(d.statementId())) {
                        String expected = d.iteration() < 3
                                ? "<v:wroteOnce>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>"
                                : "wroteOnce$8&&lastOneWasSpace$8!=ElementarySpace.NONE&&lastOneWasSpace$8!=ElementarySpace.RELAXED_NONE";
                        assertEquals(expected, d.currentValue().toString());
                        assertFalse(d.variableInfo().getLinkedVariables().stream()
                                .anyMatch(e -> "symbol".equals(e.getKey().simpleName())));
                    }
                }
                if ("symbol".equals(d.variableName())) {
                    // symbol should not exist outside 8.0.3
                    assertTrue(d.statementId().compareTo("8.0.4") < 0);
                }
                if (d.variable() instanceof FieldReference fr && "NICE".equals(fr.fieldInfo.name)) {
                    assertEquals("ElementarySpace", fr.scope.returnType().typeInfo.simpleName);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("9".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 ->
                                    "!<instanceOf:Space>&&(<instanceOf:Guide>?<m:apply>&&-1-<v:chars>+(!<instanceOf:Space>&&-1+<v:end>>=<v:pos>&&<f:NEWLINE>!=<m:get>&&(<instanceOf:Guide>||<m:length>>=1)?<p:maxChars>:instance type int)>=0:<m:apply>&&<m:length>>=1&&(!<v:allowBreak>||!<v:wroteOnce>||-<v:chars>+<p:maxChars>-<m:length>>=(!<instanceOf:Guide>&&!<instanceOf:Space>&&<v:wroteOnce>&&<m:apply>&&<m:length>>=1&&-1+<v:end>>=<v:pos>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>&&<f:NEWLINE>!=<m:get>?1:0)))&&-1+<v:end>>=<v:pos>&&<f:NEWLINE>!=<m:get>";
                            case 1 ->
                                    "!<c:boolean>&&(<c:boolean>?<m:apply>&&!<c:boolean>:<m:apply>&&<m:length>>=1&&(!<vl:wroteOnce>||!(<c:boolean>?instance type boolean&&<dv:scope-scope-81:25:8.0.3.split>!=<f:NEVER>:instance type boolean)||-<m:length>!(<m:apply>&&!<c:boolean>&&!<c:boolean>&&<m:length>>=1&&-1+end$8>=pos$8&&<vp:NONE:container@Class_ElementarySpace>!=(<c:boolean>?<m:combine>:nullable instance type ElementarySpace)&&<f:RELAXED_NONE>!=(<c:boolean>?<m:combine>:nullable instance type ElementarySpace)&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>&&<vp:NEWLINE:container@Record_Space>!=<m:get>&&-1+<vl:end>>=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>?1+(<c:boolean>?<vl:pos>:<c:boolean>?<new:ForwardInfo>:<m:length>>=1?<vl:pos>:instance type int):start)&&(<c:boolean>||<c:boolean>||<m:length><=0?instance type boolean:<s:boolean>)?1:0)+(!<c:boolean>&&!<c:boolean>&&<m:length>>=1&&-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>?<p:maxChars>:instance type int)>=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>?<instanceOf:Space>||<instanceOf:Guide>||<m:length><=0?<vl:chars>:<v:chars>+<m:length>+(<v:wroteOnce>&&!<m:apply>&&!<instanceOf:Guide>&&!<instanceOf:Space>&&<m:length>>=1&&-1+<v:end>>=<v:pos>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>&&<f:NEWLINE>!=<m:get>?1:0):0)))&&<vp:NEWLINE:container@Record_Space>!=<m:get>&&-1+<vl:end>>=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:container@Record_Space>?1+(<c:boolean>?<vl:pos>:<c:boolean>?<new:ForwardInfo>:<m:length>>=1?<vl:pos>:instance type int):start)";
                            case 2 ->
                                    "!<c:boolean>&&(<c:boolean>?<m:apply>&&!<c:boolean>:<m:apply>&&<m:length>>=1&&(!<vl:wroteOnce>||!(<c:boolean>?instance type boolean&&<dv:scope-scope-81:25:8.0.3.split>!=<f:NEVER>:instance type boolean)||-<m:length>!(<m:apply>&&!<c:boolean>&&!<c:boolean>&&<m:length>>=1&&-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>&&<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>!=<m:get>&&(<c:boolean>?<m:combine>:nullable instance type ElementarySpace)!=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>?<f:NONE>:instance type ElementarySpace/*new ElementarySpace()*/)&&-1+<vl:end>>=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>?1+(<c:boolean>?instance type int:<c:boolean>?<new:ForwardInfo>:<m:length>>=1?<vl:pos>:instance type int):start)&&(<c:boolean>||<c:boolean>||<m:length><=0?instance type boolean:<s:boolean>)&&(nullable instance type ElementarySpace!=instance type ElementarySpace/*new ElementarySpace()*/||<f:RELAXED_NONE>!=<m:combine>)?1:0)+(!<c:boolean>&&!<c:boolean>&&<m:length>>=1&&-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>?<p:maxChars>:instance type int)>=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>?<instanceOf:Space>||<instanceOf:Guide>||<m:length><=0?<vl:chars>:<v:chars>+<m:length>+(<v:wroteOnce>&&!<m:apply>&&!<instanceOf:Guide>&&!<instanceOf:Space>&&<m:length>>=1&&-1+<v:end>>=<v:pos>&&<v:lastOneWasSpace>!=<f:NONE>&&<v:lastOneWasSpace>!=<f:RELAXED_NONE>&&<f:NEWLINE>!=<m:get>?1:0):0)))&&<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>!=<m:get>&&-1+<vl:end>>=(-1+end$8>=pos$8&&list.get(pos$8)!=<vp:NEWLINE:cm@Parameter_split;mom@Parameter_split>?1+(<c:boolean>?instance type int:<c:boolean>?<new:ForwardInfo>:<m:length>>=1?<vl:pos>:instance type int):start)";
                            default ->
                                    "[(outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:\"abc\").length(),(outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\").length(),list.get(pos$8),list.get(-1+end$8>=pos$8&&list.get(pos$8)!=Space.NEWLINE?1+pos$8:start),list.size(),writer.apply(new ForwardInfo(pos,chars,null,Split.NEVER,list.get(pos$8)/*(Guide)*/,false)),writer.apply(new ForwardInfo(pos,chars,wroteOnce$8&&!(outputElement instanceof Guide guide)&&!(outputElement instanceof Space space)&&(outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\").length()>=1&&-1+end$8>=pos$8&&lastOneWasSpace$8!=ElementarySpace.NONE&&lastOneWasSpace$8!=ElementarySpace.RELAXED_NONE&&list.get(pos$8)!=Space.NEWLINE&&(!(outputElement instanceof Symbol symbol?instance type boolean&&scope-scope-81:25:8.0.3.split!=Split.NEVER:instance type boolean)||-(outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\").length()-chars$8+maxChars>=(wroteOnce$8&&!(outputElement instanceof Guide guide)&&!(outputElement instanceof Space space)&&(outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\").length()>=1&&-1+end$8>=pos$8&&lastOneWasSpace$8!=ElementarySpace.NONE&&lastOneWasSpace$8!=ElementarySpace.RELAXED_NONE&&list.get(pos$8)!=Space.NEWLINE?1:0))?\" \"+(outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\"):outputElement instanceof Symbol symbol?`list.get(pos$8)/*(Symbol)*/.symbol`:outputElement instanceof Guide?\"\":\"abc\",split,null,outputElement instanceof Symbol)),chars$8,end$8,lastOneWasSpace$8,ElementarySpace.NICE,ElementarySpace.NONE,ElementarySpace.RELAXED_NONE,Space.NEWLINE,(-1+end$8>=pos$8&&list.get(pos$8)!=Space.NEWLINE?list.get(pos$8)/*(Symbol)*/.left():nullable instance type Space).split,scope-scope-81:25:8.0.3.split,Split.NEVER,start,maxChars,outputElement,pos$8,wroteOnce$8,instance type boolean]";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("forward".equals(d.methodInfo().name)) {
                if ("8".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
                }
                if ("8.0.3".equals(d.statementId())) {
                    /*
                     the variable "symbol" should go out of scope... it should not be contained in any of the
                     values of the variables
                     */
                    Set<String> varsInValues = d.statementAnalysis().variableStream().flatMap(vi ->
                                    vi.getValue().variableStream())
                            .map(Variable::fullyQualifiedName)
                            .collect(Collectors.toUnmodifiableSet());
                    assertFalse(varsInValues.contains("symbol"));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("symbol".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:symbol>" : "/*inline symbol*/symbol";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("symbol".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ElementarySpace".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        // 2 errors: overwriting a previous value; valid, I'd say
        // 2 re-assignment warnings, ok given the null returned by elementarySpace
        testClass("FormatterSimplified_2", 2, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
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
                String expect = d.iteration() <= 2 ? "<m:combine>" : "lastOneWasSpace$4";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        testClass("FormatterSimplified_4", 2, 3, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
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
                    String expect = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1 -> "null==<vp:guide:container@Interface_Guide>";
                        default -> "null==forwardInfo.guide";
                    };
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() < 2, d.evaluationResult().causesOfDelay().isDelayed());
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
                    if (d.iteration() < 2) {
                        assertTrue(exec.isDelayed());
                    } else {
                        assertEquals(FlowData.ALWAYS, exec);
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
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
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
        testClass("FormatterSimplified_8", 0, 3, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0 -> "<f:(new Stack<GuideOnStack>()).peek().forwardInfo>";
                        case 1 -> "<vp:forwardInfo:container@Record_ForwardInfo>";
                        case 2 ->
                                "<vp:forwardInfo:cm@Parameter_guide;cm@Parameter_string;mom@Parameter_guide;mom@Parameter_string>";
                        default -> "((new Stack<GuideOnStack>()/*0==this.size()*/).peek()).forwardInfo";
                    };
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0, 1, 2 -> "9==<m:index>&&<null-check>";
                        default ->
                                "9==((new Stack<GuideOnStack>()/*0==this.size()*/).peek()).forwardInfo.guide.index()&&null!=((new Stack<GuideOnStack>()/*0==this.size()*/).peek()).forwardInfo";
                    };
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() < 3, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<instanceOf:Guide>" : "list.get(forwardInfo.pos) instanceof Guide";
                    assertEquals(expect, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() < 3, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                if ("fwdInfo".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<f:(new Stack<GuideOnStack>()).peek().forwardInfo>";
                            case 1 -> "<vp:forwardInfo:container@Record_ForwardInfo>";
                            case 2 ->
                                    "<vp:forwardInfo:cm@Parameter_guide;cm@Parameter_string;mom@Parameter_guide;mom@Parameter_string>";
                            default -> "((new Stack<GuideOnStack>()/*0==this.size()*/).peek()).forwardInfo";
                        };
                        assertEquals(expect, d.currentValue().toString());

                        // the type is in the same primary type, so we ignore IMMUTABLE if we don't know it yet
                        String linked = d.iteration() < 3 ? "(new Stack<GuideOnStack>()).peek().forwardInfo:0,scope-53:35:-1"
                                : "(new Stack<GuideOnStack>()).peek().forwardInfo:0,scope-53:35:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expect = d.iteration() < 3 ? "<s:boolean>" : "list.get(forwardInfo.pos) instanceof Guide";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "pos".equals(fr.fieldInfo.name)) {
                    assertEquals("forwardInfo", fr.scope.toString());
                    String expect = d.iteration() < 3 ? "<f:forwardInfo.pos>" : "instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "guide".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() < 3 ? "<f:fwdInfo.guide>" : "nullable instance type Guide";
                    assertEquals(expect, d.currentValue().toString());
                    if ("fwdInfo".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            fail();
                        }
                        if ("1".equals(d.statementId())) {
                            assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else if ("(new Stack<GuideOnStack>()/*0==this.size()*/).peek().forwardInfo".equals(fr.scope.toString())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    } else fail();
                }
                if (d.variable() instanceof FieldReference fr && "forwardInfo".equals(fr.fieldInfo.name)) {
                    if ("(new Stack<GuideOnStack>()).peek()".equals(fr.scope.toString())) {
                        String expect = d.iteration() < 3
                                ? "<f:(new Stack<GuideOnStack>()).peek().forwardInfo>"
                                : "nullable instance type ForwardInfo";
                        assertEquals(expect, d.currentValue().toString());
                    } else if ("(new Stack<GuideOnStack>()/*0==this.size()*/).peek()".equals(fr.scope.toString())) {
                        assertEquals("nullable instance type ForwardInfo", d.currentValue().toString());
                    } else fail("Scope is " + fr.scope);
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
                    assertEquals(d.iteration() < 3, d.conditionManagerForNextStatement().isDelayed());
                    assertFalse(d.localConditionManager().isDelayed());

                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().stateData().valueOfExpressionIsDelayed().isDone());
                    //     mustSeeIteration(d, 3);

                    assertEquals(d.iteration() >= 3, d.statementAnalysis().stateData().preconditionIsFinal());
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() < 3, d.localConditionManager().isDelayed());
                    assertEquals(d.iteration() < 3, d.conditionManagerForNextStatement().isDelayed());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                String expected = d.iteration() < 3 ? "<m:apply>" : "/*inline apply*/list.get(forwardInfo.pos) instanceof Guide";
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
                assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE));
            }
            if ("guide".equals(d.fieldInfo().name)) {
                assertEquals("guide", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof VariableExpression);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputElement".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
            }
            if ("Guide".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
                MethodInfo index = d.typeInfo().findUniqueMethod("index", 0);
                MethodAnalysis indexAnalysis = d.analysisProvider().getMethodAnalysis(index);
                assertEquals(DV.FALSE_DV, indexAnalysis.getProperty(Property.MODIFIED_METHOD));
            }
            if ("ForwardInfo".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("GuideOnStack".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
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
