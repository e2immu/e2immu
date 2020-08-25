package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestExampleManualEventuallyE1Container extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("setNegativeJ".equals(methodInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals("((-this.j) >= 0 and (-j) >= 0)", methodInfo.methodAnalysis.get().precondition.get().toString());
                Assert.assertEquals("(-this.j) >= 0", methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().toString());

                FieldInfo fieldJ = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "j".equals(f.name)).findAny().orElseThrow();
                TransferValue tv = methodInfo.methodAnalysis.get().fieldSummaries.get(fieldJ);
                Assert.assertNotNull(tv);
                Value value = tv.value.get();
                Assert.assertEquals("j", value.toString());
                Value state = tv.stateOnAssignment.get();
                Assert.assertEquals("(-this.j) >= 0", state.toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("setNegativeJ".equals(d.methodInfo.name) && "2".equals(d.statementId) && "ExampleManualEventuallyE1Container.this.j".equals(d.variableName)) {
            Assert.assertEquals("j", d.currentValue.toString());
            if (d.iteration > 0) {
                Assert.assertEquals("(-this.j) >= 0", d.stateOnAssignment.toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("setNegativeJ".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                if (d.iteration <= 1) {
                    Assert.assertEquals("(-j) >= 0", d.state.toString());
                } else {
                    Assert.assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.state.toString());
                }
            }
            if ("1".equals(d.statementId) && d.iteration > 0) {
                Assert.assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.state.toString());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualEventuallyE1Container", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
