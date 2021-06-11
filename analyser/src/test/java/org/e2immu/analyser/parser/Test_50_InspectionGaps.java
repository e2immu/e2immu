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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.testexample.InspectionGaps_1;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/*
tries to catch the remaining problems with the inspection system
 */
public class Test_50_InspectionGaps extends CommonTestRunner {

    public Test_50_InspectionGaps() {
        super(false);
    }

    /* https://github.com/e2immu/e2immu/issues/46; closed! */
    @Test
    public void test_0() throws IOException {
        testClass("InspectionGaps_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        // informative for test_2; but because it crashes before getting to the visitor, I put it here
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo number = typeMap.get(Number.class);
            List<ParameterizedType> numberImplemented = number.typeInspection.get().interfacesImplemented();
            assertEquals("[Type java.io.Serializable]", numberImplemented.toString());
            TypeInfo integer = typeMap.get(Integer.class);
            List<ParameterizedType> integerImplemented = integer.typeInspection.get().interfacesImplemented();
            assertEquals(3, integerImplemented.size());
            // comparable is part of it!!!
            assertTrue(integerImplemented.toString().contains("Comparable"));
        };

        TypeContext typeContext = testClass("InspectionGaps_1", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());

        TypeInfo typeInfo = typeContext.getFullyQualified(InspectionGaps_1.class);

        assertEquals("org.e2immu.analyser.testexample.InspectionGaps_1.method1(M0 extends java.util.Set<M0>)," +
                        "org.e2immu.analyser.testexample.InspectionGaps_1.method2(M0 extends java.util.List<M0>)," +
                        "org.e2immu.analyser.testexample.InspectionGaps_1.method3(java.lang.String)",
                typeInfo.typeInspection.get().methods()
                        .stream().map(m -> m.distinguishingName).sorted().collect(Collectors.joining(",")));
    }

    @Test
    public void test_2() throws IOException {


        testClass("InspectionGaps_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // IMPROVE the output of this test is not good enough (though correct!)
    @Test
    public void test_3() throws IOException {
        testClass("InspectionGaps_3", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

    // IMPROVE the MyFieldVisitor can make the decision that the field is @NotNull (but maybe not @NotNull1, ...)
    @Test
    public void test_5() throws IOException {
        testClass("InspectionGaps_5", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("InspectionGaps_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("InspectionGaps_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("InspectionGaps_8", 0, 2, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_9() throws IOException {
        testClass(List.of("InspectionGaps_9", "a.Value"), 0, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_10() throws IOException {
        testClass(List.of("InspectionGaps_10"), List.of("jmods/java.compiler.jmod"),
                0, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_11() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("InspectionGaps_11".equals(d.methodInfo().name)) {
                int expectDep = d.iteration() <= 1 ? Level.DELAY : MultiLevel.INDEPENDENT;
                assertEquals(expectDep, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }

            if ("createUnmodifiable".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("new ArrayList<>(list)", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));

                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectPm, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
                int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNnp, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
            if ("of".equals(d.methodInfo().name)) {
                MethodInspection mi = d.evaluationContext().getAnalyserContext().getMethodInspection(d.methodInfo());
                Statement statement0 = mi.getMethodBody().structure.getStatements().get(0);
                assertTrue(statement0 instanceof ReturnStatement returnStatement &&
                        returnStatement.expression instanceof NewObject); // and not UnknownObjectCreation

                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

            }
        };

        testClass(List.of("InspectionGaps_11"), List.of("jmods/java.compiler.jmod"),
                0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

    // import type fqn;
    @Test
    public void test_12() throws IOException {
        testClass(List.of("InspectionGaps_12", "a.TypeWithStaticSubType"), 0, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

    // import static type fqn.*
    @Test
    public void test_13() throws IOException {
        testClass(List.of("InspectionGaps_13", "a.TypeWithStaticSubType"), 0, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

    // flatMap
    @Test
    public void test_14() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("flatMap".equals(d.methodInfo().name)) {
                assertEquals("org.e2immu.analyser.testexample.InspectionGaps_14.flatMap(java.util.function.Function<? super T,? extends java.util.stream.Stream<? extends R>>)", d.methodInfo().fullyQualifiedName);
            }
        };
        testClass("InspectionGaps_14", 1, 1,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }
}
