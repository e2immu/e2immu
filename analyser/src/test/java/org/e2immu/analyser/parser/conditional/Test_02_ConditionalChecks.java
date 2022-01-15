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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.InterruptsFlow;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_02_ConditionalChecks extends CommonTestRunner {

    public static final String RETURN_VALUE = "null!=o&&o.getClass()==this.getClass()&&o!=this&&this.i==o/*(org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4)*/.i";

    public Test_02_ConditionalChecks() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        final String RETURN1 = "org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_0.method1(boolean,boolean)";
        final String RETURN_1_VALUE = "!a&&b?4:a&&!b?3:!a&&!b?2:a&&b?1:<return value>";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            DV inBlock = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
            DV inMethod = d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod();

            if ("method1".equals(d.methodInfo().name)) {

                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("a&&b", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("a&&b", d.absoluteState().toString());
                    assertEquals(FlowData.ALWAYS, inBlock);
                    assertEquals(FlowData.CONDITIONALLY, inMethod);
                    Map<InterruptsFlow, DV> interruptsFlow = d.statementAnalysis().flowData().getInterruptsFlow();
                    assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.ALWAYS), interruptsFlow);
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("!a||!b", d.state().toString());
                    assertEquals(FlowData.ALWAYS, inBlock);
                    assertEquals(FlowData.ALWAYS, inMethod);
                    Map<InterruptsFlow, DV> interruptsFlow = d.statementAnalysis().flowData().getInterruptsFlow();
                    assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.CONDITIONALLY), interruptsFlow);
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.get().isEmpty());
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("!a&&!b", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("!a&&!b", d.absoluteState().toString());
                    assertEquals(FlowData.ALWAYS, inBlock);
                    assertEquals(FlowData.CONDITIONALLY, inMethod);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("(a||b)&&(!a||!b)", d.state().toString());
                    assertEquals(FlowData.CONDITIONALLY, inBlock);
                    assertEquals(FlowData.CONDITIONALLY, inMethod);
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.get().isEmpty());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("a&&!b", d.statementAnalysis().stateData().valueOfExpression.get().toString());
                    assertEquals("!a&&b", d.state().toString());
                    assertEquals(FlowData.CONDITIONALLY, inBlock);
                    assertEquals(FlowData.CONDITIONALLY, inMethod);
                }
                // constant condition
                if ("3".equals(d.statementId())) {
                    assertEquals("true", d.statementAnalysis().stateData().valueOfExpression.get().toString());
                    assertEquals("false", d.state().toString()); // after the statement...
                    assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
                    assertEquals(FlowData.CONDITIONALLY, inBlock);
                    assertEquals(FlowData.CONDITIONALLY, inMethod);
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.get().isEmpty());
                }
                // unreachable statement
                if ("4".equals(d.statementId())) {
                    assertEquals(FlowData.NEVER, inBlock);
                    assertEquals(FlowData.NEVER, inMethod);
                    assertNotNull(d.haveError(Message.Label.UNREACHABLE_STATEMENT));
                    assertFalse(d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                }
                if ("5".equals(d.statementId())) {
                    assertEquals(FlowData.NEVER, inBlock);
                    assertEquals(FlowData.NEVER, inMethod);
                    assertNull(d.haveError(Message.Label.UNREACHABLE_STATEMENT));
                    VariableInfo ret = d.getReturnAsVariable();
                    assertNull(ret); // unreachable statement, no data have even been copied!
                    assertFalse(d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                }

            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN1.equals(d.variableName())) {
                // return 1;
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("1", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                }
                // after if(a&&b) return 1
                if ("0".equals(d.statementId())) {
                    assertEquals("a&&b?1:<return value>", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("2", d.currentValue().toString());
                }
                // after if (!a && !b) return 2;
                if ("1".equals(d.statementId())) {
                    // we do NOT expect a regression to the ReturnVariable
                    assertEquals("!a&&!b?2:a&&b?1:<return value>",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    // we do NOT expect a regression to the ReturnVariable
                    assertEquals("a&&!b?3:!a&&!b?2:a&&b?1:<return value>",
                            d.currentValue().toString());
                }
                if ("3.0.0".equals(d.statementId())) {
                    assertEquals("4", d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    // nothing is possible anymore
                    // we do NOT expect a regression to the ReturnVariable
                    assertEquals(RETURN_1_VALUE, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                }
                if ("4".equals(d.statementId())) {
                    fail("not reached!");
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                assertEquals("3", d.methodAnalysis().getLastStatement().index());
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
                assertEquals(RETURN_1_VALUE, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("ConditionalChecks_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    /*
    The escape is purely based on parameters that should not be null. This condition does not go into the precondition,
    it simply forces a @NotNull on the parameter.
     */
    @Test
    public void test1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ParameterInfo a && "a".equals(a.name) && ("0".equals(d.statementId()) || "1".equals(d.statementId()))) {
                assertTrue(d.getProperty(CONTEXT_NOT_NULL).isDone());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("null==a||null==b", d.absoluteState().toString());
                    assertEquals("null==a||null==b", d.condition().toString());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                }
                if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                    if (d.iteration() > 0) {
                        assertEquals("true", d.state().toString());
                        assertEquals("true", d.condition().toString());
                        assertEquals("true", d.statementAnalysis().stateData().getPrecondition().toString());
                        assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.get().isEmpty());
                    }
                }
            }
        };
        testClass("ConditionalChecks_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test2() throws IOException {
        final String RETURN3 = "org.e2immu.analyser.parser.failing.testexample.ConditionalChecks.method3(String,String)";
        final String A3 = RETURN3 + ":0:a";
        final String B3 = "org.e2immu.analyser.parser.failing.testexample.ConditionalChecks.method3(String,String):1:b";

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    for (int param : new int[]{0, 1}) {
                        assertDv(d.p(param), MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                        assertDv(d.p(param), MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_PARAMETER);
                    }
                }
                assertEquals(0, d.methodAnalysis().getCompanionAnalyses().size());
                assertEquals(0, d.methodAnalysis().getComputedCompanions().size());
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("true", d.condition().toString());
                        assertEquals("true", d.state().toString()); //->precondition, in this case, parameter not null
                        // goes into not-null on parameters
                        assertEquals("true", d.statementAnalysis().methodLevelData().combinedPrecondition.get().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("null==a", d.condition().toString());
                        assertEquals("null==a", d.absoluteState().toString());
                        // not-null does not contribute to the precondition
                        assertEquals("true", d.statementAnalysis().stateData().getPrecondition().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("true", d.condition().toString());
                        assertEquals("true", d.state().toString()); // in both parameters by now
                        //      assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                        assertEquals("true", d.statementAnalysis().methodLevelData().combinedPrecondition.get().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("null==b", d.condition().toString());
                        assertEquals("null==b", d.absoluteState().toString()); // null!=a in parameter @NotNull
                        assertEquals("true", d.statementAnalysis().stateData().getPrecondition().toString());
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN3.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals("<return value>", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("<return value>", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(A3 + " + " + B3, d.currentValue().toString());
                }
            }
            if (d.variable() instanceof ParameterInfo a && "a".equals(a.name)) {
                if ("0".equals(d.statementId()) || "1".equals(d.statementId()))
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
            }
            if (d.variable() instanceof ParameterInfo b && "b".equals(b.name)) {
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
            }
        };

        testClass("ConditionalChecks_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test3() throws IOException {
        testClass("ConditionalChecks_3", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test4() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4";
        final String RETURN5 = TYPE + ".method5(java.lang.Object)";
        final String O5 = RETURN5 + ":0:o";
        final String I = TYPE + ".i";
        final String CC_I = TYPE + ".i#" + O5;
        final String CONDITIONAL_CHECKS = "conditionalChecks";
        final String O_I_DELAYED = "<field:org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.i#org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.method5(java.lang.Object):0:o>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    if (O5.equals(d.variableName())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals("nullable instance type Object/*@Identity*/", d.currentValue().toString());
                        assertEquals("o:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if (RETURN5.equals(d.variableName())) {
                        assertEquals("<return value>||o==this", d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (RETURN5.equals(d.variableName())) {
                        assertEquals("null!=o&&o.getClass()==this.getClass()&&(<return value>||o==this)",
                                d.currentValue().toString());
                    }
                }
                if ("2".equals(d.statementId())) {
                    if (CONDITIONAL_CHECKS.equals(d.variableName())) {//d.iteration() == 0 ? O :
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:o:container@Class_ConditionalChecks_4;immutable@Class_ConditionalChecks_4;independent@Class_ConditionalChecks_4>";
                            case 1 -> "<vp:o:assign_to_field@Parameter_i;initial@Field_i>";
                            default -> "o/*(ConditionalChecks_4)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if (O5.equals(d.variableName())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if ("3".equals(d.statementId())) {
                    if (CC_I.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ? O_I_DELAYED : "instance type int";
                        assertEquals(expectValue, d.currentValue().debugOutput());
                        assertEquals(d.iteration() == 0, d.currentValue().isDelayed());
                    }
                    if (RETURN5.equals(d.variableName())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "null!=o&&o.getClass()==this.getClass()&&o!=this&&<field:org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.i>==<field:org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.i#conditionalChecks>";
                            case 1 -> "null!=o&&o.getClass()==this.getClass()&&o!=this&&this.i==<field:org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.i#conditionalChecks>";
                            default -> RETURN_VALUE;
                        };
                        assertEquals(expectValue, d.currentValue().debugOutput());
                        mustSeeIteration(d, 2);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                // the escape mechanism does NOT kick in!
                if ("0".equals(d.statementId())) {
                    assertEquals("o!=this", d.absoluteState().toString());
                } else if ("0.0.0".equals(d.statementId())) {
                    assertEquals("o==this", d.absoluteState().toString());
                } else if ("1.0.0".equals(d.statementId())) {
                    assertEquals("o!=this&&(null==o||o.getClass()!=this.getClass())", d.absoluteState().toString());
                } else {
                    assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.absoluteState().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.absoluteState().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                mustSeeIteration(d, 2);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, NOT_NULL_PARAMETER);
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("o==this", d.evaluationResult().value().toString());
                    assertFalse(d.haveSetProperty(O5, CONTEXT_NOT_NULL));
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("true", d.evaluationResult().value().toString());
                    assertFalse(d.haveSetProperty(O5, CONTEXT_NOT_NULL));
                }
                if ("1".equals(d.statementId())) {
                    assertFalse(d.haveSetProperty(O5, CONTEXT_NOT_NULL));
                    assertEquals("null==o||o.getClass()!=this.getClass()", d.evaluationResult().value().toString());
                    assertTrue(d.haveMarkRead(O5));
                    Variable o5 = d.evaluationResult().changeData().keySet().stream().filter(v -> v.simpleName().equals("o")).findFirst().orElseThrow();
                    assertEquals(LinkedVariables.EMPTY, d.evaluationResult().changeData().get(o5).linkedVariables());
                }
                if ("2".equals(d.statementId())) {
                    assertFalse(d.haveSetProperty(O5, CONTEXT_NOT_NULL));
                    assertFalse(d.haveSetProperty(CONDITIONAL_CHECKS, CONTEXT_NOT_NULL));
                    assertTrue(d.haveValueChange(CONDITIONAL_CHECKS));
                    assertEquals("o/*(ConditionalChecks_4)*/", d.findValueChange(CONDITIONAL_CHECKS).value().toString());
                    assertEquals("o/*(ConditionalChecks_4)*/", d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expectValueString = d.iteration() == 0
                            ? "null!=o&&<field:org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.i#o/*(org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4)*/>==<field:org.e2immu.analyser.parser.conditional.testexample.ConditionalChecks_4.i>&&o.getClass()==this.getClass()&&o!=this"
                            : RETURN_VALUE;
                    assertEquals(expectValueString, d.evaluationResult().value().debugOutput());
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());

                    if (d.iteration() == 0) {
                        // markRead is only done in the first iteration
                        assertTrue(d.haveMarkRead(CONDITIONAL_CHECKS));
                        assertTrue(d.haveMarkRead(I));
                        //assertTrue(d.haveMarkRead(I + "#o"));
                    }
                    assertFalse(d.haveSetProperty(O5, CONTEXT_NOT_NULL));
                    assertFalse(d.haveSetProperty(CONDITIONAL_CHECKS, CONTEXT_NOT_NULL));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("i".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(FINAL));
            }
        };

        testClass("ConditionalChecks_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //  .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test5x() {
        assertEquals("abc", method(0, 10));
        assertEquals("xyz", method(0, 0));
        assertEquals("tuv", method(10, -10));
        assertNull(method(10, 10));
    }

    private static String method(int p, int q) {
        return p <= 2 ? q >= 5 ? "abc" : "xyz" : q <= -1 ? "tuv" : null;
    }

    @Test
    public void test5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("p<=2||q<=-1", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("q>=5?\"abc\":\"xyz\"", d.currentValue().toString());
                    assertEquals("1.0.0.0.0-E,1.0.0.1.0-E,1.0.0:M", d.variableInfo().getAssignmentIds().toString());
                }
                if ("1.0.0.0.0".equals(d.statementId())) {
                    assertEquals("\"abc\"", d.currentValue().toString());
                }
                if ("1.0.0.1.0".equals(d.statementId())) {
                    assertEquals("\"xyz\"", d.currentValue().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    assertEquals("q<=-1?\"tuv\":null", d.currentValue().toString());
                    assertEquals("0-E,1.1.0.0.0-E,1.1.0:M", d.variableInfo().getAssignmentIds().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("p<=2?q>=5?\"abc\":\"xyz\":q<=-1?\"tuv\":null", d.currentValue().toString());
                    assertEquals("0-E,1.0.0.0.0-E,1.0.0.1.0-E,1.0.0:M,1.1.0.0.0-E,1.1.0:M,1:M",
                            d.variableInfo().getAssignmentIds().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    assertEquals("q>=5", d.condition().toString());
                    assertEquals("p<=2&&q>=5", d.absoluteState().toString());
                }
                if ("1.0.0.1.0".equals(d.statementId())) {
                    assertEquals("q<=4", d.condition().toString());
                    assertEquals("p<=2&&q<=4", d.absoluteState().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("p<=2", d.condition().toString());
                    assertEquals("p<=2", d.absoluteState().toString());
                }
                if ("1.1.0.0.0".equals(d.statementId())) {
                    assertEquals("q<=-1", d.condition().toString());
                    assertEquals("p>=3&&q<=-1", d.absoluteState().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    assertEquals("p>=3", d.condition().toString());
                    assertEquals("p>=3", d.absoluteState().toString());
                }
            }
        };
        testClass("ConditionalChecks_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test6() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("q>=5?\"abc\":\"xyz\"", d.currentValue().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    assertEquals("q<=-1?\"tuv\":\"zzz\"", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("p<=2?q>=5?\"abc\":\"xyz\":q<=-1?\"tuv\":\"zzz\"", d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    assertEquals("q>=5", d.condition().toString());
                    assertEquals("p<=2&&q>=5", d.absoluteState().toString());
                }
                if ("1.0.0.1.0".equals(d.statementId())) {
                    assertEquals("q<=4", d.condition().toString());
                    assertEquals("p<=2&&q<=4", d.absoluteState().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("p<=2", d.condition().toString());
                    assertEquals("p<=2", d.absoluteState().toString());
                }
                if ("1.1.0.0.0".equals(d.statementId())) {
                    assertEquals("q<=-1", d.condition().toString());
                    assertEquals("p>=3&&q<=-1", d.absoluteState().toString());
                }
                if ("1.1.0.1.0".equals(d.statementId())) {
                    assertEquals("q>=0", d.condition().toString());
                    assertEquals("p>=3&&q>=0", d.absoluteState().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    assertEquals("p>=3", d.condition().toString());
                    assertEquals("p>=3", d.absoluteState().toString());
                }
            }
        };
        testClass("ConditionalChecks_6", 1, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    assertEquals("(p>=3||p<=2)&&(p>=3||q>=5)&&(p<=2||q<=-1)&&(q>=5||q<=-1)",
                            d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals("(p>=3||p<=2)&&(p>=3||q>=5)&&(p<=2||q<=-1)&&(q>=5||q<=-1)",
                            d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.0.0.0".equals(d.statementId())) {
                    assertEquals("q>=5", d.condition().toString());
                    assertEquals("p<=2&&q>=5", d.absoluteState().toString());
                }
                if ("2.0.0.1.0".equals(d.statementId())) {
                    assertEquals("q<=4", d.condition().toString());
                    assertEquals("p<=2&&q<=4", d.absoluteState().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("p<=2", d.condition().toString());
                    assertEquals("p<=2", d.absoluteState().toString());
                }
                if ("2.1.0.0.0".equals(d.statementId())) {
                    assertEquals("q<=-1", d.condition().toString());
                    assertEquals("p>=3&&q<=-1", d.absoluteState().toString());
                }
                if ("2.1.0.1.0".equals(d.statementId())) {
                    assertEquals("q>=0", d.condition().toString());
                    assertEquals("p>=3&&q>=0", d.absoluteState().toString());
                }
                if ("2.1.0".equals(d.statementId())) {
                    assertEquals("p>=3", d.condition().toString());
                    assertEquals("p>=3", d.absoluteState().toString());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals("(p>=3||p<=2)&&(p>=3||q>=5)&&(p<=2||q<=-1)&&(q>=5||q<=-1)",
                            d.localConditionManager().precondition().expression().toString());
                    assertEquals("true", d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) & "s".equals(d.variableName())) {
                if ("2.0.0.0.0".equals(d.statementId())) {
                    assertEquals("\"abc\"", d.currentValue().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("q>=5?\"abc\":null", d.currentValue().toString());
                }
                if ("2.1.0".equals(d.statementId())) {
                    assertEquals("q<=-1?\"tuv\":null", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("p<=2?q>=5?\"abc\":null:q<=-1?\"tuv\":null", d.currentValue().toString());
                }
            }
            if ("method".equals(d.methodInfo().name) & "t".equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    assertEquals("p<=2?q>=5?null:\"xyz\":q<=-1?null:\"zzz\"", d.currentValue().toString());
                }
            }
        };

        testClass("ConditionalChecks_7", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }
}
