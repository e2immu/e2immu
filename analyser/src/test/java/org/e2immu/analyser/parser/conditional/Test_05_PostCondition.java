package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_05_PostCondition extends CommonTestRunner {

    public Test_05_PostCondition() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("PostCondition[expression=set.size()>=5, index=1]",
                            d.statementAnalysis().stateData().getPostCondition().toString());
                    assertEquals("[PostCondition[expression=set.size()>=5, index=1]]",
                            d.statementAnalysis().methodLevelData().getPostConditions().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
                assertEquals("[PostCondition[expression=set.size()>=5, index=1]]",
                        d.methodAnalysis().getPostConditions().toString());
            }
            if ("addConstant2".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
                assertEquals("PostCondition[expression=set.size()>=5, index=1], PostCondition[expression=set.size()>=6, index=3]",
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
                    assertEquals("PostCondition[expression=set.size()>=5, index=1.0.0]",
                            d.statementAnalysis().stateData().getPostCondition().toString());
                    assertEquals("[PostCondition[expression=set.size()>=5, index=1.0.0]]",
                            d.statementAnalysis().methodLevelData().getPostConditions().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addConstant".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]", d.methodAnalysis().getPrecondition().toString());
                assertEquals("PostCondition[expression=set.size()>=5, index=1.0.0]",
                        d.methodAnalysis().postConditionsSortedToString());
            }
            if ("addConstant2".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());

                assertEquals("PostCondition[expression=set.size()>=5, index=1.0.0], PostCondition[expression=set.size()>=6, index=3.0.0]",
                        d.methodAnalysis().postConditionsSortedToString());

            }
        };
        testClass("PostCondition_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
