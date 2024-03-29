
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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
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
                                case 0 -> "<f:enclosingType3.fullyQualifiedName>+\".\"+simpleName3";
                                case 1 -> "<wrapped:fullyQualifiedName>";
                                default -> "enclosingType3.fullyQualifiedName+\".\"+simpleName3";
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
                        "[instance type String,instance type String,\"\".equals(packageName2)?simpleName2:packageName2+\".\"+simpleName2,\"\".equals(packageName1)?simpleName1:packageName1+\".\"+simpleName1]";
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
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExplicitConstructorInvocation_7".equals(d.methodInfo().name)) {
                ParameterizedType typeOfParam1 = d.methodInfo().methodInspection.get().getParameters().get(1).parameterizedType;
                assertNotNull(typeOfParam1.typeInfo);
                if ("List".equals(typeOfParam1.typeInfo.simpleName) && numParams == 2) {
                    EvaluationResult.ChangeData cd = d.findValueChangeByToString("primitives");
                    assertTrue(cd.markAssignment());

                    Expression value = cd.value();
                    String expected = d.iteration() == 0 ? "<s:Primitives>" : "primitives1/*@NotNull*/";
                    assertEquals(expected, value.toString());
                    if (d.iteration() == 0) {
                        if (value instanceof DelayedExpression de) {
                            List<String> parameters = de.variables(true).stream()
                                    .filter(v -> v instanceof ParameterInfo).map(Variable::simpleName).toList();
                            assertEquals("[primitives1]", parameters.toString());
                        } else fail();
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExplicitConstructorInvocation_7".equals(d.methodInfo().name)) {
                ParameterizedType typeOfParam1 = d.methodInfo().methodInspection.get().getParameters().get(1).parameterizedType;
                assertNotNull(typeOfParam1.typeInfo);
                if ("Primitives".equals(typeOfParam1.typeInfo.simpleName) && numParams == 2) {
                    assertEquals("0", d.statementId());
                    if (d.variable() instanceof FieldReference fr && "primitives".equals(fr.fieldInfo.name)) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<eci>";
                            case 1 -> "<s:Primitives>";
                            default -> "primitives3/*@NotNull*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "identifier:-1,primitives3:-1,this.complexity:-1,this.expressions:-1";
                            case 1 -> "primitives3:-1";
                            default -> "primitives3:1";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if (d.variable() instanceof FieldReference fr && "complexity".equals(fr.fieldInfo.name)) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<eci>";
                            case 1 -> "List.of().stream().mapToInt(Expression::getComplexity).sum()+`ExplicitConstructorInvocation_7.COMPLEXITY`+(null==identifier?0:<m:getComplexity>)";
                            default -> "3+List.of().stream().mapToInt(Expression::getComplexity).sum()+(null==identifier?0:identifier.getComplexity())";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "identifier:-1,primitives3:-1,this.expressions:-1,this.primitives:-1";
                            case 1 -> "identifier:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                } else if ("List".equals(typeOfParam1.typeInfo.simpleName) && numParams == 2) {
                    assertEquals("0", d.statementId());
                    if (d.variable() instanceof FieldReference fr && "primitives".equals(fr.fieldInfo.name)) {
                        String expected = d.iteration() == 0 ? "<s:Primitives>" : "primitives1/*@NotNull*/";

                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "primitives1:-1" : "primitives1:1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if (d.variable() instanceof FieldReference fr && "complexity".equals(fr.fieldInfo.name)) {
                        String expected = d.iteration() == 0
                                ? "expressions.stream().mapToInt(Expression::getComplexity).sum()+<f:COMPLEXITY>+<simplification>"
                                : "3+expressions.stream().mapToInt(Expression::getComplexity).sum()";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0
                                ? "ExplicitConstructorInvocation_7.COMPLEXITY:-1,expressions:-1,this.expressions:-1"
                                : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                } else {
                    assertEquals(3, numParams);
                    if (d.variable() instanceof FieldReference fr && "primitives".equals(fr.fieldInfo.name)) {
                        String expected = d.iteration() == 0 ? "<s:Primitives>" : "primitives2/*@NotNull*/";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "primitives2:-1" : "primitives2:1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        testClass("ExplicitConstructorInvocation_7", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("C".equals(d.methodInfo().name) && n == 0) {
                assertEquals("0", d.statementId()); // ECI!
                if (d.variable() instanceof FieldReference fr && "E2".equals(fr.fieldInfo.name)) {
                    String linked = d.iteration() <= 2 ? "this.state:-1" : "this.state:1";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "condition".equals(fr.fieldInfo.name)) {
                    String linked = d.iteration() <= 2 ? "UnknownExpression.E1:-1" : "UnknownExpression.E1:1";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "<s:Expression>";
                        case 1 -> "<vp:E1:container@Record_UnknownExpression>";
                        case 2 -> "<vp:E1:break_imm_delay@Method_merge;cm@Parameter_condition;initial:this.v@Method_merge_0-C;srv@Method_merge>";
                        default -> "UnknownExpression.E1";
                    };
                    assertEquals(expected, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "state".equals(fr.fieldInfo.name)) {
                    String linked = d.iteration() <= 2 ? "UnknownExpression.E2:-1" : "UnknownExpression.E2:1";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "<dv:UnknownExpression.E2>";
                        case 1 -> "<vp:E2:container@Record_UnknownExpression>";
                        case 2 -> "<vp:E2:break_imm_delay@Method_merge;cm@Parameter_condition;initial:this.v@Method_merge_0-C;srv@Method_merge>";
                        default -> "UnknownExpression.E2";
                    };
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("C".equals(d.methodInfo().name) && n == 4) {
                if (d.variable() instanceof FieldReference fr && "E2".equals(fr.fieldInfo.name)) {
                    fail("should not exist here");
                }
                if (d.variable() instanceof ParameterInfo pi && "condition".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() == 0 ? "<mod:Expression>" : "nullable instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "state".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() == 0 ? "<p:state>" : "nullable instance type Expression";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "condition".equals(fr.fieldInfo.name)) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("condition:0", d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() == 0 ? "<s:Expression>" : "condition";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "state".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        assertEquals("state:0", d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() == 0 ? "<p:state>" : "state";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("absolute".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "parent".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    String expected = d.iteration() <= 4 ? "<f:parent>" : "nullable instance type C";
                    assertEquals(expected, d.currentValue().toString());
                    String linked = d.iteration() == 0 ? "parent.condition:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
                if (d.variable() instanceof FieldReference fr && "condition".equals(fr.fieldInfo.name)) {
                    if ("parent".equals(fr.scope.toString())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "<f:condition>";
                            case 2, 3, 4 -> "<f:parent.condition>";
                            default -> "instance type Expression";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "this.parent:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    } else if (fr.scopeIsThis()) {
                        String expected = d.iteration() <= 4 ? "<f:condition>" : "instance type Expression";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    } else fail("Found " + fr.scope);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("E1".equals(d.fieldInfo().name)) {
                assertEquals("new UnknownExpression(true)", d.fieldAnalysis().getValue().toString());
            }
            if ("C".equals(d.fieldInfo().owner.simpleName)) {
                if ("parent".equals(d.fieldInfo().name)) {
                    if (d.iteration() > 0) {
                        assertTrue(d.fieldAnalysis().getValue() instanceof VariableExpression ve
                                && ve.variable() instanceof ParameterInfo pi
                                && "parent".equals(pi.name));
                    }
                    // parent is of mySelf type; IMMUTABLE_BREAK...
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("merge".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), DV.TRUE_DV, Property.MODIFIED_VARIABLE); // !!!!!! IMPORTANT !!!!!!
            }
            if ("merge".equals(d.methodInfo().name) && "UnknownExpression".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() == 0 ? "<m:merge>" : "/*inline merge*/new UnknownExpression(v||condition.other())";
                // broken by Cause.SINGLE_RETURN_VALUE
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("absolute".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 4 ? "<m:absolute>"
                        : "null==parent?condition:condition.merge(parent.condition)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("UnknownExpression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("C".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("ExplicitConstructorInvocation_10", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
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
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("BinaryOperator".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 4, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("BitwiseAnd".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("ElementImpl".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("getIdentifier".equals(d.methodInfo().name)) {
                if ("ElementImpl".equals(d.methodInfo().typeInfo.simpleName)) {
                    // transparent!
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Identifier".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("ElementImpl".equals(d.typeInfo().simpleName)) {
                assertEquals("Type org.e2immu.analyser.parser.minor.testexample.ExplicitConstructorInvocation_11.Identifier",
                        d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("ExplicitConstructorInvocation_11", 0, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //3
            }
            if ("X3_BinaryOperator".equals(d.methodInfo().name)) {
                String delay = switch (d.iteration()) {
                    case 0, 1 -> "cm@Parameter_identifier3;mom@Parameter_identifier3";
                    case 2 -> "mom@Parameter_identifier3";
                    default -> "cm@Parameter_identifier3";
                };
                assertDv(d.p(0), delay, 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //4
            }
            if ("X4_BitwiseAnd".equals(d.methodInfo().name)) {
                assertDv(d.p(0), "cm@Parameter_identifier4", 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //2
            }
            if ("X1_ElementImpl".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //1
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Primitives".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("X1_ElementImpl".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("X5_BaseExpression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("X3_BinaryOperator".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("X4_BitwiseAnd".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("ExplicitConstructorInvocation_12", 0, 1, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }


    // trimmed down version of 12_1
    @Test
    public void test_12_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("X3_BinaryOperator".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "identifier".equals(fr.fieldInfo.name)) {
                    assertEquals("this", fr.scope.toString());
                    String expected = d.iteration() == 0 ? "<eci>" : "identifier3";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("X5_BaseExpression".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //3
            }
            if ("X3_BinaryOperator".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //4
            }
            if ("X1_ElementImpl".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE); //1
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("X1_ElementImpl".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("X5_BaseExpression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("X3_BinaryOperator".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("ExplicitConstructorInvocation_12_1", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().isConstructor && d.methodInfo().isPrivate()) {
                int n = d.methodInfo().methodInspection.get().getParameters().size();
                assertEquals(10, n);

                if (d.variable() instanceof ParameterInfo pi && "identifier".equals(pi.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if (d.variable() instanceof FieldReference fr && "variableTarget".equals(fr.fieldInfo.name)) {
                    assertTrue(d.statementId().compareTo("7") >= 0);
                    String expected = d.iteration() == 0 ? "<p:variableTarget>"
                            : "variableTarget";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (d.methodInfo().isConstructor && d.methodInfo().isPrivate()) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        testClass("ExplicitConstructorInvocation_13", 0, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_14() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ExplicitConstructorInvocation_14".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("ExplicitConstructorInvocation_14", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
