
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

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/*
https://github.com/bnaudts/e2immu/issues/15
 */
public class TestLazy extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("get".equals(d.methodInfo().name) && "Lazy.this.supplier".equals(d.variableName())) {
            Assert.assertFalse("Statement: " + d.statementId(), d.properties().isSet(VariableProperty.ASSIGNED));
        }
        if ("get".equals(d.methodInfo().name) && "Lazy.this.t".equals(d.variableName()) && d.iteration() > 0) {
            if ("2.0.0".equals(d.statementId())) {
                Assert.assertEquals("this.supplier.get(),@NotNull", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                Assert.assertEquals(1, d.currentValue().variables().size());
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        if ("t".equals(d.fieldInfo().name) && iteration > 0) {
            Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        }
        if ("supplier".equals(d.fieldInfo().name) && iteration > 0) {
            Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            Assert.assertNotNull(d.fieldAnalysis().getEffectivelyFinalValue());

            // Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (d.iteration() > 0 && "get".equals(d.methodInfo().name)) {
            if ("2.0.0".equals(d.statementId())) {
                Assert.assertEquals("null == localT", d.state().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        FieldInfo supplier = d.methodInfo().typeInfo.getFieldByName("supplier", true);
        FieldInfo t = d.methodInfo().typeInfo.getFieldByName("t", true);
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();

        if ("Lazy".equals(d.methodInfo().name)) {
            VariableInfo tv = d.getFieldAsVariable(supplier);
            Assert.assertNotSame(tv.getValue(), UnknownValue.NO_VALUE);

            ParameterInfo supplierParam = d.methodInfo().methodInspection.get().parameters.get(0);
            Assert.assertEquals("supplierParam", supplierParam.name);
            if (d.iteration() > 0) {
                Assert.assertNotNull(supplierParam.parameterAnalysis.get().getAssignedToField());
                //  Assert.assertTrue(supplierParam.parameterAnalysis.get().copiedFromFieldToParameters.isSet());
            }
        }
        if ("get".equals(d.methodInfo().name)) {
            VariableInfo tv = d.getFieldAsVariable(supplier);
            Assert.assertEquals(Level.DELAY, tv.properties.get(VariableProperty.ASSIGNED));


            VariableInfo ret = d.getReturnAsVariable();
            if (d.iteration() >= 1) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret.properties.get(VariableProperty.NOT_NULL));

                Assert.assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());
                Set<Variable> linkedToT = d.methodAnalysis().getLastStatement().variables.get(t.fullyQualifiedName())
                        .linkedVariables.get();
                // for now (and I believe it's correct, t will not be linked to supplier)
                Assert.assertFalse(linkedToT.isEmpty());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("Lazy"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
