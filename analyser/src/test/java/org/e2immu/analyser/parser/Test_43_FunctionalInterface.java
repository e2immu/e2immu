/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_43_FunctionalInterface extends CommonTestRunner {

    public Test_43_FunctionalInterface() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("$1".equals(d.typeInfo().simpleName)) {
                int expectContainer = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectContainer, d.typeAnalysis().getProperty(VariableProperty.CONTAINER));
            }
            if ("FunctionalInterface_0".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
                int expectContainer = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectContainer, d.typeAnalysis().getProperty(VariableProperty.CONTAINER));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("explicitGetAndIncrement".equals(d.fieldInfo().name)) {
                int expectContainer = d.iteration() <= 3 ? Level.DELAY : Level.TRUE;
                assertEquals(expectContainer, d.fieldAnalysis().getProperty(VariableProperty.CONTAINER));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("myExplicitIncrementer".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof FieldReference fr && fr.fieldInfo.name.equals("explicitGetAndIncrement")) {
                    int expectContainer = d.iteration() <= 3 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectContainer, d.getProperty(VariableProperty.CONTAINER));

                    int expectExtNn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectExtNn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    int expectExtImm = d.iteration() <= 5 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                }
            }
        };

        testClass("FunctionalInterface_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("$1".equals(d.methodInfo().typeInfo.simpleName) && "get".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            if ("myIncrementer".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("myIncrementer".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "getAndIncrement".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:getAndIncrement>" : "instance type $1";
                    assertEquals(expectValue, d.currentValue().toString());
                    if (d.iteration() > 0) {
                        NewObject newObject = (NewObject) d.currentValue();
                        assertEquals("Type org.e2immu.analyser.testexample.FunctionalInterface_1.$1",
                                newObject.parameterizedType().toString());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            FieldInfo fieldInfo = d.fieldInfo();

            if ("getAndAdd".equals(fieldInfo.name) || "getAndAdd2".equals(fieldInfo.name) || "getAndAdd3".equals(fieldInfo.name)) {
                MethodInfo sam = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                Block block = sam.methodInspection.get().getMethodBody();
                assertEquals(1, block.structure.statements().size());
                ReturnStatement returnStatement = (ReturnStatement) block.structure.statements().get(0);
                assertEquals("myCounter.add(t)", returnStatement.structure.expression().minimalOutput());
            }

            if ("getAndIncrement".equals(fieldInfo.name)) {
                MethodInfo sam = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                Block block = sam.methodInspection.get().getMethodBody();
                assertEquals(1, block.structure.statements().size());
                ReturnStatement returnStatement = (ReturnStatement) block.structure.statements().get(0);
                assertEquals("myCounter.increment()", returnStatement.structure.expression().minimalOutput());

                assertEquals("instance type $1", d.fieldAnalysis().getEffectivelyFinalValue().toString());

                MethodAnalysis methodAnalysis = d.evaluationContext().getAnalyserContext().getMethodAnalysis(sam);
                int getMethodModified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                int expectMm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMm, getMethodModified);
                int fieldModified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                int expectMom = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, fieldModified);
            }

            if ("explicitGetAndIncrement".equals(fieldInfo.name)) {
                MethodInfo get = fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
                assertEquals("get", get.name);

                assertEquals("instance type $2", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals("Type org.e2immu.analyser.testexample.FunctionalInterface_1.$2",
                        d.fieldAnalysis().getEffectivelyFinalValue().returnType().toString());

                MethodAnalysis methodAnalysis = d.evaluationContext().getAnalyserContext().getMethodAnalysis(get);
                int getMethodModified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                int expectMm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMm, getMethodModified); // STEP 1 CHECKED
                int fieldModified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                int expectMom = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, fieldModified); // STEP 2
            }

        };

        testClass("FunctionalInterface_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
                        .getCallsPotentiallyCircularMethod());
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
            assertEquals(Level.DELAY, accept.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            ParameterInfo t = accept.methodInspection.get().getParameters().get(0);
            assertEquals(Level.DELAY, t.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
        };


        testClass("FunctionalInterface_2", 0, 0, new DebugConfiguration.Builder()
              //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
              //  .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
             //   .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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
