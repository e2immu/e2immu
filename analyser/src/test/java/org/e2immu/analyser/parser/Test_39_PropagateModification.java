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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.testexample.PropagateModification_0;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_39_PropagateModification extends CommonTestRunner {
    public Test_39_PropagateModification() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name) && "PropagateModification_0".equals(d.methodInfo().typeInfo.simpleName)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(expectCm, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
                int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectPm, p0.getProperty(VariableProperty.PROPAGATE_MODIFICATION));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "myConsumer".equals(p.name)) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectPm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectPm, d.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD));
                }
            }
        };

        TypeContext typeContext = testClass("PropagateModification_0", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
        TypeInfo myConsumer = typeContext.getFullyQualified(
                PropagateModification_0.class.getCanonicalName() + ".MyConsumer", true);
        MethodInfo methodInfo = myConsumer.findUniqueMethod("accept", 1);
        assertTrue(methodInfo.isAbstract());
        assertEquals(Level.DELAY, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        ParameterAnalysis p0 = methodInfo.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
        assertEquals(Level.DELAY, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
    }

    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "myConsumer".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectPm, d.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD));
                    }
                    if ("1".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                        assertEquals(expectPm, d.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                ParameterAnalysis myConsumer = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, myConsumer.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ClassWithConsumer".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        // warning: typical System.out potential null pointer exception, no Annotated APIs
        testClass("PropagateModification_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {

        // warning: typical System.out potential null pointer exception, no Annotated APIs
        testClass("PropagateModification_2", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("PropagateModification_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // TODO 4, 5, 6

    @Test
    public void test_7() throws IOException {

        // test that the different This scopes do not matter in equality of field references
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            TypeInfo map = typeMap.get(Map.class);
            FieldInfo fieldInfo = new FieldInfo(Identifier.CONSTANT,
                    typeMap.getPrimitives().charParameterizedType, "test", system);
            fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder().build());
            FieldReference fr1 = new FieldReference(typeMap, fieldInfo);
            FieldReference fr2 = new FieldReference(typeMap, fieldInfo, new VariableExpression(new This(typeMap, map)));
            assertEquals(fr1, fr2);

            Set<Variable> s1 = Set.of(fr1);
            Set<Variable> s2 = Set.of(fr2);
            Set<Variable> one = SetUtil.immutableUnion(s1, s2);
            assertEquals(1, one.size());
        };

        TypeContext typeContext = testClass("PropagateModification_7", 0, 0,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .build());

        // verify that the default for accept is @Modified
        TypeInfo classWithConsumer = typeContext
                .getFullyQualified("org.e2immu.analyser.testexample.PropagateModification_7.ClassWithConsumer", true);
        MethodInfo accept = classWithConsumer.findUniqueMethod("accept", 1);
        assertTrue(accept.isAbstract());
        assertEquals(Level.FALSE, accept.getAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
    }

    @Test
    public void test_8() throws IOException {
        testClass("PropagateModification_8", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_9() throws IOException {
        testClass("PropagateModification_9", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_10() throws IOException {
        testClass("PropagateModification_9", 0, 1, new DebugConfiguration.Builder()
                .build());
    }
}
