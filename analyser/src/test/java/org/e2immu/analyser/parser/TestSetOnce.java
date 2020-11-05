
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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TestSetOnce extends CommonTestRunner {

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("t".equals(d.fieldInfo().name) && d.iteration() > 0) {
            Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));

        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();

        if ("get".equals(d.methodInfo().name)) {
            if (d.iteration() == 0) {
                Assert.assertFalse(methodLevelData.linksHaveBeenEstablished.isSet());
            } else {
                Assert.assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());

                VariableInfo tv = d.getReturnAsVariable();
                Assert.assertTrue(tv.linkedVariablesIsSet());
                Assert.assertEquals(1, tv.getLinkedVariables().size());
                if (d.iteration() > 1) {
                    Set<Variable> set = tv.getLinkedVariables();
                    Assert.assertEquals(2, set.size());
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("set".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && d.iteration() > 0) {
            Assert.assertEquals("null == this.t", d.statementAnalysis().stateData.precondition.get().toString());
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SetOnce"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
