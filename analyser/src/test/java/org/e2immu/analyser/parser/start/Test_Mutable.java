package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlineConditional;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Mutable extends CommonTestRunner {

    public Test_Mutable() {
        super(true);
    }

    @Disabled("Method call's equality needs to include statement time")
    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("4".compareTo(d.statementId()) > 0) {
                    assertEquals(0, d.evaluationResult().statementTime());
                } else {
                    assertEquals(1, d.evaluationResult().statementTime());
                }
                if ("0".equals(d.statementId())) {
                    ChangeData cd = d.findValueChangeBySubString("set");
                    assertEquals(0, cd.modificationTimeIncrement());
                }
                if ("1".equals(d.statementId())) {
                    ChangeData cd = d.findValueChangeBySubString("set");
                    int expected = d.iteration() == 0 ? 0 : 1;
                    assertEquals(expected, cd.modificationTimeIncrement());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("b1".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<m:contains>" : "set.contains(s)";
                    assertEquals(expected, d.currentValue().toString());
                    if (d.iteration() > 0) {
                        if (d.currentValue() instanceof MethodCall mc) {
                            assertEquals("0,0", mc.getModificationTimes());
                        } else fail();
                    }
                }
                if ("b2".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<m:contains>" : "set.contains(s)";
                    assertEquals(expected, d.currentValue().toString());
                    if (d.iteration() > 0) {
                        if (d.currentValue() instanceof MethodCall mc) {
                            assertEquals("1,0", mc.getModificationTimes());
                        } else fail();
                    }
                }
                if ("b3".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<s:boolean>" : "set.contains(s)";
                    assertEquals(expected, d.currentValue().toString());
                    if (d.iteration() > 0) {
                        if (d.currentValue() instanceof MethodCall mc) {
                            assertEquals("2,0", mc.getModificationTimes());
                        } else fail();
                    }
                }
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo().name)) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(0, initial.getModificationTimeOrNegative());

                        String expected = d.iteration() == 0 ? "<f:set>" : "instance type HashSet<String>";
                        assertEquals(expected, d.currentValue().toString());

                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertModificationTime(d, 1, 0);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "s".equals(pi.name)) {
                    assertModificationTime(d, 0, 0);
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("4".compareTo(d.statementId()) > 0) {
                    assertEquals(0, d.statementAnalysis().statementTime(Stage.MERGE));
                } else {
                    assertEquals(1, d.statementAnalysis().statementTime(Stage.MERGE));
                }
            }
        };

        testClass("Mutable_0", 0, 0, new DebugConfiguration.Builder()
                //     .addEvaluationResultVisitor(evaluationResultVisitor)
                //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    if (d.iteration() > 0) {
                        assertEquals("set.contains(s)?s.length():0",
                                d.evaluationResult().getExpression().toString());
                    }
                }
            }
            if ("static1".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:length>==<m:method>"
                            : d.iteration() == 1 ? "(new Mutable_1()).method(s)==<m:length>"
                            : "s.length()==(new Mutable_1()).method(s)";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo().name)) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(0, initial.getModificationTimeOrNegative());

                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        DV modified = eval.getProperty(Property.CONTEXT_MODIFIED);
                        assertEquals(DV.FALSE_DV, modified);
                        assertEquals(0, eval.getModificationTimeOrNegative());

                        String expected = d.iteration() == 0 ? "<f:set>" : "instance type HashSet<String>";
                        assertEquals(expected, d.currentValue().toString());

                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertModificationTime(d, 1, 0);
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertModificationTime(d, 0, 0);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:contains>?<m:length>:<return value>" :
                                "set.contains(s)?s.length():<return value>";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() > 0) {
                            if (d.currentValue() instanceof InlineConditional inline) {
                                if (inline.condition instanceof MethodCall methodCall) {
                                    assertEquals("0,0", methodCall.getModificationTimes());
                                } else fail();
                            } else fail();
                        }
                    }
                }
            }
            if ("static1".equals(d.methodInfo().name)) {
                if ("m1".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:m1>" : "new Mutable_1()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("static2".equals(d.methodInfo().name)) {
                if ("m1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new Mutable_1()", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".compareTo(d.statementId()) > 0) {
                    assertEquals(0, d.statementAnalysis().statementTime(Stage.MERGE));
                } else {
                    assertEquals(1, d.statementAnalysis().statementTime(Stage.MERGE));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "<m:method>" : "set.contains(s)?s.length():0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("Mutable_1", 0, 0, new DebugConfiguration.Builder()
              //  .addEvaluationResultVisitor(evaluationResultVisitor)
              //  .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
               // .addStatementAnalyserVisitor(statementAnalyserVisitor)
               // .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Disabled("Method call's equality needs to include statement time")
    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertEquals("<m:contains>", d.evaluationResult().getExpression().toString());
                    } else if (d.evaluationResult().getExpression() instanceof MethodCall methodCall) {
                        assertEquals("0,0", methodCall.getModificationTimes());
                        assertEquals("set.contains(s)", d.evaluationResult().getExpression().toString());
                    } else fail("expected method call set.contains(s)");
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<m:contains>?<m:length>:<return value>"
                                : "set.contains(s)?s.length():<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo().name)) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(0, initial.getModificationTimeOrNegative());

                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertModificationTime(d, 1, 0);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:set>"
                                : "instance type HashSet<String>/*this.size()>=1&&this.contains(s)*/";
                        assertEquals(expected, d.currentValue().toString());

                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertModificationTime(d, 1, 1);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<m:contains>" : "!set.contains(s)";
                    assertEquals(expected, d.absoluteState().toString());
                    if (d.iteration() > 0) {
                        if (d.absoluteState() instanceof Negation negation
                                && negation.expression instanceof MethodCall methodCall) {
                            assertEquals("0,0", methodCall.getModificationTimes());
                        } else fail();
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "<m:method>" : "set.contains(s)?s.length():-1";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("Mutable_2", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
