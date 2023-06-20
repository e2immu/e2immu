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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Test_11_MethodReferences extends CommonTestRunner {
    public Test_11_MethodReferences() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            assertNotNull(collection);
            MethodInfo stream = collection.findUniqueMethod("stream", 0);

            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, stream.methodAnalysis.get().getProperty(Property.NOT_NULL_EXPRESSION));
        };

        testClass("MethodReferences_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("MethodReferences_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            assertEquals(DV.TRUE_DV, put.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
            MethodInfo forEach = map.findUniqueMethod("forEach", 1);
            assertEquals(DV.FALSE_DV, forEach.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };

        testClass("MethodReferences_2", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                String expectValue = d.iteration() == 0 ? "<m:stream>" : "map.entrySet().stream()";
                assertEquals(expectValue, d.evaluationResult().value().toString());

                DV immutable = d.evaluationResult().evaluationContext()
                        .getProperty(d.evaluationResult().value(), Property.IMMUTABLE, true, true);
                if (d.iteration() > 0) {
                    assertEquals(MultiLevel.MUTABLE_DV, immutable);
                } else {
                    assertEquals("initial:this.map@Method_stream_0-C", immutable.toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() == 0 ? "this.map:-1" : "this.map:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        testClass("MethodReferences_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_4() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("MethodReferences_4".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
        };
        testClass("MethodReferences_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
