
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
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class Test_Support_05_Lazy extends CommonTestRunner {

    public Test_Support_05_Lazy() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("get".equals(d.methodInfo().name) && d.variable() instanceof FieldReference s && "supplier".equals(s.fieldInfo.name)) {
            Assert.assertFalse("Statement: " + d.statementId(), d.variableInfo().isAssigned());
        }
        if ("get".equals(d.methodInfo().name)) {
            if (d.variable() instanceof FieldReference t && "t".equals(t.fieldInfo.name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() > 0) {
                        Assert.assertEquals("supplier.get()/*@NotNull*/", d.currentValue().toString());
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                                d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                        Assert.assertEquals(1, d.currentValue().variables().size());
                    }
                }
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
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (d.iteration() > 0 && "get".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                // this can be picked up as a precondition for the method
                Assert.assertEquals("null==org.e2immu.support.Lazy.t$0", d.state().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (!"Lazy".equals(d.methodInfo().typeInfo.simpleName)) return;

        FieldInfo supplier = d.methodInfo().typeInfo.getFieldByName("supplier", true);
        FieldInfo t = d.methodInfo().typeInfo.getFieldByName("t", true);
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();

        if ("Lazy".equals(d.methodInfo().name)) {
            VariableInfo tv = d.getFieldAsVariable(supplier);
            assert tv != null;
            Assert.assertFalse(tv.isDelayed());

            ParameterInfo supplierParam = d.methodInfo().methodInspection.get().getParameters().get(0);
            Assert.assertEquals("supplierParam", supplierParam.name);
        }
        if ("get".equals(d.methodInfo().name)) {
            VariableInfo vi = d.getFieldAsVariable(supplier);
            assert vi != null;
            Assert.assertFalse(vi.isAssigned());

            VariableInfo ret = d.getReturnAsVariable();
            if (d.iteration() >= 1) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                Assert.assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if("Lazy".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals("[Type param T]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
        }
    };

    @Test
    public void test() throws IOException {
        testSupportClass(List.of("Lazy"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
