package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class TestUnusedLocalVariableChecks extends WithAnnotatedAPIs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestUnusedLocalVariableChecks.class);

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, statement) -> {
        // ERROR: t.trim() result is not used
        if ("method1".equals(methodInfo.name) && "2".equals(statement.streamIndices())) {
            Assert.assertTrue(statement.errorValue.get());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {

        // ERROR: Unused variable "a"
        if ("UnusedLocalVariableChecks".equals(methodInfo.name)) {
            Assert.assertEquals(1L,
                    methodInfo.methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("a", methodInfo.methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);
        }

        if ("method1".equals(methodInfo.name)) {
            // ERROR: unused variable "s"
            Assert.assertEquals(1L,
                    methodInfo.methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("s", methodInfo.methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);

            // ERROR: method should be static
            Assert.assertTrue(methodInfo.methodAnalysis.complainedAboutMissingStaticStatement.get());

        }

    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        // ERROR: b is never read
        if ("b".equals(fieldInfo.name) && iteration>= 1) {
            Assert.assertTrue(fieldInfo.fieldAnalysis.fieldError.get());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("UnusedLocalVariableChecks", 6, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
