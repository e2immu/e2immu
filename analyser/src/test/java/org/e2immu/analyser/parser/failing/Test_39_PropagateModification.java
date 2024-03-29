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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.failing.testexample.Consumer_0;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_39_PropagateModification extends CommonTestRunner {
    public Test_39_PropagateModification() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name) && "PropagateModification_0".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "myConsumer".equals(p.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        TypeContext typeContext = testClass("PropagateModification_0", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
        TypeInfo myConsumer = typeContext.getFullyQualified(
                Consumer_0.class.getCanonicalName() + ".MyConsumer", true);
        MethodInfo methodInfo = myConsumer.findUniqueMethod("accept", 1);
        assertTrue(methodInfo.isAbstract());
        assertTrue(methodInfo.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD).isDelayed());
        ParameterAnalysis p0 = methodInfo.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
        assertTrue(p0.getProperty(Property.MODIFIED_VARIABLE).isDelayed());
    }

    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "myConsumer".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ClassWithConsumer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        // warning: typical System.out potential null pointer exception, no Annotated APIs
        testClass("PropagateModification_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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

        // test that the different "This" scopes do not matter in equality of field references
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            TypeInfo map = typeMap.get(Map.class);
            FieldInfo fieldInfo = new FieldInfo(Identifier.constant("test"),
                    typeMap.getPrimitives().charParameterizedType(), "test", system);
            fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder().build());
            FieldReference fr1 = new FieldReference(typeMap, fieldInfo);
            FieldReference fr2 = new FieldReference(typeMap, fieldInfo, new VariableExpression(new This(typeMap, map)), map);
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
        TypeInfo classWithConsumer = typeContext.getFullyQualified(
                "org.e2immu.analyser.parser.failing.testexample.PropagateModification_7.ClassWithConsumer", true);
        MethodInfo accept = classWithConsumer.findUniqueMethod("accept", 1);
        assertTrue(accept.isAbstract());
        assertEquals(DV.FALSE_DV, accept.getAnalysis().getProperty(Property.MODIFIED_METHOD));
    }

    @Test
    public void test_8() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("name".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("name", d.fieldAnalysis().getValue().toString());
                if (d.fieldAnalysis().getValue() instanceof VariableExpression ve) {
                    assertTrue(ve.variable() instanceof ParameterInfo);
                } else fail();
                assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV,
                        d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE));
                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if ("n".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<m:getName>";
                            case 1 -> "<f:name>";
                            default -> "myConsumer.name";
                        };
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        String expect = d.iteration() <= 1 ? "<m:getName>" : "myConsumer.name";
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "name".equals(fr.fieldInfo.name)) {
                    assertFalse(fr.scopeIsThis());
                    assertEquals("myConsumer", fr.scope.toString());

                    if ("0".equals(d.statementId())) {
                        assertTrue(d.iteration() > 0);
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("nullable instance type String", vi1.getValue().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());

                        // still delayed value in iteration 1 because "someValueWasDelayed"
                        String expect = d.iteration() == 1 ? "<f:name>" : "nullable instance type String";
                        assertEquals(expect, d.currentValue().toString());
                    }

                    assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() <= 1 ? "<m:getName>" : "myConsumer.name";
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("myConsumer.name", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        TypeContext typeContext = testClass("PropagateModification_8", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
        TypeInfo classWithConsumer = typeContext.getFullyQualified(
                "org.e2immu.analyser.parser.failing.testexample.PropagateModification_8.ClassWithConsumer", true);
        MethodInfo accept = classWithConsumer.findUniqueMethod("abstractAccept", 1);
        assertTrue(accept.isAbstract());
        assertEquals(DV.FALSE_DV, accept.getAnalysis().getProperty(Property.MODIFIED_METHOD));

        ParameterInfo p0 = accept.methodInspection.get().getParameters().get(0);
        ParameterAnalysis p0Ana = p0.parameterAnalysis.get();

        AnnotationExpression ae = p0.getInspection().getAnnotations().get(0);
        assertFalse(ae.e2ImmuAnnotationParameters().contract()); // not explicitly contracted, acceptVerifyAsContracted
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0Ana.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.TRUE_DV, p0Ana.getProperty(Property.MODIFIED_VARIABLE));
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
