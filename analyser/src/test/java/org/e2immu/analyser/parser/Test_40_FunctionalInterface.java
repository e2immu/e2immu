package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_40_FunctionalInterface extends CommonTestRunner {

    public Test_40_FunctionalInterface() {
        super(true);
    }

    @Test
    public void test_1() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            FieldInfo fieldInfo = d.fieldInfo();
            int iteration = d.iteration();

            if ("getAndAdd".equals(fieldInfo.name) || "getAndAdd2".equals(fieldInfo.name) || "getAndAdd3".equals(fieldInfo.name)) {
                MethodInfo sam = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                Block block = sam.methodInspection.get().getMethodBody();
                assertEquals(1, block.structure.statements().size());
                ReturnStatement returnStatement = (ReturnStatement) block.structure.statements().get(0);
                assertEquals("myCounter.add(t)", returnStatement.structure.expression().minimalOutput());
            }

            if ("getAndAdd".equals(fieldInfo.name)) {
                MethodInfo sam = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                int modified = sam.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD);
                assertEquals(Level.TRUE, modified); // STEP 1 CHECKED
                if (iteration > 0) {
                    int modifiedOnField = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                    assertEquals(Level.TRUE, modifiedOnField); // STEP 2
                }
            }

            if ("getAndIncrement".equals(fieldInfo.name)) {
                MethodInfo sam = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                Block block = sam.methodInspection.get().getMethodBody();
                assertEquals(1, block.structure.statements().size());
                ReturnStatement returnStatement = (ReturnStatement) block.structure.statements().get(0);
                assertEquals("myCounter.increment()", returnStatement.structure.expression().minimalOutput());
            }
            if ("explicitGetAndIncrement".equals(fieldInfo.name)) {
                MethodInfo get = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                assertEquals("get", get.name);
                if (iteration > 0) {
                    int getMethodModified = get.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD);
                    assertEquals(Level.TRUE, getMethodModified); // STEP 1 CHECKED
                    int fieldModified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                    assertEquals(Level.TRUE, fieldModified); // STEP 2
                }
            }

        };

        testClass("FunctionalInterface_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("0".equals(d.statementId()) && "acceptMyCounter1".equals(d.methodInfo().name)) {
                if ("consumer".equals(d.variableName())) {
                    assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
                if ("FunctionalInterfaceModified2.this.myCounter1".equals(d.variableName())) {
                    assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (Set.of("acceptMyCounter1", "acceptMyCounter2", "acceptInt1").contains(d.methodInfo().name)) {
                assertTrue(d.methodAnalysis().methodLevelData()
                        .getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FunctionalInterfaceModified2".equals(d.typeInfo().name())) {
                assertEquals("[]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo consumer = typeMap.get(Consumer.class);
            MethodInfo accept = consumer.findUniqueMethod("accept", 1);
            assertEquals(Level.TRUE, accept.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            ParameterInfo t = accept.methodInspection.get().getParameters().get(0);
            assertEquals(Level.TRUE, t.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
        };


        testClass("FunctionalInterface_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodInfo staticallyExposing = d.methodInfo().typeInfo.findUniqueMethod("staticallyExposing", 2);
            MethodInfo expose3 = d.methodInfo().typeInfo.findUniqueMethod("expose3", 1);
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            if ("expose3".equals(d.methodInfo().name)) {
                assertTrue(methodLevelData.copyModificationStatusFrom.isSet(staticallyExposing));
            }
            if ("expose4".equals(d.methodInfo().name)) {
                assertTrue(methodLevelData.copyModificationStatusFrom.isSet(expose3));
            }
        };

        // two potential null pointer warnings
        testClass("FunctionalInterfaceModified3", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit3".equals(d.methodInfo().name) && "FunctionalInterfaceModified4.this.ts".equals(d.variableName())) {
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                // if(d.iteration>0) assertEquals(Level.FALSE, (int) d.properties().get(VariableProperty.METHOD_DELAY));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD);

            if ("doTheVisiting".equals(name)) {
                assertEquals(Level.FALSE, modified);
                ParameterInfo set = d.methodInfo().methodInspection.get().getParameters().get(1);
                assertEquals("set", set.name);
                assertEquals(Level.FALSE, set.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
                //    assertEquals(Level.IS_A_SIZE, set.parameterAnalysis.get().getProperty(VariableProperty.SIZE));
            }
            if ("visit2".equals(name) && iteration > 0) {
                assertEquals(Level.FALSE, modified);
            }
            if ("visit3".equals(name)) {
                if (iteration > 0) {
                    assertEquals(Level.FALSE, modified);
                }
                FieldInfo ts = d.methodInfo().typeInfo.getFieldByName("ts", true);
                VariableInfo vi = d.getFieldAsVariable(ts);
                assert vi != null;
                assertTrue(vi.isRead());
                if (iteration > 1) {
                    assertEquals(Level.FALSE, vi.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
                MethodInfo doTheVisiting = d.methodInfo().typeInfo.findUniqueMethod("doTheVisiting", 2);
                assertTrue(d.methodAnalysis().methodLevelData().copyModificationStatusFrom.isSet(doTheVisiting));
            }
        };

        testClass("FunctionalInterface_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("FunctionalInterface_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("FunctionalInterface_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
