
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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestObjectFlowFreezableSet extends CommonTestRunner {

    public TestObjectFlowFreezableSet() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method1".equals(d.methodInfo.name)) {
            if ("1".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.ObjectCreation);
                Assert.assertEquals("add", d.objectFlow.getModifyingAccess().methodInfo.name);
                Assert.assertTrue(d.objectFlow.marks().isEmpty());
            }
            if ("2".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.ParentFlows);
                ObjectFlow parent = ((ObjectFlow.ParentFlows) d.objectFlow.origin).objectFlows.stream().findFirst().orElseThrow();
                Assert.assertTrue(parent.origin instanceof ObjectFlow.ObjectCreation);
                Assert.assertEquals("add", d.objectFlow.getModifyingAccess().methodInfo.name);
            }
            if ("3".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.ParentFlows);
                Assert.assertEquals("isFrozen", ((ObjectFlow.MethodCall) d.objectFlow.getNonModifyingObjectAccesses().findFirst().orElseThrow()).methodInfo.name);

                ObjectFlow parent = ((ObjectFlow.ParentFlows) d.objectFlow.origin).objectFlows.stream().findFirst().orElseThrow();
                Assert.assertTrue(parent.origin instanceof ObjectFlow.ParentFlows);
                Assert.assertEquals("add", parent.getModifyingAccess().methodInfo.name);

                ObjectFlow parent2 = ((ObjectFlow.ParentFlows) parent.origin).objectFlows.stream().findFirst().orElseThrow();
                Assert.assertTrue(parent2.origin instanceof ObjectFlow.ObjectCreation);
                Assert.assertEquals("add", parent2.getModifyingAccess().methodInfo.name);
                Assert.assertTrue(d.objectFlow.marks().isEmpty());
            }
            if ("4".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.ParentFlows);
                Assert.assertEquals("isFrozen", ((ObjectFlow.MethodCall) d.objectFlow.getNonModifyingObjectAccesses().findFirst().orElseThrow()).methodInfo.name);
                Assert.assertEquals("freeze", d.objectFlow.getModifyingAccess().methodInfo.name);
                Assert.assertEquals("[freeze]", d.objectFlow.marks().toString());
            }
            if ("5".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.ParentFlows);
                Assert.assertEquals("isFrozen", ((ObjectFlow.MethodCall) d.objectFlow.getNonModifyingObjectAccesses().findFirst().orElseThrow()).methodInfo.name);
                Assert.assertNull(d.objectFlow.getModifyingAccess());
            }
            if ("6".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.ParentFlows);
                Assert.assertEquals(2L, d.objectFlow.getNonModifyingObjectAccesses().count());
                Assert.assertNull(d.objectFlow.getModifyingAccess());
                Assert.assertEquals("[freeze]", d.objectFlow.marks().toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method2".equals(methodInfo.name) && "3".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
            if ("method3".equals(methodInfo.name) && "3".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
            // the argument to method9 should be frozen already, so we can call "stream()" but not "add()"
            if ("method9".equals(methodInfo.name)) {
                if ("0".equals(numberedStatement.streamIndices())) {
                    // TODO Assert.assertFalse(numberedStatement.errorValue.isSet());
                }
                if ("1".equals(numberedStatement.streamIndices())) {
                   // TODO Assert.assertTrue(numberedStatement.errorValue.isSet());
                }
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("SET8".equals(fieldInfo.name) && iteration > 0) {
                ObjectFlow objectFlow = fieldInfo.fieldAnalysis.get().getObjectFlow();
               // TODO  Assert.assertEquals("[freeze]", objectFlow.marks().toString());
            }
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlowFreezableSet", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());

    }

}
