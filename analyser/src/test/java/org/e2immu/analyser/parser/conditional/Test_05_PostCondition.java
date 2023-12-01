package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analysis.MethodLevelData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_05_PostCondition extends CommonTestRunner {

    public Test_05_PostCondition() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("PostCondition[expression=set$0.size()>=5, index=1]",
                            d.statementAnalysis().stateData().getPostCondition().toString());
                    assertEquals("[PostCondition[expression=set$0.size()>=5, index=1]]",
                            d.statementAnalysis().methodLevelData().getPostConditions().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
                assertEquals("[PostCondition[expression=set$0.size()>=5, index=1]]",
                        d.methodAnalysis().getPostConditions().toString());
            }
            if ("addConstant2".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
                assertEquals("PostCondition[expression=set$0.size()>=5, index=1], PostCondition[expression=set$2.size()>=6, index=3]",
                        d.methodAnalysis().postConditionsSortedToString());
            }
        };
        testClass("PostCondition_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("PostCondition[expression=set$0.size()>=5, index=1.0.0]",
                            d.statementAnalysis().stateData().getPostCondition().toString());
                    assertEquals("[PostCondition[expression=set$0.size()>=5, index=1.0.0]]",
                            d.statementAnalysis().methodLevelData().getPostConditions().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]", d.methodAnalysis().getPrecondition().toString());
                assertEquals("PostCondition[expression=set$0.size()>=5, index=1.0.0]",
                        d.methodAnalysis().postConditionsSortedToString());
            }
            if ("addConstant2".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());

                assertEquals("PostCondition[expression=set$0.size()>=5, index=1.0.0], PostCondition[expression=set$2.size()>=6, index=3.0.0]",
                        d.methodAnalysis().postConditionsSortedToString());

            }
        };
        testClass("PostCondition_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                assertEquals("PostCondition[expression=true, index=-]",
                        d.statementAnalysis().stateData().getPostCondition().toString());
                MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData();
                assertTrue(methodLevelData.getPostConditions().isEmpty());
                assertTrue(d.statementAnalysis().stateData().isEscapeNotInPreOrPostConditions());
                assertEquals("[0]", methodLevelData.getIndicesOfEscapesNotInPreOrPostConditions().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]", d.methodAnalysis().getPrecondition().toString());
                assertEquals("", d.methodAnalysis().postConditionsSortedToString());
                assertEquals("[0]", d.methodAnalysis().indicesOfEscapesNotInPreOrPostConditions().toString());
            }
        };
        testClass("PostCondition_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
