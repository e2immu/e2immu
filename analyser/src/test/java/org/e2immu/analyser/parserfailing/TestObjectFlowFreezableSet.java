
/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

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
                assertSame(Origin.NEW_OBJECT_CREATION, objectFlow.origin);
                assertEquals("add", objectFlow.getModifyingAccess().methodInfo.name);
                assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            if ("2".equals(d.statementId()) && "set1".equals(d.variableName())) {
                assertSame(Origin.INTERNAL, objectFlow.origin);
                ObjectFlow parent = objectFlow.getPrevious().findFirst().orElseThrow();
                assertSame(Origin.NEW_OBJECT_CREATION, parent.origin);
                assertEquals("add", objectFlow.getModifyingAccess().methodInfo.name);
            }
            if ("3".equals(d.statementId()) && "set1".equals(d.variableName())) {
                assertSame(Origin.INTERNAL, objectFlow.origin);
                assertEquals("isFrozen", ((MethodAccess) objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);

                ObjectFlow parent = objectFlow.getPrevious().findFirst().orElseThrow();
                assertSame(Origin.INTERNAL, parent.origin);
                assertEquals("add", parent.getModifyingAccess().methodInfo.name);

                ObjectFlow parent2 = parent.getPrevious().findFirst().orElseThrow();
                assertSame(Origin.NEW_OBJECT_CREATION, parent2.origin);
                assertEquals("add", parent2.getModifyingAccess().methodInfo.name);
                assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            if ("4".equals(d.statementId()) && "set1".equals(d.variableName())) {
                assertSame(Origin.INTERNAL, objectFlow.origin);
                assertEquals("isFrozen", ((MethodAccess) objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);
                assertEquals("freeze", objectFlow.getModifyingAccess().methodInfo.name);
                assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            }
            if ("5".equals(d.statementId()) && "set1".equals(d.variableName())) {
                assertSame(Origin.INTERNAL, objectFlow.origin);
                assertEquals("isFrozen", ((MethodAccess) objectFlow.getNonModifyingAccesses().findFirst().orElseThrow()).methodInfo.name);
                assertNull(objectFlow.getModifyingAccess());
            }
            if ("6".equals(d.statementId()) && "set1".equals(d.variableName())) {
                assertSame(Origin.INTERNAL, objectFlow.origin);
                assertEquals(2L, objectFlow.getNonModifyingAccesses().count());
                assertNull(objectFlow.getModifyingAccess());
                assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            }
        }

        if ("method4".equals(d.methodInfo().name) && "set4".equals(d.variableName())) {
            if ("1".equals(d.statementId())) {
                assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            }
            if ("4".equals(d.statementId())) {
                assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            }
        }

        if ("method7".equals(d.methodInfo().name) && "set7".equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty(),
                        "Have " + objectFlow.marks(currentType, inspectionProvider));
            }
            // now after set7.freeze():
            if ("1".equals(d.statementId())) {
                assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
                assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo currentType = d.methodInfo().typeInfo;
        InspectionProvider inspectionProvider = d.evaluationContext().getAnalyserContext();

        if ("method4".equals(d.methodInfo().name)) {
            ObjectFlow objectFlow = d.methodAnalysis().getObjectFlow();
            assertNotNull(objectFlow);
            assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
        if ("method6".equals(d.methodInfo().name)) {
            ObjectFlow objectFlow = d.methodAnalysis().getObjectFlow();
            assertNotNull(objectFlow);
            assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
        if ("method7".equals(d.methodInfo().name)) {
            ObjectFlow objectFlow = d.methodAnalysis().getObjectFlow();
            assertNotNull(objectFlow);
            assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            assertNotNull(d.haveError(Message.ONLY_AFTER));
        }
        if ("method3".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            assertNotNull(d.haveError(Message.ONLY_BEFORE));
        }
        // the argument to method9 should be frozen already, so we can call "stream()" but not "add()"
        if ("method9".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                assertNotNull(d.haveError(Message.ONLY_AFTER));
            }
            if ("1".equals(d.statementId())) {
                assertNotNull(d.haveError(Message.ONLY_AFTER));
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
            assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, immutable);
            int container = d.fieldAnalysis().getProperty(VariableProperty.CONTAINER);
            assertEquals(Level.TRUE, container);
        }
        if ("SET10".equals(name) && iteration > 0) {
            ObjectFlow objectFlow = d.fieldAnalysis().getObjectFlow();
            LOGGER.info("Object flow of SET10 at iteration {}: {}", iteration, objectFlow.detailed());
            assertTrue(objectFlow.marks(currentType, inspectionProvider).isEmpty());
            int immutable = d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE);
            assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK, immutable);
        }

        if ("SET8".equals(name) && iteration > 0) {
            ObjectFlow objectFlow = d.fieldAnalysis().getObjectFlow();
            LOGGER.info("Object flow of SET8 at iteration {}: {}", iteration, objectFlow.detailed());
            assertEquals("[mark]", objectFlow.marks(currentType, inspectionProvider).toString());
            int immutable = d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE);
            assertEquals(MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK, immutable);
            int container = d.fieldAnalysis().getProperty(VariableProperty.CONTAINER);
            assertEquals(Level.TRUE, container);
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo collection = typeMap.get(Collection.class);
        MethodInfo stream = collection.findUniqueMethod("stream", 0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
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
