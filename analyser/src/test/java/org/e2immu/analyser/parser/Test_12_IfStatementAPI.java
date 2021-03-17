package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_12_IfStatementAPI extends CommonTestRunner {
    public Test_12_IfStatementAPI() {
        super(true);
    }

    /*
    Linked variables come later in get2 and get3 as compared to get1.
    Should we be worried about this?
     */

    @Test
    public void test4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "map.get(label3)";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("get2".equals(d.methodInfo().name) && d.variable() instanceof This) {
                Assert.assertEquals(d.statementId() + ", it " + d.iteration(),
                        Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
                Assert.assertEquals("null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label3)?defaultValue3:map.get(label3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("IfStatement_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<method:java.util.Map.get(Object)>" : "map.get(label3)";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("\"3\".equals(label1)", d.condition().toString());
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("!\"3\".equals(label1)", d.statementAnalysis().stateData.precondition.get().toString());
                    Assert.assertEquals("!\"3\".equals(label1)", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    Assert.assertEquals("true", d.conditionManagerForNextStatement().precondition().toString());
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.condition().toString());
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("!\"3\".equals(label1)", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    Assert.assertEquals("true", d.statementAnalysis().stateData.precondition.get().toString());
                    Assert.assertEquals("true", d.conditionManagerForNextStatement().precondition().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("true", d.condition().toString());
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("!\"3\".equals(label1)", d.localConditionManager().precondition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
                Assert.assertEquals("null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label3)?defaultValue3:map.get(label3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("IfStatement_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
