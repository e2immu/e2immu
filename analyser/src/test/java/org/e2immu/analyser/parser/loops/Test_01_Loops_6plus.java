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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analysis.FlowData.ALWAYS;
import static org.e2immu.analyser.analysis.FlowData.CONDITIONALLY;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_6plus extends CommonTestRunner {

    public Test_01_Loops_6plus() {
        super(true);
    }

    @Test
    public void test_6() throws IOException {
        testClass("Loops_6", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    // in the first iteration (0), we compare 1+<v:i> against <v:i>, which is false
                    String expect = d.iteration() == 0 ? "false" : "true";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("k".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())
                            || "1.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("i".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>" : "0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // it 1: 1+i$1 rather than n>i$1?1+i$1:i$1
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());

                    assertTrue(((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.isFrozen());
                    assertEquals("i", ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.stream().collect(Collectors.joining()));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertDv(d, 1, CONDITIONALLY, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());

                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.absoluteState().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.absoluteState().toString());
                    assertEquals(expect, d.condition().toString());
                    String expectInterrupt = "{}";
                    assertEquals(expectInterrupt, d.statementAnalysis().flowData().getInterruptsFlow().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "true" : "false";
                    assertEquals(expect, d.state().toString());
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().flowData().interruptsFlowIsSet());
                    } else {
                        assertEquals("{}", d.statementAnalysis().flowData().getInterruptsFlow().toString());
                    }
                }
                if ("1.0.2.0.0".equals(d.statementId())) {
                    String expect = "{break=ALWAYS:2}"; // ALWAYS=value 2, see FlowData
                    assertEquals(expect, d.statementAnalysis().flowData().getInterruptsFlow().toString());
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().flowData().blockExecution.isSet());
                    } else {
                        assertSame(CONDITIONALLY, d.statementAnalysis().flowData().blockExecution.get());
                    }
                }
                if ("1.0.3".equals(d.statementId())) {
                    assertDv(d, 1, FlowData.NEVER, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                }
            }
        };

        // expression in if statement always true; unreachable statement
        testClass("Loops_7", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:in>" : "\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "strings.length>0?null:<f:in>" :
                                "strings.length>0?null:\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("node$1$1.0.0-E".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "strings.length>0?null:<v:node>"
                            : "strings.length>0?null:node$1";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        testClass("Loops_9", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:in>" : "\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "strings.isEmpty()?<f:in>:null" :
                                "strings.isEmpty()?\"abc\":null";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_10", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_11() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("java.time.ZoneOffset.UTC".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:UTC>" : "nullable instance type ZoneOffset";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("java.time.ZoneOffset.UTC:0", d.variableInfo().getLinkedVariables().toString());

                        assertEquals(DV.TRUE_DV, d.getProperty(CONTEXT_MODIFIED));
                    }
                }
                if ("result".equals(d.variableName())) {
                    if ("3.0.1.0.1.0.0".equals(d.statementId())) {
                        // important: because we're in a loop, we're not just adding one element; therefore,
                        // we cannot keep count, and erase all state
                        String expect = d.iteration() == 0 ? "<v:result>" : "instance type Map<String,String>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals("result:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("5".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("5".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "map.entrySet().isEmpty()||queried.contains((instance type Entry<String,String>).getKey())||<m:compareTo><=0?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<v:result>"
                                : "map.entrySet().isEmpty()||queried.contains((instance type Entry<String,String>).getKey())||(instance type Entry<String,String>).getValue().compareTo(now$3.toString())<=0?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = "result:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("count,entry",
                            ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo typeInfo = typeMap.get(LocalDateTime.class);
            MethodInfo now = typeInfo.findUniqueMethod("now", 0);
            assertTrue(now.methodInspection.get().isStatic());
            assertEquals(DV.FALSE_DV, now.methodAnalysis.get().getProperty(MODIFIED_METHOD));
            TypeInfo chrono = typeMap.get(ChronoLocalDateTime.class);
            MethodInfo toInstant = chrono.findUniqueMethod("toInstant", 1);
            assertFalse(toInstant.methodInspection.get().isStatic());
            assertEquals(DV.FALSE_DV, toInstant.methodAnalysis.get().getProperty(MODIFIED_METHOD));
            ParameterAnalysis utc = toInstant.parameterAnalysis(0);
            assertEquals(DV.TRUE_DV, utc.getProperty(MODIFIED_VARIABLE));
        };

        // potential null pointer exception
        testClass("Loops_11", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    @Test
    public void test_12() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("[i]", // and not RES!
                            ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet().toString());
                }
            }
        };
        // errors:
        // 1- overwriting previous assignment: in condition
        // 2- empty loop
        // 3- unused local variable (in stmt 3, because the loop is empty, i is not used)
        // warning: parameter n not used
        testClass("Loops_12", 3, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("i,res1",
                            ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals("2-E", d.variableInfo().getAssignmentIds().toString());
                    }
                }
            }
        };

        // overwriting previous assignment, for i (in the condition) and res1
        // (even though the compiler forces us to assign a value, this is bad programming)
        testClass("Loops_13", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        // the 0 is inaccessible, because inside loop 2, i$2 is read rather than i
                        // this you can see in the "j"-"2.0.0" value
                        String expect = d.iteration() == 0 ? "<v:i>" : "0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("j".equals(d.variableName())) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        assertEquals("10", d.currentValue().toString());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "3==<v:i>?10:9" : "3==i$2?10:9";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "6==<v:i>?11:3==<v:i>?10:9" : "6==i$2?11:3==i$2?10:9";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertTrue(((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.isFrozen());
                    assertEquals("i,j", ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet()
                            .stream().sorted().collect(Collectors.joining(",")));
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "n><v:i>" : "n>i$2";
                    assertEquals(expected, d.absoluteState().toString());
                }
            }
        };

        testClass("Loops_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_15() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertEquals("4", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("i==p?4:3", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // value is not 4! p could be greater than 10, and then i can never reach p
                        // FIXME
                        String expected =  "p>=0&&p<=9?4:3";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("i".equals(d.variableName())) {
                    assertNotEquals("0", d.statementId());
                    assertNotEquals("2", d.statementId());
                    if ("1.0.0".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable lv) {
                            assertEquals("1", lv.statementIndex());
                        } else fail();
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                var list = d.statementAnalysis().stateData().statesOfInterruptsStream().toList();
                if ("1".equals(d.statementId())) {
                    assertEquals(1, list.size());
                    String breakStmtIndex = list.get(0).getKey();
                    assertEquals("1.0.0.0.1", breakStmtIndex);
                    Expression state = list.get(0).getValue().get();
                    assertEquals("i<=9&&-1==i-p", state.toString());
                }
            }
        };
        testClass("Loops_15", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_16() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "map.entrySet().isEmpty()?3:<merge:int>"
                                : "map.entrySet().isEmpty()?3:instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_16", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_17() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:entrySet>.isEmpty()?3:<merge:int>"
                                : "map$0.entrySet().isEmpty()?3:instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_17", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    // looks very much like Project_0.recentlyReadAndUpdatedAfterwards, which has multiple problems
    // 20211015: there's a 3rd linked1Variables value for 1.0.1.0.0: empty
    @Test
    public void test_18() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:entrySet>" : "kvStore$0.entrySet()";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1.0.1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:getValue>" : "entry$1.getValue()";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<m:entrySet>.isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>"
                                : "kvStore$0.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        assertEquals(expected, d.currentValue().toString());
                        String expectVars = d.iteration() == 0 ? "[entry, key, kvStore, result]" : "[kvStore$0]";
                        assertEquals(expectVars, d.currentValue().variables(true).stream().map(Variable::toString).sorted().toList().toString());
                    }
                }
                if ("entry".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "entry:0,key:-1" : "entry:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }

                    if ("1.0.1.0.0".equals(d.statementId())) {
                        String expectL1 = d.iteration() == 0 ? "container:-1,entry:0,key:-1" : "entry:0";
                        assertEquals(expectL1, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Container".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                assertEquals(MultiLevel.INDEPENDENT_DV, d.typeAnalysis().getProperty(INDEPENDENT));
            }
        };

        testClass("Loops_18", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    // looks very much like Project_0.recentlyReadAndUpdatedAfterwards, which has multiple problems
    // Solution: DelayedExpression.translate()
    @Test
    public void test_19() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1.0.1.0.1".equals(d.statementId())) {
                EvaluationResult.ChangeData cd = d.findValueChangeByToString("container.read");
                assertFalse(cd.properties().containsKey(CONTEXT_NOT_NULL));
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {

                if ("container".equals(d.variableName())) {
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:getValue>" : "entry$1.getValue()";
                        assertEquals(expected, d.currentValue().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertFalse(d.variableInfoContainer().hasMerge());
                    }
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:getValue>" : "entry$1.getValue()";
                        assertEquals(expected, d.currentValue().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertTrue(d.variableInfoContainer().hasMerge());
                    }
                }

                if ("org.e2immu.analyser.parser.loops.testexample.Loops_19.Date.time#entry$1.getValue().read".equals(d.variableName())) {
                    assertTrue(d.iteration() >= 2);
                    String expected = d.iteration() == 2 ? "<f:time>" : "instance type long";
                    assertEquals(expected, d.currentValue().toString());
                }

                if ("result".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:entrySet>.isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>";
                            case 1, 2 -> "kvStore$0.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>";
                            default -> "kvStore$0.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }

                if ("org.e2immu.analyser.parser.loops.testexample.Loops_19.Container.read#container".equals(d.variableName())) {
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:read>" : "";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() <= 1 ? "container.read:0" : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        // must be nullable, because of the null check
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "!<m:entrySet>.isEmpty()" : "!kvStore$0.entrySet().isEmpty()";
                    assertEquals(expectCondition, d.conditionManagerForNextStatement().condition().toString());
                }
            }
        };

        testClass("Loops_19", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
