
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
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
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
        ObjectFlow objectFlow = d.variableInfo().getObjectFlow();
        TypeInfo currentType = d.methodInfo().typeInfo;
        InspectionProvider inspectionProvider = d.evaluationContext().getAnalyserContext();

        if ("method1".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId()) && "set1".equals(d.variableName())) {
                Assert.assertSame(Origin.NEW_OBJECT_CREATION, objectFlow.origin);
                Assert.assertEquals("add", objectFlow.getModifyingAccess().methodInfo.name);
                Assert.assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            if ("2".equals(d.statementId()) && "set1".equals(d.variableName())) {
                Assert.assertSame(Origin.INTERNAL, objectFlow.origin);
                ObjectFlow parent = objectFlow.getPrevious().findFirst().orElseThrow();
                Assert.assertSame(Origin.NEW_OBJECT_CREATION, parent.origin);
                Assert.assertEquals("add", objectFlow.getModifyingAccess().methodInfo.name);
            }
            if ("3".equals(d.statementId()) && "set1".equals(d.variableName())) {
                Assert.assertSame(Origin.INTERNAL, objectFlow.origin);
                Assert.assertEquals("isFrozen", ((MethodAccess) objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);

                ObjectFlow parent = objectFlow.getPrevious().findFirst().orElseThrow();
                Assert.assertSame(Origin.INTERNAL, parent.origin);
                Assert.assertEquals("add", parent.getModifyingAccess().methodInfo.name);

                ObjectFlow parent2 = parent.getPrevious().findFirst().orElseThrow();
                Assert.assertSame(Origin.NEW_OBJECT_CREATION, parent2.origin);
                Assert.assertEquals("add", parent2.getModifyingAccess().methodInfo.name);
                Assert.assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            if ("4".equals(d.statementId()) && "set1".equals(d.variableName())) {
                Assert.assertSame(Origin.INTERNAL, objectFlow.origin);
                Assert.assertEquals("isFrozen", ((MethodAccess) objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);
                Assert.assertEquals("freeze", objectFlow.getModifyingAccess().methodInfo.name);
                Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            }
            if ("5".equals(d.statementId()) && "set1".equals(d.variableName())) {
                Assert.assertSame(Origin.INTERNAL, objectFlow.origin);
                Assert.assertEquals("isFrozen", ((MethodAccess) objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);
                Assert.assertNull(objectFlow.getModifyingAccess());
            }
            if ("6".equals(d.statementId()) && "set1".equals(d.variableName())) {
                Assert.assertSame(Origin.INTERNAL, objectFlow.origin);
                Assert.assertEquals(2L, objectFlow.getNonModifyingAccesses().count());
                Assert.assertNull(objectFlow.getModifyingAccess());
                Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            }
        }

        if ("method4".equals(d.methodInfo().name) && "set4".equals(d.variableName())) {
            if ("1".equals(d.statementId())) {
                Assert.assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            if ("4".equals(d.statementId())) {
                Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            }
        }

        if ("method7".equals(d.methodInfo().name) && "set7".equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertTrue("Have " + objectFlow.marks(currentType, inspectionProvider), objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            // now after set7.freeze():
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo currentType = d.methodInfo().typeInfo;
        InspectionProvider inspectionProvider = d.evaluationContext().getAnalyserContext();

        if ("method4".equals(d.methodInfo().name)) {
            ObjectFlow objectFlow = d.methodAnalysis().getObjectFlow();
            Assert.assertNotNull(objectFlow);
            Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
        if ("method6".equals(d.methodInfo().name)) {
            ObjectFlow objectFlow = d.methodAnalysis().getObjectFlow();
            Assert.assertNotNull(objectFlow);
            Assert.assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
        if ("method7".equals(d.methodInfo().name)) {
            ObjectFlow objectFlow = d.methodAnalysis().getObjectFlow();
            Assert.assertNotNull(objectFlow);
            Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.ONLY_AFTER));
        }
        if ("method3".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.ONLY_BEFORE));
        }
        // the argument to method9 should be frozen already, so we can call "stream()" but not "add()"
        if ("method9".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.ONLY_AFTER));
            }
            if ("1".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.ONLY_AFTER));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        String name = d.fieldInfo().name;
        TypeInfo currentType = d.fieldInfo().owner;
        InspectionProvider inspectionProvider = d.evaluationContext().getAnalyserContext();

        if ("SET5".equals(name) && iteration > 0) {
            int immutable = d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, immutable);
            int container = d.fieldAnalysis().getProperty(VariableProperty.CONTAINER);
            Assert.assertEquals(Level.TRUE, container);
        }
        if ("SET10".equals(name) && iteration > 0) {
            ObjectFlow objectFlow = d.fieldAnalysis().getObjectFlow();
            LOGGER.info("Object flow of SET10 at iteration {}: {}", iteration, objectFlow.detailed());
            Assert.assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            int immutable = d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, immutable);
        }

        if ("SET8".equals(name) && iteration > 0) {
            ObjectFlow objectFlow = d.fieldAnalysis().getObjectFlow();
            LOGGER.info("Object flow of SET8 at iteration {}: {}", iteration, objectFlow.detailed());
            Assert.assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            int immutable = d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, immutable);
            int container = d.fieldAnalysis().getProperty(VariableProperty.CONTAINER);
            Assert.assertEquals(Level.TRUE, container);
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo collection = typeMap.get(Collection.class);
        MethodInfo stream = collection.findUniqueMethod("stream", 0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    };

    @Test
    public void test() throws IOException {
        testClass("ObjectFlowFreezableSet", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());

    }

}
