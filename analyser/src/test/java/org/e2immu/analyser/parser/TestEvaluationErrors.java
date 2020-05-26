package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestEvaluationErrors extends CommonTestRunner {
    public TestEvaluationErrors() {
        super(false);
    }

    /*
     public static int testDivisionByZero() {
        int i=0;
        int j = 23 / i;
        return j;
    }

    public static int testDeadCode() {
        int i=1;
        if(i != 1) {
            return 2;
        }
        return 3;
    }
     */
   StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if("testDivisionByZero".equals(methodInfo.name) ) {
                if("1".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.errorValue.get());
                }
                if("2".equals(numberedStatement.streamIndices())) {
                    Assert.assertFalse(numberedStatement.errorValue.isSet());
                }
            }
            if("testDeadCode".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.get());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluationErrors", 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
