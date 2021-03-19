package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Test_41_NotModified1 extends CommonTestRunner {

    public Test_41_NotModified1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {

            // checks the 2 errors
            if ("useApply".equals(d.methodInfo().name) && Set.of("2", "3").contains(d.statementId())) {
                assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT)); // TODO
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if("apply".equals(d.methodInfo().name) && "consumer".equals(d.variableName())) {
                assertEquals(Level.TRUE, d.getProperty(VariableProperty.NOT_MODIFIED_1));
            }
        };

        testClass("NotModified1_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
