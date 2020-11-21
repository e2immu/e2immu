
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

import org.e2immu.analyser.analyser.VariableInfo;
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
        if ("list".equals(d.methodInfo().name) && "list".equals(d.variableName()) && "3".equals(d.statementId())) {
            Assert.assertEquals("map.get(a)", d.currentValue().toString());

            // NOTE: this is in contradiction with the state, but here we test the fact that get can return null
            Assert.assertEquals(MultiLevel.NULLABLE, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
        }
        if ("add".equals(d.methodInfo().name) && "bs".equals(d.variableName()) && "3".equals(d.statementId())) {
            Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("3".equals(d.statementId()) && "list".equals(d.methodInfo().name)) {
            if (d.iteration() == 0) {
                Assert.assertEquals("(not (null == map.get(a)) and not (null == a))", d.state().toString());
            } else {
                Assert.assertEquals("not (null == map.get(a))", d.state().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("list".equals(name)) {
            VariableInfo returnValue1 = d.getReturnAsVariable();
            if (d.iteration() == 0) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, returnValue1.getProperty(VariableProperty.NOT_NULL));

                // the end result
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            } else {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, returnValue1.getProperty(VariableProperty.NOT_NULL));

                // the end result
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        }
        if ("copy".equals(name)) {
            VariableInfo returnValue = d.getReturnAsVariable();
            Assert.assertEquals(MultiLevel.MUTABLE, returnValue.getProperty(VariableProperty.IMMUTABLE));
        }
        if ("add".equals(name) && d.methodInfo().methodInspection.get().getParameters().size() == 3) {
            ParameterInfo parameterInfo = d.methodInfo().methodInspection.get().getParameters().get(2);
            if ("bs".equals(parameterInfo.name)) {
                int modified = d.parameterAnalyses().get(2).getProperty(VariableProperty.MODIFIED);
                Assert.assertEquals(Level.FALSE, modified);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SMapList"), 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
