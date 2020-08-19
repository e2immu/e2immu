package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModifiedFieldChecks extends CommonTestRunner {

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if ("getAndAdd".equals(fieldInfo.name) || "getAndAdd2".equals(fieldInfo.name) || "getAndAdd3".equals(fieldInfo.name)) {
            MethodInfo sam = fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            Block block = sam.methodInspection.get().methodBody.get();
            Assert.assertEquals(1, block.statements.size());
            ReturnStatement returnStatement = (ReturnStatement) block.statements.get(0);
            Assert.assertEquals("myCounter.add(t)", returnStatement.expression.expressionString(0));
        }

        if("getAndAdd".equals(fieldInfo.name)) {
            MethodInfo sam = fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            int modified = sam.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            Assert.assertEquals(Level.TRUE, modified); // STEP 1 CHECKED
            if (iteration > 0) {
                int modifiedOnField = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED);
                Assert.assertEquals(Level.TRUE, modifiedOnField); // STEP 2
            }
        }

        if ("getAndIncrement".equals(fieldInfo.name)) {
            MethodInfo sam = fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            Block block = sam.methodInspection.get().methodBody.get();
            Assert.assertEquals(1, block.statements.size());
            ReturnStatement returnStatement = (ReturnStatement) block.statements.get(0);
            Assert.assertEquals("myCounter.increment()", returnStatement.expression.expressionString(0));
        }
        if ("explicitGetAndIncrement".equals(fieldInfo.name)) {
            MethodInfo get = fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            Assert.assertEquals("get", get.name);
            int modified = get.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            Assert.assertEquals(Level.TRUE, modified); // STEP 1 CHECKED
            if (iteration > 0) {
                int modifiedOnField = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED);
                Assert.assertEquals(Level.TRUE, modifiedOnField); // STEP 2
            }
        }

    };

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModifiedFieldChecks", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
