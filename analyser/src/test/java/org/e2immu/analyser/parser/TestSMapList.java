
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TestSMapList extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("list".equals(d.methodInfo.name) && "list".equals(d.variableName) && "3".equals(d.statementId)) {
            Assert.assertEquals("map.get(a)", d.currentValue.toString());

            // NOTE: this is in contradiction with the state, but here we test the fact that get can return null
            Assert.assertEquals(MultiLevel.NULLABLE, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
        }
        if ("add".equals(d.methodInfo.name) && "bs".equals(d.variableName) && "3".equals(d.statementId)) {
            Assert.assertEquals(Level.FALSE, d.properties.get(VariableProperty.MODIFIED));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("3".equals(d.statementId) && "list".equals(d.methodInfo.name)) {
            if (d.iteration == 0) {
                Assert.assertEquals("(not (null == map.get(a)) and not (null == a))", d.state.toString());
            } else {
                Assert.assertEquals("not (null == map.get(a))", d.state.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        String name = d.methodInfo().name;

        if ("list".equals(name)) {
            TransferValue returnValue1 = methodLevelData.returnStatementSummaries.get("2");
            if (d.iteration() == 0) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, returnValue1.properties.get(VariableProperty.NOT_NULL));

                // the end result
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            } else {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, returnValue1.properties.get(VariableProperty.NOT_NULL));

                // the end result
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        }
        if ("copy".equals(name)) {
            TransferValue returnValue = methodLevelData.returnStatementSummaries.get("2");
            Assert.assertEquals(MultiLevel.MUTABLE, returnValue.properties.get(VariableProperty.IMMUTABLE));
        }
        if ("add".equals(name) && d.methodInfo().methodInspection.get().parameters.size() == 3) {
            ParameterInfo parameterInfo = d.methodInfo().methodInspection.get().parameters.get(2);
            if ("bs".equals(parameterInfo.name)) {
                int modified = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED);
                Assert.assertEquals(Level.FALSE, modified);
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo map = typeContext.getFullyQualified(Map.class);
        MethodInfo entrySet = map.typeInspection.getPotentiallyRun().methods.stream().filter(m -> m.name.equals("entrySet")).findFirst().orElseThrow();
        Assert.assertEquals(Level.SIZE_COPY_TRUE, entrySet.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY));
        Assert.assertEquals(0, entrySet.methodAnalysis.get().getProperty(VariableProperty.SIZE)); // no idea, could be empty
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SMapList"), 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
