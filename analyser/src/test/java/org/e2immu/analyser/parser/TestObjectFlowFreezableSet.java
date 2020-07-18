
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

public class TestObjectFlowFreezableSet extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlowFreezableSet.class);

    public TestObjectFlowFreezableSet() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method1".equals(d.methodInfo.name)) {
            if ("1".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertSame(Origin.NEW_OBJECT_CREATION, d.objectFlow.origin);
                Assert.assertEquals("add", d.objectFlow.getModifyingAccess().methodInfo.name);
                Assert.assertTrue(d.objectFlow.marks().isEmpty());
            }
            if ("2".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertSame(Origin.INTERNAL, d.objectFlow.origin);
                ObjectFlow parent = d.objectFlow.getPrevious().findFirst().orElseThrow();
                Assert.assertSame(Origin.NEW_OBJECT_CREATION, parent.origin);
                Assert.assertEquals("add", d.objectFlow.getModifyingAccess().methodInfo.name);
            }
            if ("3".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertSame(Origin.INTERNAL, d.objectFlow.origin);
                Assert.assertEquals("isFrozen", ((MethodAccess) d.objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);

                ObjectFlow parent = d.objectFlow.getPrevious().findFirst().orElseThrow();
                Assert.assertSame(Origin.INTERNAL, parent.origin);
                Assert.assertEquals("add", parent.getModifyingAccess().methodInfo.name);

                ObjectFlow parent2 = parent.getPrevious().findFirst().orElseThrow();
                Assert.assertSame(Origin.NEW_OBJECT_CREATION, parent2.origin);
                Assert.assertEquals("add", parent2.getModifyingAccess().methodInfo.name);
                Assert.assertTrue(d.objectFlow.marks().isEmpty());
            }
            if ("4".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertSame(Origin.INTERNAL, d.objectFlow.origin);
                Assert.assertEquals("isFrozen", ((MethodAccess) d.objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);
                Assert.assertEquals("freeze", d.objectFlow.getModifyingAccess().methodInfo.name);
                Assert.assertEquals("[mark]", d.objectFlow.marks().toString());
            }
            if ("5".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertSame(Origin.INTERNAL, d.objectFlow.origin);
                Assert.assertEquals("isFrozen", ((MethodAccess) d.objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);
                Assert.assertNull(d.objectFlow.getModifyingAccess());
            }
            if ("6".equals(d.statementId) && "set1".equals(d.variableName)) {
                Assert.assertSame(Origin.INTERNAL, d.objectFlow.origin);
                Assert.assertEquals(2L, d.objectFlow.getNonModifyingAccesses().count());
                Assert.assertNull(d.objectFlow.getModifyingAccess());
                Assert.assertEquals("[mark]", d.objectFlow.marks().toString());
            }
        }

        if ("method4".equals(d.methodInfo.name) && "set4".equals(d.variableName)) {
            if ("1".equals(d.statementId)) {
                Assert.assertTrue(d.objectFlow.marks().isEmpty());
            }
            if ("4".equals(d.statementId)) {
                Assert.assertEquals("[mark]", d.objectFlow.marks().toString());
            }
        }

        if ("method7".equals(d.methodInfo.name) && "set7".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                Assert.assertTrue("Have " + d.objectFlow.marks(), d.objectFlow.marks().isEmpty());
            }
            // now after set7.freeze():
            if ("1".equals(d.statementId)) {
                Assert.assertEquals("[mark]", d.objectFlow.marks().toString());
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("method4".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().objectFlow.isSet());
            ObjectFlow objectFlow = methodInfo.methodAnalysis.get().objectFlow.get();
            Assert.assertEquals("[mark]", objectFlow.marks().toString());
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, methodInfo.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        }
        if ("method6".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().objectFlow.isSet());
            ObjectFlow objectFlow = methodInfo.methodAnalysis.get().objectFlow.get();
            Assert.assertTrue(objectFlow.marks().isEmpty());
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, methodInfo.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        }
        if ("method7".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().objectFlow.isSet());
            ObjectFlow objectFlow = methodInfo.methodAnalysis.get().objectFlow.get();
            Assert.assertEquals("[mark]", objectFlow.marks().toString());
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, methodInfo.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, numberedStatement, conditional) -> {
        if ("method2".equals(methodInfo.name) && "3".equals(numberedStatement.streamIndices())) {
            Assert.assertTrue(numberedStatement.errorValue.isSet());
        }
        if ("method3".equals(methodInfo.name) && "3".equals(numberedStatement.streamIndices())) {
            Assert.assertTrue(numberedStatement.errorValue.isSet());
        }
        // the argument to method9 should be frozen already, so we can call "stream()" but not "add()"
        if ("method9".equals(methodInfo.name)) {
            if ("0".equals(numberedStatement.streamIndices())) {
                Assert.assertFalse(numberedStatement.errorValue.isSet());
            }
            if ("1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if ("SET5".equals(fieldInfo.name) && iteration > 0) {
            int immutable = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, immutable);
        }
        if ("SET10".equals(fieldInfo.name) && iteration > 0) {
            ObjectFlow objectFlow = fieldInfo.fieldAnalysis.get().getObjectFlow();
            LOGGER.info("Object flow of SET10 at iteration {}: {}", iteration, objectFlow.detailed());
            Assert.assertTrue(objectFlow.marks().isEmpty());
            int immutable = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, immutable);
        }

        if ("SET8".equals(fieldInfo.name) && iteration > 0) {
            ObjectFlow objectFlow = fieldInfo.fieldAnalysis.get().getObjectFlow();
            LOGGER.info("Object flow of SET8 at iteration {}: {}", iteration, objectFlow.detailed());
            Assert.assertEquals("[mark]", objectFlow.marks().toString());
            int immutable = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, immutable);
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        MethodInfo stream = collection.findUniqueMethod("stream", 0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    @Test
    public void test() throws IOException {
        testClass("ObjectFlowFreezableSet", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());

    }

}
