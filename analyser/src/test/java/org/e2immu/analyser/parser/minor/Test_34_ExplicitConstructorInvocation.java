
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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.minor.testexample.ExplicitConstructorInvocation_7;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_34_ExplicitConstructorInvocation extends CommonTestRunner {

    public Test_34_ExplicitConstructorInvocation() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("ExplicitConstructorInvocation_0", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("ExplicitConstructorInvocation_1", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("ExplicitConstructorInvocation_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("ExplicitConstructorInvocation_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("ExplicitConstructorInvocation_4".equals(d.methodInfo().name)
                    && d.methodInfo().methodInspection.get().getParameters().size() == 1) {
                if (d.variable() instanceof FieldReference fr && "index".equals(fr.fieldInfo.name)) {
                    String expected = d.iteration() == 0 ? "<f:generator>" : "ExplicitConstructorInvocation_4.generator";
                    if ("0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("ExplicitConstructorInvocation_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        // 3 errors: private fields not read outside constructors

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().isConstructor && d.methodInfo().methodInspection.get().getParameters().size() == 1
                    && "ExplicitConstructorInvocation_5".equals(d.methodInfo().methodInspection.get().getParameters().get(0).parameterizedType.typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "parent".equals(fr.fieldInfo.name)) {
                    assertEquals("parent/*@NotNull*/", d.currentValue().toString());
                    assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("parent".equals(d.fieldInfo().name)) {
                String expected = "[null,parentContext/*@NotNull*/,parent/*@NotNull*/]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
            }
            if ("typeMap".equals(d.fieldInfo().name)) {
                assertEquals("typeMap", d.fieldAnalysis().getValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TypeMap".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        testClass("ExplicitConstructorInvocation_5", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // discovered: no reEvaluate in StringConcat
    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().isConstructor && d.methodInfo().methodInspection.get().getParameters().size() == 2
                    && "ExplicitConstructorInvocation_6".equals(d.methodInfo().methodInspection.get().getParameters().get(0).parameterizedType.typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "fullyQualifiedName".equals(fr.fieldInfo.name)) {
                    if (fr.scopeIsThis()) {
                        if ("0".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "<f:enclosingType.fullyQualifiedName>+\".\"+simpleName";
                                case 1 -> "<wrapped:fullyQualifiedName>";
                                default -> "enclosingType.fullyQualifiedName+\".\"+simpleName";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        } else fail("?" + d.statementId());
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("fullyQualifiedName".equals(d.fieldInfo().name)) {
                String expected = d.iteration() == 0 ? "<f:fullyQualifiedName>" :
                        "[instance type String,instance type String,\"\".equals(packageName)?simpleName:packageName+\".\"+simpleName,\"\".equals(packageName)?simpleName:packageName+\".\"+simpleName]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
            }
        };
        // 4 errors: private fields not read outside constructors
        testClass("ExplicitConstructorInvocation_6", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_7() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExplicitConstructorInvocation_7".equals(d.methodInfo().name) && numParams == 2) {
                ParameterizedType typeOfParam1 = d.methodInfo().methodInspection.get().getParameters().get(1).parameterizedType;
                assertNotNull(typeOfParam1.typeInfo);
                if ("Primitives".equals(typeOfParam1.typeInfo.simpleName)) {
                    if ("0".equals(d.statementId())) {
                        VariableInfoContainer vic = d.statementAnalysis().getVariable(ExplicitConstructorInvocation_7.class.getCanonicalName() + ".primitives");
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:primitives>";
                            case 1 -> "<s:Primitives>";
                            default -> "primitives/*@NotNull*/";
                        };
                        assertEquals(expected, vic.current().getValue().toString());
                    }
                }
            }
        };
        testClass("ExplicitConstructorInvocation_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }


    @Test
    public void test_8() throws IOException {
        testClass("ExplicitConstructorInvocation_8", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("LoopStatement".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.context().evaluationContext().delayStatementBecauseOfECI());
                }
            }
        };
        // unused parameter "structure"
        testClass("ExplicitConstructorInvocation_9", 0, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }

    @Test
    public void test_9_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("LoopStatement".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertFalse(d.context().evaluationContext().delayStatementBecauseOfECI());
                }
            }
        };
        // unused parameter "structure"
        testClass("ExplicitConstructorInvocation_9", 0, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(false).build());
    }

    @Test
    public void test_10() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("merge".equals(d.methodInfo().name) && "UnknownExpression".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() == 0 ? "<m:merge>" : "/*inline merge*/new UnknownExpression(v||condition.other())";
                // broken by Cause.SINGLE_RETURN_VALUE
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("UnknownExpression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("ExplicitConstructorInvocation_10", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // problem when forcing alphabetic analysis; works perfectly fine if we can do things in good order
    @Test
    public void test_11() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("BaseExpression".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("<eci>", d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("BaseExpression".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr) {
                    assertTrue(Set.of("complexity", "identifier").contains(fr.fieldInfo.name));
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("BaseExpression".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //3
                FieldInfo identifier = d.methodInfo().typeInfo.typeInspection.get().parentClass().typeInfo.getFieldByName("identifier", true);
                List<VariableInfo> viList = d.methodAnalysis().getFieldAsVariable(identifier);
                assertEquals(1, viList.size());
            }
            if ("BinaryOperator".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //4
            }
            if ("BitwiseAnd".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //1
            }
            if ("ElementImpl".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //2
            }
        };
        testClass("ExplicitConstructorInvocation_11", 0, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }


    // yet another order, again problematic
    // eval order is 1,3,4,5
    @Test
    public void test_12() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("X5_BaseExpression".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //3
            }
            if ("X3_BinaryOperator".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //4
            }
            if ("X4_BitwiseAnd".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //2
            }
            if ("X1_ElementImpl".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //1
            }
        };
        testClass("ExplicitConstructorInvocation_12", 0, 1, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_13() throws IOException {
        testClass("ExplicitConstructorInvocation_13", 0, 1, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }
}
