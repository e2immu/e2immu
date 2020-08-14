
/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/*
https://github.com/bnaudts/e2immu/issues/15
 */
public class TestLazy extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("get".equals(d.methodInfo.name) && "Lazy.this.supplier".equals(d.variableName)) {
            Assert.assertNull("Statement: " + d.statementId, d.properties.get(VariableProperty.ASSIGNED));
        }
        if ("get".equals(d.methodInfo.name) && "Lazy.this.t".equals(d.variableName) && d.iteration > 0) {
            if ("2.0.0".equals(d.statementId)) {
                Assert.assertEquals("supplier.get(),@NotNull", d.currentValue.toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
                Assert.assertEquals(1, d.currentValue.variables().size());
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("t".equals(fieldInfo.name) && iteration > 0) {
                Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
            }
            if ("supplier".equals(fieldInfo.name) && iteration > 0) {
                Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                Assert.assertTrue(fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if (iteration > 0 && "get".equals(methodInfo.name) && "2.0.0".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("null == localT", conditional.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            FieldInfo supplier = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> f.name.equals("supplier")).findFirst().orElseThrow();
            FieldInfo t = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> f.name.equals("t")).findFirst().orElseThrow();

            if ("Lazy".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().fieldSummaries.get(supplier);
                Assert.assertTrue(tv.value.isSet());
            }
            if ("get".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().fieldSummaries.get(supplier);
                Assert.assertEquals(Level.DELAY, tv.properties.get(VariableProperty.ASSIGNED));

                TransferValue ret1 = methodInfo.methodAnalysis.get().returnStatementSummaries.get("1.0.0");
                TransferValue ret2 = methodInfo.methodAnalysis.get().returnStatementSummaries.get("2.0.1");
                if (iteration >= 1) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret1.properties.get(VariableProperty.NOT_NULL));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret2.properties.get(VariableProperty.NOT_NULL));

                    Assert.assertTrue(methodInfo.methodAnalysis.get().variablesLinkedToFieldsAndParameters.isSet());
                    Set<Variable> linkedToT = methodInfo.methodAnalysis.get().variablesLinkedToFieldsAndParameters.get()
                            .entrySet().stream()
                            .filter(e -> e.getKey() instanceof FieldReference && ((FieldReference) e.getKey()).fieldInfo == t)
                            .map(Map.Entry::getValue).findFirst().orElseThrow();
                    // for now (and I believe it's correct, t will not be linked to supplier)
                    Assert.assertFalse(linkedToT.isEmpty());
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass("Lazy", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
