package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Mutable extends CommonTestRunner {

    public Test_Mutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
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
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:set>"
                                : "instance type HashSet<String>/*this.contains(s)&&this.size()>=1*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                // FIXME now when evaluating the return expression in statement 2, we know that s is in set,
                //   and therefore the inline condition collapses! this is not correct!!
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<m:contains>" : "!set.contains(s)";
                    assertEquals(expected, d.absoluteState().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 2 ? "<m:method>" : "??";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("Mutable_0", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("static1".equals(d.methodInfo().name)) {
                if ("m1".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<new:Mutable_1>" : "instance type Mutable_1";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("static2".equals(d.methodInfo().name)) {
                if ("m1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<new:Mutable_1>" : "new Mutable_1()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                //   String expected = d.iteration() < 2 ? "<m:method>"
                //           : "/*inline method*/set.contains(s)?s.length():set$1.contains(s)?1:0";
                //    assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("Mutable_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
