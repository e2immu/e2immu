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
                    assertEquals("number/*(Integer)*/", d.currentValue().toString());
                    assertNotEquals("0.1.1", d.statementId());
                    assertNotEquals("1", d.statementId());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("\"Integer: \"+number/*(Integer)*/", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("in/*(Number)*/ instanceof Integer&&null!=number?\"Integer: \"+number/*(Integer)*/:<return value>", d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("!(in instanceof Integer)&&in instanceof Number&&null!=in?\"Number: \"+in/*(Number)*/:\"Integer: \"+in/*(Number)*/", d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals("in instanceof Number&&null!=in?!(in instanceof Integer)?\"Number: \"+in/*(Number)*/:\"Integer: \"+in/*(Number)*/:<return value>", d.currentValue().toString());
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
                if ("list".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern pattern) {
                        assertEquals("1", pattern.getStatementIndexOfBlockVariable());
                    } else fail();
                    assertTrue(d.statementId().startsWith("1"));
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<vp:collection:identity:collection@Method_add_0;not_null:collection@Method_add_0>/*(List<String>)*/"
                                : "collection/*(List<String>)*/";
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "collection:-1,list:0,return add:-1"
                                : "collection:1,list:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<vp:collection:identity:collection@Method_add_0;not_null:collection@Method_add_0>/*(List<String>)*/"
                                : "collection/*(List<String>)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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

                        String expectLv = d.iteration() == 0 ? "collection:-1,return add:0" : "return add:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                // FALSE because no AnnotatedAPI, addAll is not modifying!
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
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

                        String expectLv = d.iteration() == 0 ? "object:0,return create:-1,string:-1" : "object:0,string:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
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

                        String expectLv = d.iteration() == 0 ? "object:1,return create:-1,string:0" : "object:1,string:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("bool".equals(d.variableName())) {
                    String VP_DELAY = "<vp:object:identity:object@Method_create_0.0.0;not_null:object@Method_create_0.0.0>/*(Boolean)*/";

                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(VariableInfoContainer.Level.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? VP_DELAY : "object/*(Boolean)*/";
                        assertEquals(expected, prev.getValue().toString());

                        String expect = d.iteration() == 0 ? VP_DELAY : "object/*(Boolean)*/";
                        assertEquals(expect, d.currentValue().toString());

                        assertEquals("Type java.lang.Boolean", d.currentValue().returnType().toString());
                    }
                }
                if ("integer".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals(VariableInfoContainer.Level.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<s:Integer>" : "object/*(Integer)*/";
                        assertEquals(expected, prev.getValue().toString());

                        String expect = d.iteration() == 0 ? "<s:Integer>" : "object/*(Integer)*/";
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
                    String expected = d.iteration() == 0 ? "<instanceOf:Boolean>" : "object instanceof Boolean&&null!=object";
                    assertEquals(expected, d.condition().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expected = d.iteration() == 0
                            ? "!<instanceOf:Boolean>&&(!(object instanceof String)||null==object)"
                            : "(!(object instanceof Boolean)||null==object)&&(!(object instanceof String)||null==object)";
                    assertEquals(expected, d.state().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<instanceOf:Integer>" : "object instanceof Integer&&null!=object";
                    assertEquals(expected, d.condition().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expected = d.iteration() == 0
                            ? "!<instanceOf:Boolean>&&!<instanceOf:Integer>&&(!(object instanceof String)||null==object)"
                            : "(!(object instanceof Boolean)||null==object)&&(!(object instanceof Integer)||null==object)&&(!(object instanceof String)||null==object)";
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo integer = typeMap.get(Integer.class);
            assertEquals(DV.TRUE_DV, integer.typeAnalysis.get().getProperty(Property.CONTAINER));
            TypeInfo boxedBool = typeMap.get(Boolean.class);
            assertEquals(DV.TRUE_DV, boxedBool.typeAnalysis.get().getProperty(Property.CONTAINER));
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "expression".equals(p.name)) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("nullable instance type Expression/*@Identity*/", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name)
                        && "ne".equals(fr.scope.toString())) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "<f:expression>";
                            default -> "nullable instance type Expression";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() <= 1 ? "expression:0,ne.expression:0,x:0"
                                : "expression/*(org.e2immu.analyser.parser.minor.testexample.InstanceOf_10.Negation)*/.expression:1,expression:0,ne.expression:0,x:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        fail("ne.expression should not exist here!");
                    }
                }
                if ("ne".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:expression:container@Record_Negation;immutable@Record_Negation;independent@Record_Negation>/*(Negation)*/";
                            case 1 -> "<vp:expression:assign_to_field@Parameter_expression;initial@Field_expression>/*(Negation)*/";
                            default -> "expression/*(Negation)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() <= 1 ? "expression:-1,ne:0" : "expression:1,ne:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:expression:container@Record_Negation;immutable@Record_Negation;independent@Record_Negation>/*(Negation)*/";
                            case 1 -> "<vp:expression:assign_to_field@Parameter_expression;initial@Field_expression>/*(Negation)*/";
                            default -> "expression/*(Negation)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() <= 1 ? "expression:-1,ne.expression:-1,x:-1"
                                : "expression/*(org.e2immu.analyser.parser.minor.testexample.InstanceOf_10.Negation)*/.expression:1,expression:1,ne.expression:1,x:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        fail(); // should not exist here!
                    }
                }
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name)
                        && "expression/*(Negation)*/".equals(fr.scope.toString())) {
                    assertTrue(d.iteration() >= 2);
                    if ("2".equals(d.statementId())) {
                        assertEquals("nullable instance type Expression", d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("x".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<f:expression>" : "expression/*(Negation)*/.expression";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTAINER);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.1.0".equals(d.statementId())) {
                        assertEquals("expression", d.currentValue().toString());
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTAINER);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "expression instanceof Negation&&null!=expression?<f:expression>:expression";
                            default -> "expression instanceof Negation&&null!=expression?expression/*(Negation)*/.expression:expression";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTAINER);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                        String expectLv = switch (d.iteration()) {
                            case 0, 1 -> "expression:0,ne.expression:0,x:0";
                            default -> "expression/*(org.e2immu.analyser.parser.minor.testexample.InstanceOf_10.Negation)*/.expression:1,expression:0,ne.expression:0,x:0";
                        };
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "expression instanceof Negation&&null!=expression?<f:expression>:expression";
                            default -> "expression instanceof Negation&&null!=expression?expression/*(Negation)*/.expression:expression";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTAINER);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                assertDv(d.p(0), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, DV.FALSE_DV, Property.CONTAINER);
            }
            if ("Negation".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, DV.TRUE_DV, Property.CONTAINER);
            }
        };
        testClass("InstanceOf_10", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }

    @Test
    public void test_10_2() throws IOException {
        testClass("InstanceOf_10", 0, 0, new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }


    @Test
    public void test_11() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        String expect = d.iteration() <= 1
                                ? "<instanceOf:Sum>&&null!=<m:numericPartOfLhs>?<new:XB>:<return value>" : "";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = switch (d.iteration()) {
                            case 0 -> "evaluationContext:-1,ne:-1,return method:0,this.expression:-1";
                            case 1 -> "ne:-1,return method:0,this.expression:-1";
                            default -> "";
                        };
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name)
                        && "ne".equals(fr.scope.toString())) {
                    assertTrue(d.statementId().startsWith("3"), "In " + d.statementId());
                }
                if ("x".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String expect = d.iteration() <= 1
                                ? "<instanceOf:Negation>?<f:expression>:<f:expression>" : "";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = switch (d.iteration()) {
                            case 0 -> "evaluationContext:-1,ne.expression:0,this.expression:-1,x:0";
                            case 1 -> "ne.expression:0,this.expression:0,x:0";
                            default -> "";
                        };
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("ne".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("3"), "In " + d.statementId());
                }
            }
        };
        testClass("InstanceOf_11", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
