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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.failing.testexample.InspectionGaps_1;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
tries to catch the remaining problems with the inspection system
 */
public class Test_50_InspectionGaps_AAPI extends CommonTestRunner {

    public Test_50_InspectionGaps_AAPI() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("InspectionGaps_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        TypeContext typeContext = testClass("InspectionGaps_1", 0, 0, new DebugConfiguration.Builder()
                .build());

        TypeInfo typeInfo = typeContext.getFullyQualified(InspectionGaps_1.class);

        assertEquals("org.e2immu.analyser.parser.failing.testexample.InspectionGaps_1.method1(M0 extends java.util.Set<M0>)," +
                        "org.e2immu.analyser.parser.failing.testexample.InspectionGaps_1.method2(M0 extends java.util.List<M0>)," +
                        "org.e2immu.analyser.parser.failing.testexample.InspectionGaps_1.method3(java.lang.String)",
                typeInfo.typeInspection.get().methods()
                        .stream().map(m -> m.distinguishingName).sorted().collect(Collectors.joining(",")));
    }

    @Test
    public void test_11() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("createUnmodifiable".equals(d.methodInfo().name)) {
                assertEquals("new ArrayList<>(list)", d.methodAnalysis().getSingleReturnValue().toString());

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.methodAnalysis().getProperty(Property.NOT_NULL_EXPRESSION));
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(Property.INDEPENDENT));

                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }

            if ("of".equals(d.methodInfo().name)) {
                MethodInspection mi = d.evaluationContext().getAnalyserContext().getMethodInspection(d.methodInfo());
                Statement statement0 = mi.getMethodBody().structure.getStatements().get(0);
                assertTrue(statement0 instanceof ReturnStatement returnStatement &&
                        returnStatement.expression instanceof Instance); // and not UnknownObjectCreation
            }
        };

        // null-pointer exception raised
        testClass("InspectionGaps_11", 1, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());

    }
}
