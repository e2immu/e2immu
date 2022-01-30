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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_51_InstanceOf extends CommonTestRunner {

    public Test_51_InstanceOf() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expect = "in instanceof Number&&null!=in?\"Number: \"+in/*(Number)*/:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("!(in instanceof Number)||null==in",
                            d.conditionManagerForNextStatement().state().toString());
                }
            }
        };

        testClass("InstanceOf_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("InstanceOf_1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof FieldReference fr && "number".equals(fr.fieldInfo.name)) {
                    assertEquals("in instanceof Number&&null!=in?in/*(Number)*/:3.14", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
                if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                    assertEquals("in/*(Number)*/", d.currentValue().toString());
                }
            }
        };

        testClass("InstanceOf_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("in/*(Number)*/", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("in/*(Number)*/", d.currentValue().toString());
                    }
                    assertNotEquals("1", d.statementId());
                }
                if (d.variable() instanceof LocalVariableReference lvr && "integer".equals(lvr.simpleName())) {
                    assertEquals("in/*(Number)*//*(Integer)*/", d.currentValue().toString());
                    assertNotEquals("0.1.1", d.statementId());
                    assertNotEquals("1", d.statementId());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("\"Integer: \"+in/*(Number)*//*(Integer)*/", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("in/*(Number)*/ instanceof Integer?\"Integer: \"+in/*(Number)*//*(Integer)*/:<return value>", d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("in/*(Number)*/ instanceof Integer&&null!=in/*(Number)*/?\"Integer: \"+in/*(Number)*//*(Integer)*/:\"Number: \"+in/*(Number)*/", d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals("in instanceof Number&&null!=in?in/*(Number)*/ instanceof Integer?\"Integer: \"+in/*(Number)*//*(Integer)*/:\"Number: \"+in/*(Number)*/:<return value>", d.currentValue().toString());
                    }
                }
            }
        };

        testClass("InstanceOf_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        // because of no annotated APIs, Set.addAll is non-modifying, so we get a warning that we ignore the result
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "collection".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("list".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern pattern) {
                        assertEquals("1", pattern.getStatementIndexOfBlockVariable());
                    } else fail();
                    assertTrue(d.statementId().startsWith("1"));
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<p:collection>/*(List<String>)*/" : "collection/*(List<String>)*/";
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "collection:0,list:0,return add:-1"
                                : "collection:0,list:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<p:collection>/*(List<String>)*/" : "collection/*(List<String>)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<instanceOf:List<String>>&&<m:isEmpty>?\"Empty\":<m:get>"
                                : "collection/*(List<String>)*/.isEmpty()&&collection instanceof List<String>&&null!=collection?\"Empty\":collection/*(List<String>)*/.get(0)";
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "collection:-1,list:-1,return add:0" : "return add:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<instanceOf:List<String>>?<m:isEmpty>?\"Empty\":<m:get>:<return value>"
                                : "collection instanceof List<String>&&null!=collection?collection/*(List<String>)*/.isEmpty()?\"Empty\":collection/*(List<String>)*/.get(0):<return value>";
                        assertEquals(expected, d.currentValue().toString());

                        assertEquals("return add:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                // FALSE because no AnnotatedAPI, addAll is not modifying!
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("getBase".equals(d.methodInfo().name)) {
                // Stream is mutable; is it linked?
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                String expect = d.iteration() == 0 ? "<m:getBase>" : "base.stream()";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };
        testClass("InstanceOf_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertFalse(d.variable() instanceof LocalVariableReference,
                            "Found " + d.variable().fullyQualifiedName() + " in if() { } part");
                }

                if ("1".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("in/*(Number)*/", d.currentValue().toString());
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern pattern) {
                        assertFalse(pattern.isPositive());
                        assertEquals("", pattern.parentBlockIndex());
                        assertEquals("1", pattern.scope());
                    }
                }
            }
        };

        testClass("InstanceOf_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern pattern) {
                        assertFalse(pattern.isPositive());
                        assertEquals("1", pattern.parentBlockIndex());
                        assertEquals("1.1.0", pattern.scope());
                    }
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertFalse(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern,
                            "Found " + d.variable().fullyQualifiedName() + " in if() { } part");
                }
                if ("1.1.0".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("in/*(Number)*/", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertFalse(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern,
                            "Found " + d.variable().fullyQualifiedName() + " in 2");
                }
            }
        };

        testClass("InstanceOf_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("InstanceOf_6", 3, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("InstanceOf_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_8() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("in instanceof String&&null!=in", d.evaluationResult().getExpression().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("null!=in", d.evaluationResult().getExpression().toString());
                }
            }
        };

        testClass("InstanceOf_8", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }


    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("create".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "object".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(VariableInfoContainer.Level.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("nullable instance type Object/*@Identity*/", prev.getValue().toString());

                        String expect = d.iteration() == 0 ? "<p:object>" : "nullable instance type Object/*@Identity*/";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("Type java.lang.Object", p.parameterizedType.toString());

                        String expectLv = d.iteration() == 0 ? "object:0,return create:-1,string:0" : "object:0,string:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("string".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(VariableInfoContainer.Level.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("object/*(String)*/", prev.getValue().toString());

                        String expect = d.iteration() == 0 ? "<v:string>" : "object/*(String)*/";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("Type java.lang.String", d.currentValue().returnType().toString());

                        String expectLv = d.iteration() == 0 ? "object:0,return create:-1,string:0" : "object:0,string:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("bool".equals(d.variableName())) {
                    String DELAY = "<vp:object:cnn@Parameter_s;ext_not_null@Parameter_s>/*(Boolean)*/";

                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(VariableInfoContainer.Level.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? DELAY : "object/*(Boolean)*/";
                        assertEquals(expected, prev.getValue().toString());

                        String expect = d.iteration() == 0 ? DELAY : "object/*(Boolean)*/";
                        assertEquals(expect, d.currentValue().toString());

                        assertEquals("Type java.lang.Boolean", d.currentValue().returnType().toString());
                    }
                }
                if ("integer".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        final String DELAY = "object instanceof String&&null!=object?<p:object>:nullable instance type Object/*@Identity*//*(Integer)*/";

                        assertEquals(VariableInfoContainer.Level.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<vp:object:cnn@Parameter_s;ext_not_null@Parameter_s>/*(Integer)*/"
                                : "object/*(Integer)*/";
                        assertEquals(expected, prev.getValue().toString());

                        String expect = d.iteration() == 0 ? "<vp:object:cnn@Parameter_s;ext_not_null@Parameter_s>/*(Integer)*/" : "object/*(Integer)*/";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("Type java.lang.Integer", d.currentValue().returnType().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("create".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("object instanceof String&&null!=object", d.condition().toString());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("!(object instanceof String)||null==object", d.state().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("object instanceof Boolean&&null!=object", d.condition().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expected = "(!(object instanceof Boolean)||null==object)&&(!(object instanceof String)||null==object)";
                    assertEquals(expected, d.state().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("object instanceof Integer&&null!=object", d.condition().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("(!(object instanceof Boolean)||null==object)&&(!(object instanceof Integer)||null==object)&&(!(object instanceof String)||null==object)",
                            d.state().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo integer = typeMap.get(Integer.class);
            assertEquals(MultiLevel.CONTAINER_DV, integer.typeAnalysis.get().getProperty(Property.CONTAINER));
            TypeInfo boxedBool = typeMap.get(Boolean.class);
            assertEquals(MultiLevel.CONTAINER_DV, boxedBool.typeAnalysis.get().getProperty(Property.CONTAINER));
        };

        testClass("InstanceOf_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    if (d.iteration() == 3) {
                        assertEquals("", d.evaluationResult().causesOfDelay().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("expression instanceof Negation&&null!=expression", d.condition().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "expression".equals(p.name)) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("expression:0,ne:0",
                                d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("nullable instance type Expression/*@Identity*/", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name)) {
                    if ("ne".equals(fr.scope.toString())) {
                        if ("2.0.0".equals(d.statementId())) {
                            assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                            assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("2".equals(d.statementId())) {
                            fail("Not created here, and cannot merge into here");
                        }
                        if ("3".equals(d.statementId())) {
                            fail("ne.expression should not exist here!");
                        }
                    }
                    if ("<out of scope:ne:2>".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0, 1 -> "<f:expression>";
                                default -> "nullable instance type Expression";
                            };
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                            assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    }
                    if ("expression/*(Negation)*/".equals(fr.scope.toString())) {
                        assertTrue(d.iteration() >= 2);
                        if ("2".equals(d.statementId())) {
                            assertEquals("nullable instance type Expression", d.currentValue().toString());
                            assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                            assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    }
                }
                if ("ne".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:expression:container@Record_Negation;immutable@Record_Negation;independent@Record_Negation>/*(Negation)*/";
                            case 1 -> "<vp:expression:initial@Field_expression>/*(Negation)*/";
                            default -> "expression/*(Negation)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = "expression:0,ne:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasMerge());
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:expression:container@Record_Negation;immutable@Record_Negation;independent@Record_Negation>/*(Negation)*/";
                            case 1 -> "<vp:expression:initial@Field_expression>/*(Negation)*/";
                            default -> "expression/*(Negation)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        // linking is not that relevant, given that it has no -M
                    }
                    if ("3".equals(d.statementId())) {
                        fail(); // should not exist here!
                    }
                }
                if ("x".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<f:expression>" : "expression/*(Negation)*/.expression";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.1.0".equals(d.statementId())) {
                        assertEquals("expression", d.currentValue().toString());
                        assertDv(d, 0, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "expression instanceof Negation&&null!=expression?<f:expression>:expression";
                            default -> "expression instanceof Negation&&null!=expression?expression/*(Negation)*/.expression:expression";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = switch (d.iteration()) {
                            case 0, 1 -> "<out of scope:ne:2>.expression:0,expression:0,x:0";
                            default -> "expression/*(org.e2immu.analyser.parser.minor.testexample.InstanceOf_10.Negation)*/.expression:1,expression:0,x:0";
                        };
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "expression instanceof Negation&&null!=expression?<f:expression>:expression";
                            default -> "expression instanceof Negation&&null!=expression?expression/*(Negation)*/.expression:expression";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("expression".equals(d.fieldInfo().name)) {
                assertEquals("Negation", d.fieldInfo().owner.simpleName);
                assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.EXTERNAL_CONTAINER);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expression".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("Negation".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("InstanceOf_10", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }

    @Test
    public void test_10_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name)) {
                    if ("ne".equals(fr.scope.toString())) {
                        if ("2.0.0".equals(d.statementId())) {
                            assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    }
                }
                if ("ne".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:expression:container@Record_Negation;immutable@Record_Negation;independent@Record_Negation>/*(Negation)*/";
                            case 1 -> "<vp:expression:initial@Field_expression>/*(Negation)*/";
                            default -> "expression/*(Negation)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expression".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);

                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("Negation".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("expression".equals(d.fieldInfo().name)) {
                assertEquals("Negation", d.fieldInfo().owner.simpleName);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        testClass("InstanceOf_10", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }


    @Test
    public void test_11() throws IOException {
        int BIG = 20;
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("sum".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:expression>/*(Sum)*/"
                                : "<vp:expression:container@Class_InstanceOf_11;immutable@Class_InstanceOf_11>/*(Sum)*/";
                        //     assertEquals(expect, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "evaluationContext".equals(p.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        // delays in clustering in iteration 2, otherwise we'd have CM
                        assertDv(d, BIG, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expression".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);

                // only reason for waiting would be nonNumericPartOfLhs, where it appears as argument
                // but there are still delays in clustering in 0.0.1.0.0 in iteration 2
                assertDv(d.p(0), BIG, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), BIG, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("nonNumericPartOfLhs".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("numericPartOfLhs".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            switch (d.typeInfo().simpleName) {
                case "$1" -> {

                    // means: we have to wait until we know the property of the enclosing type
                    String expect = d.iteration() == 0 ? "container@Class_InstanceOf_11" : "cm@Parameter_evaluationContext;container@Class_InstanceOf_11";
                    assertDv(d, expect, BIG, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
                case "Expression", "EvaluationContext" -> assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                case "InstanceOf_11" -> assertDv(d, "cm@Parameter_evaluationContext;container@Class_InstanceOf_11", BIG, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                case "Negation", "Sum" -> assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                case "XB" -> assertDv(d, "cm@Parameter_x;container@Record_XB;mom@Parameter_x", 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                default -> fail("? " + d.typeInfo().simpleName);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("expression".equals(d.fieldInfo().name)) {
                switch (d.fieldInfo().owner.simpleName) {
                    case "InstanceOf_11" -> {
                        assertEquals("new Expression(){}", d.fieldAnalysis().getValue().toString());
                        String expect = d.iteration() == 0 ? "container@Class_InstanceOf_11"
                                : "cm@Parameter_evaluationContext;container@Class_InstanceOf_11";
                        assertDv(d, expect, BIG, MultiLevel.CONTAINER_DV, Property.EXTERNAL_CONTAINER);
                        assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                    }
                    case "Negation" -> {
                        assertEquals("expression", d.fieldAnalysis().getValue().toString());
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.EXTERNAL_CONTAINER);
                        assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                    }
                    default -> fail("? " + d.fieldInfo().owner.simpleName);
                }
            }
        };
        testClass("InstanceOf_11", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
