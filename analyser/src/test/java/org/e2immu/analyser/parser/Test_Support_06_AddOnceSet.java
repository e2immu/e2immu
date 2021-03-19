
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Test_Support_06_AddOnceSet extends CommonTestRunner {

    public Test_Support_06_AddOnceSet() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo keySet = map.findUniqueMethod("keySet", 0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                    keySet.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("AddOnceSet".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("[Type param V]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());

                Assert.assertEquals("{frozen=!frozen}", d.typeAnalysis().getApprovedPreconditionsE1().toString());
                if (d.iteration() >= 2) {
                    Assert.assertEquals("{frozen=!frozen}", d.typeAnalysis().getApprovedPreconditionsE2().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("contains".equals(d.methodInfo().name)) {
                ParameterAnalysis v = d.parameterAnalyses().get(0);
                int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectNnp, v.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
            if ("add".equals(d.methodInfo().name) && "AddOnceSet".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.iteration() <= 1) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    Assert.assertEquals("[!frozen]", d.methodAnalysis().getPreconditionForEventual().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add$Modification$Size".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo v && "v".equals(v.name)) {
                    int expectNnc = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNnc, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        testSupportClass(List.of("AddOnceSet", "Freezable"), 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
