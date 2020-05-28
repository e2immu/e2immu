package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestConditionalChecks extends CommonTestRunner {
    public TestConditionalChecks() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method1".equals(methodInfo.name)) {
                if ("0".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("(not (a) or not (b))", conditional.toString());
                }
                if ("1".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("((a or b) and (not (a) or not (b)))", conditional.toString());
                }
                if ("2".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("(b and not (a))", conditional.toString());
                }
                if ("3".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals(BoolValue.FALSE, conditional);
                    Assert.assertTrue(numberedStatement.errorValue.isSet());
                }
                if("4".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.errorValue.isSet());
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ConditionalChecks", 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
