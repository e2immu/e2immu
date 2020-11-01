package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFieldNotRead extends CommonTestRunner {

    public TestFieldNotRead() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {

        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("FieldNotRead".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertEquals("ERROR in M:FieldNotRead:1: Unused local variable: a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertEquals("ERROR in M:FieldNotRead:1: Useless assignment: a", d.haveError(Message.USELESS_ASSIGNMENT));

            AnalysisStatus expectAnalysisStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
            Assert.assertEquals(d.toString(), expectAnalysisStatus, d.analysisStatus());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        // ERROR: b is never read
        if ("b".equals(d.fieldInfo().name) && d.iteration() >= 1) {
            Assert.assertTrue(d.fieldAnalysis().getFieldError());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FieldNotRead", 3, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
