package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModifiedChecks extends CommonTestRunner {

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if ("getAndAdd".equals(fieldInfo.name) || "getAndAdd2".equals(fieldInfo.name) || "getAndAdd3".equals(fieldInfo.name)) {
            MethodInfo sam = fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            Block block = sam.methodInspection.get().methodBody.get();
            Assert.assertEquals(1, block.statements.size());
            ReturnStatement returnStatement = (ReturnStatement) block.statements.get(0);
            Assert.assertEquals("myCounter.add(t)", returnStatement.expression.expressionString(0));
        }

        if ("getAndIncrement".equals(fieldInfo.name)) {
            MethodInfo sam = fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            Block block = sam.methodInspection.get().methodBody.get();
            Assert.assertEquals(1, block.statements.size());
            ReturnStatement returnStatement = (ReturnStatement) block.statements.get(0);
            Assert.assertEquals("myCounter.increment()", returnStatement.expression.expressionString(0));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModifiedChecks", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
