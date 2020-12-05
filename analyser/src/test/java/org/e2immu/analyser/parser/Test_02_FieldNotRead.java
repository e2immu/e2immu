package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_02_FieldNotRead extends CommonTestRunner {

    public Test_02_FieldNotRead() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {

        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("FieldNotRead".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertEquals("ERROR in M:FieldNotRead:1: Unused local variable: a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertEquals("ERROR in M:FieldNotRead:1: Useless assignment: a", d.haveError(Message.USELESS_ASSIGNMENT));

            AnalysisStatus expectAnalysisStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
            Assert.assertEquals(d.toString(), expectAnalysisStatus, d.result().analysisStatus);
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
