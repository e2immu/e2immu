
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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestBasicsOpposite extends CommonTestRunner {

    public TestBasicsOpposite() {
        super(true);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("string".equals(d.fieldInfo().name)) {
            int expect = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expect, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("collection".equals(d.variableName) && "add".equals(d.methodInfo.name) && "0".equals(d.statementId)) {
            Assert.assertTrue("Class is " + d.currentValue.getClass(), d.currentValue instanceof VariableValue);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
        }
        if ("org.e2immu.analyser.testexample.BasicsOpposite.string".equals(d.variableName) && "setString".equals(d.methodInfo.name)) {
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("getString".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertEquals(MultiLevel.NULLABLE, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
        if ("setString".equals(d.methodInfo().name)) {
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            FieldInfo string = d.methodInfo().typeInfo.getFieldByName("string", true);
            Assert.assertEquals(Level.TRUE, methodLevelData.fieldSummaries.get(string).getProperty(VariableProperty.ASSIGNED));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("BasicsOpposite", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
