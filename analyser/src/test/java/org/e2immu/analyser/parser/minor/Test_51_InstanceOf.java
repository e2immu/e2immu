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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_51_InstanceOf extends CommonTestRunner {

    public static final String NEW_EXPRESSION = "instance type Expression/*new Expression(){}*/";

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
                        assertEquals("in/*(Number)*/ instanceof Integer?\"Integer: \"+in/*(Number)*//*(Integer)*/:<return value>",
                                d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("in/*(Number)*/ instanceof Integer&&null!=in/*(Number)*/?\"Integer: \"+in/*(Number)*//*(Integer)*/:\"Number: \"+in/*(Number)*/",
                                d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals("in instanceof Number&&null!=in?in/*(Number)*/ instanceof Integer&&null!=in/*(Number)*/?\"Integer: \"+in/*(Number)*//*(Integer)*/:\"Number: \"+in/*(Number)*/:<return value>",
                                d.currentValue().toString());
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
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
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
                        String expectLv = d.iteration() == 0 ? "collection:-1" : "collection:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
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
                                ? "<m:isEmpty>?\"Empty\":<m:get>"
                                : "collection/*(List<String>)*/.isEmpty()?\"Empty\":collection/*(List<String>)*/.get(0)";
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "collection:-1,list:-1" : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<instanceOf:List<String>>?<m:isEmpty>?\"Empty\":<m:get>:<return value>"
                                : "collection instanceof List<String>&&null!=collection?collection/*(List<String>)*/.isEmpty()?\"Empty\":collection/*(List<String>)*/.get(0):<return value>";
                        assertEquals(expected, d.currentValue().toString());

                        String expectedLv = d.iteration() == 0 ? "collection:-1" : "";
                        assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                // FALSE because no AnnotatedAPI, addAll is not modifying!
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("getBase".equals(d.methodInfo().name)) {
                // Stream is mutable; is it linked?
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                String expect = d.iteration() == 0 ? "<m:getBase>" : "/*inline getBase*/base.stream()";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("InstanceOf_3".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
        };
        testClass("InstanceOf_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3_2() throws IOException {
        testClass("InstanceOf_3_2", 0, 1, new DebugConfiguration.Builder()
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

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "in".equals(pi.name)) {
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("in instanceof String&&null!=in?in.toString():<return value>", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("in instanceof String&&null!=in?in.toString():\"Object\"", d.currentValue().toString());
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "in".equals(pi.name)) {
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("null==in?<return value>:in.toString()", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("null==in?\"Object\":in.toString()", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("CM{state=!(in instanceof String)||null==in;parent=CM{}}",
                            d.conditionManagerForNextStatement().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("CM{state=null==in;parent=CM{}}", d.conditionManagerForNextStatement().toString());
                }
            }
        };
        testClass("InstanceOf_8", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("create".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = "object instanceof Boolean&&null!=object";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("create".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "object".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(Stage.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("nullable instance type Object/*@Identity*/", prev.getValue().toString());

                        String expect = d.iteration() == 0 ? "<mod:String>" : "nullable instance type Object/*@Identity*/";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("Type java.lang.Object", p.parameterizedType.toString());

                        assertEquals("string:1", d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("string".equals(d.variableName())) {
                    assertTrue(d.variable() instanceof LocalVariableReference);
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(Stage.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("object/*(String)*/", prev.getValue().toString());

                        String expected = d.iteration() == 0 ? "<mod:String>" : "object/*(String)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals("Type java.lang.String", d.currentValue().returnType().toString());

                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern);
                        assertEquals("object:1", d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("bool".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(Stage.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("object/*(Boolean)*/", prev.getValue().toString());

                        String expected = d.iteration() == 0 ? "<v:bool>" : "object/*(Boolean)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals("Type java.lang.Boolean", d.currentValue().returnType().toString());
                    }
                }
                if ("integer".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals(Stage.EVALUATION, d.variableInfoContainer().getLevelForPrevious());
                        assertTrue(d.variableInfoContainer().isPrevious());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("object/*(Integer)*/", prev.getValue().toString());
                        String expected = d.iteration() == 0 ? "<v:integer>" : "object/*(Integer)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals("Type java.lang.Integer", d.currentValue().returnType().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "object instanceof String&&null!=object?<new:StringConstant>:<return value>";
                            default -> "object instanceof String&&null!=object?new StringConstant(object/*(String)*/):<return value>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "object instanceof Boolean&&null!=object?<new:BooleanConstant>:object instanceof String&&null!=object?<new:StringConstant>:<return value>";
                            case 1 -> "object instanceof Boolean&&null!=object?new BooleanConstant(object/*(Boolean)*/):object instanceof String&&null!=object?<new:StringConstant>:<return value>";
                            default -> "object instanceof Boolean&&null!=object?new BooleanConstant(object/*(Boolean)*/):object instanceof String&&null!=object?new StringConstant(object/*(String)*/):<return value>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<new:BooleanConstant>" : "new BooleanConstant(bool)";
                        assertEquals(expected, d.currentValue().toString());
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
                    String expected = "object instanceof Boolean&&null!=object";
                    assertEquals(expected, d.condition().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expected = "(!(object instanceof Boolean)||null==object)&&(!(object instanceof String)||null==object)";
                    assertEquals(expected, d.state().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expected = "object instanceof Integer&&null!=object";
                    assertEquals(expected, d.condition().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expected = "(!(object instanceof Boolean)||null==object)&&(!(object instanceof Integer)||null==object)&&(!(object instanceof String)||null==object)";
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo integer = typeMap.get(Integer.class);
            assertEquals(MultiLevel.CONTAINER_DV, integer.typeAnalysis.get().getProperty(Property.CONTAINER));
            TypeInfo boxedBool = typeMap.get(Boolean.class);
            assertEquals(MultiLevel.CONTAINER_DV, boxedBool.typeAnalysis.get().getProperty(Property.CONTAINER));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("BooleanConstant".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("IntConstant".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("StringConstant".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("InstanceOf_9", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {

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
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expected = d.iteration() < 3 ? "ne.expression:-1,ne:-1,x:-1" : "ne:1";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("nullable instance type Expression/*@Identity*/", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name)) {
                    if ("ne".equals(fr.scope.toString())) {
                        if ("2.0.0".equals(d.statementId())) {
                            assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                                case 0 -> "<f:expression>";
                                case 1 -> "expression instanceof Negation&&null!=expression?<f:expression>:nullable instance type Expression";
                                default -> "nullable instance type Expression";
                            };
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                            assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                        String expected = d.iteration() < 3
                                ? "<vp:expression:container@Record_Negation>/*(Negation)*/"
                                : "expression/*(Negation)*/";
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() < 3 ? "expression:-1,ne.expression:-1,x:-1" : "expression:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasMerge());
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:expression:container@Record_Negation>/*(Negation)*/";
                            case 1 -> "<vp:expression:assign_to_field@Parameter_expression>/*(Negation)*/";
                            case 2 -> "<vp:expression:immutable@Interface_Expression>/*(Negation)*/";
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
                        assertCurrentValue(d, 3, "expression/*(Negation)*/.expression");
                        assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.1.0".equals(d.statementId())) {
                        assertEquals("expression", d.currentValue().toString());
                        // nothing we can do about this NOT_CONTAINER value, parameter cannot wait
                        assertDv(d, 0, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 3,
                                "expression instanceof Negation&&null!=expression?scope-ne:2.expression:expression");
                        String expectLv = "expression:0,scope-ne:2.expression:0,scope-ne:2:2";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertCurrentValue(d, 3,
                                "expression instanceof Negation&&null!=expression?scope-ne:2.expression:expression");
                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("expression".equals(d.fieldInfo().name)) {
                assertEquals("Negation", d.fieldInfo().owner.simpleName);
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expression".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("method".equals(d.methodInfo().name)) {
                assertFalse(d.allowBreakDelay());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 4, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("Negation".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("InstanceOf_10", 0, 0, new DebugConfiguration.Builder()
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
                            assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        }
                    }
                }
                if ("ne".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 4
                                ? "<vp:expression:container@Record_Negation>/*(Negation)*/"
                                : "expression/*(Negation)*/";
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

                assertDv(d.p(0), 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("Negation".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertHc(d, 1, ""); // Expression mutable, cause of 1 delayed iteration
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("expression".equals(d.fieldInfo().name)) {
                assertEquals("Negation", d.fieldInfo().owner.simpleName);
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
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
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("0.0.1.0.0".equals(d.statementId())) {
                EvaluationResult.ChangeData cd = d.findValueChangeBySubString("evaluationContext");
                String linked = switch (d.iteration()) {
                    case 0 -> "evaluationContext:0,sum:-1,this.expression:-1,v:-1";
                    case 1, 2 -> "sum:-1,this.expression:-1,v:-1";
                    case 3, 4, 5, 6, 7, 8 -> "v:-1";
                    default -> "";
                };
                assertEquals(linked, cd.linkedVariables().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "evaluationContext".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                        String expected = d.iteration() == 0 ? "<p:evaluationContext>"
                                : "nullable instance type EvaluationContext/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                    }

                    if ("0.0.1.0.0".equals(d.statementId())) {
                        // 0.0.1-E
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        String initialValue = d.iteration() == 0 ? "<p:evaluationContext>"
                                : "nullable instance type EvaluationContext/*@Identity*/";
                        assertEquals(initialValue, initial.getValue().toString());

                        String expectLv = d.iteration() < 9 ? "d:-1,sum:-1,this.expression:-1,this:-1,v:-1" : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        //  after second delay breaking
                        String expected = d.iteration() < 9 ? "<p:evaluationContext>"
                                : "nullable instance type EvaluationContext/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());

                        //  after first delay breaking
                        assertDv(d, 5, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);

                        assertDv(d, 9, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    // delays in clustering in iteration 2, otherwise we'd have CM
                    if ("0.0.1.0.5".equals(d.statementId())) {
                        String expectLv = d.iteration() < 9 ? "sum:-1,this.expression:-1,this:-1" : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }

                    if ("0.0.1".equals(d.statementId())) {
                        // delays in clustering in iteration 2, otherwise we'd have CM
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "expression".equals(fr.fieldInfo.name) && fr.scopeIsThis()) {
                    if ("0.0.1.0.4.0.0".equals(d.statementId())) {
                        String expectLv = d.iteration() < 9
                                ? "d:-1,evaluationContext:-1,ne1.expression:-1,ne1:-1,sum:-1,this:-1,v:-1,x:-1"
                                : "ne1:2,sum:1,v:2";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0.0.1.0.5".equals(d.statementId())) {
                        String expectLv = d.iteration() < 9 ? "evaluationContext:-1,sum:-1,this:-1" : "sum:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0".equals(d.statementId()) || "2".equals(d.statementId())) {
                        String expected = d.iteration() < 9 ? "<f:expression>" : NEW_EXPRESSION;
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 9 ? "<f:expression>" : NEW_EXPRESSION;
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.1.0".equals(d.statementId())) {
                        String expected = d.iteration() < 9 ? "<f:expression>" : NEW_EXPRESSION;
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() < 9 ? "<f:expression>" : NEW_EXPRESSION;
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 9 ? "<instanceOf:Sum>&&!<null-check>"
                            : "`expression/*(Sum)*/.lhs`.equals(`expression/*(Sum)*/.rhs`)&&expression instanceof Sum";
                    assertEquals(expected, d.localConditionManager().absoluteState(d.context()).toString());
                }
                if ("0.0.1.0.4.0.2".equals(d.statementId())) {
                    String expected = d.iteration() < 9 ? "<instanceOf:Negation>&&<instanceOf:Sum>&&!<null-check>"
                            : "`expression/*(Sum)*/.lhs`.equals(`expression/*(Sum)*/.rhs`)&&`expression/*(Sum)*/.lhs` instanceof Negation&&expression instanceof Sum&&null!=`expression/*(Sum)*/.lhs`";
                    assertEquals(expected, d.localConditionManager().absoluteState(d.context()).toString());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals(0, d.statementAnalysis().messageStream().count());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expression".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("method".equals(d.methodInfo().name)) {
                //  was 3, now 8
                assertDv(d, 9, DV.FALSE_DV, Property.MODIFIED_METHOD);

                // only reason for waiting would be nonNumericPartOfLhs, where it appears as argument
                // but there are still delays in clustering in 0.0.1.0.0 in iteration 2
                assertDv(d.p(0), 10, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 10, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("nonNumericPartOfLhs".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("numericPartOfLhs".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("XB".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                // dependent because Expression is mutable!
                assertDv(d.p(0), 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("expression".equals(d.fieldInfo().name)) {
                switch (d.fieldInfo().owner.simpleName) {
                    case "InstanceOf_11" -> {
                        assertEquals("new Expression(){}", d.fieldAnalysis().getValue().toString());
                        //  has to be container; was known in iteration 1 previously
                        assertDv(d, 8, MultiLevel.NOT_CONTAINER_INCONCLUSIVE, Property.CONTAINER_RESTRICTION);
                        assertDv(d, 8, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                    }
                    case "Negation" -> {
                        assertEquals("expression", d.fieldAnalysis().getValue().toString());
                        assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                        assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                    }
                    default -> fail("? " + d.fieldInfo().owner.simpleName);
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            assertFalse(d.allowBreakDelay());

            switch (d.typeInfo().simpleName) {
                case "$1" -> {
                    assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.PARTIAL_IMMUTABLE);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.PARTIAL_CONTAINER);
                    // means: we have to wait until we know the property of the enclosing type
                    String expect = d.iteration() == 0 ? "container@Class_InstanceOf_11" : "cm@Parameter_evaluationContext";
                    assertDv(d, expect, 11, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
                case "Expression", "EvaluationContext" -> {
                    assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                    assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                }
                case "Negation", "Sum" -> assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                case "InstanceOf_11" -> assertDv(d, "cm@Parameter_evaluationContext", 10, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                case "XB" -> assertDv(d, "cm@Parameter_x;mom@Parameter_x", 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                default -> fail("? " + d.typeInfo().simpleName);
            }
        };

        testClass("InstanceOf_11", 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_12() throws IOException {
        testClass("InstanceOf_12", 0, 1, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = "in instanceof Number&&null!=in?\"Number: \"+in/*(Number)*/:\"\"+in";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        testClass("InstanceOf_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("create".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "v".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type Expression/*@Identity*/", d.currentValue().toString());
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                    }
                }
                if ("unwrapped".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 0, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("ExpressionWrapper".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("InstanceOf_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_15() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setProperty".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };
        testClass("InstanceOf_15", 2, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_16() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("find".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<m:isUnaryNot>&&expression instanceof UnaryOperator&&null!=expression";
                        default -> "expression/*(UnaryOperator)*/.operator.isUnaryNot()&&expression instanceof UnaryOperator&&null!=expression";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() < 2, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 3 ? "<m:toList>"
                            : "FindInstanceOfPatterns.find(expression/*(UnaryOperator)*/.eu).stream().map(/*inline apply*/new InstanceOfPositive(iop.instanceOf,!iop.positive)).toList()";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 3, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("2".equals(d.statementId())) {
                    String delay = d.iteration() < 2 ? "initial:expression@Method_find_1-E;initial:unaryOperator.operator@Method_find_1-C"
                            : "";
                    assertEquals(delay, d.evaluationResult().causesOfDelay().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<instanceOf:InstanceOf>?<m:of>:<simplification>?<m:toList>:<simplification>?<m:toList>:<m:toList>";
                        case  2, 3 -> "expression instanceof InstanceOf&&null!=expression?<m:of>:<m:isUnaryNot>&&expression instanceof UnaryOperator&&null!=expression?<m:toList>:expression instanceof Negation&&null!=expression&&(!<m:isUnaryNot>||!(expression instanceof UnaryOperator))?<m:toList>:(nullable instance type List<Expression>).stream().flatMap(instance type $3).toList()";
                        default -> "expression instanceof InstanceOf&&null!=expression?List.of(new InstanceOfPositive(expression/*(InstanceOf)*/,true)):expression/*(UnaryOperator)*/.operator.isUnaryNot()&&expression instanceof UnaryOperator&&null!=expression?FindInstanceOfPatterns.find(expression/*(UnaryOperator)*/.eu).stream().map(/*inline apply*/new InstanceOfPositive(iop.instanceOf,!iop.positive)).toList():expression instanceof Negation&&null!=expression&&(!expression/*(UnaryOperator)*/.operator.isUnaryNot()||!(expression instanceof UnaryOperator))?FindInstanceOfPatterns.find(expression/*(Negation)*/.en).stream().map(/*inline apply*/new InstanceOfPositive(iop.instanceOf,!iop.positive)).toList():(nullable instance type List<Expression>).stream().flatMap(instance type $3).toList()";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
            if ("apply".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                String expected = d.iteration() < 4 ? "<new:InstanceOfPositive>" : "new InstanceOfPositive(iop.instanceOf,!iop.positive)";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertEquals(d.iteration() < 4, d.evaluationResult().causesOfDelay().isDelayed());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("find".equals(d.methodInfo().name)) {
                assertFalse(d.variableName().contains("(UnaryOperator)"), "Variable " + d.variableName());

                if (d.variable() instanceof ParameterInfo pi && pi.owner == d.methodInfo() && "expression".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        if (d.iteration() >= 2) {
                            assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        } else {
                            assertTrue(eval.getProperty(Property.CONTEXT_MODIFIED).isDelayed());
                        }
                        String delay = switch (d.iteration()) {
                            case 0, 1 -> "initial:expression@Method_find_1-E;initial:unaryOperator.operator@Method_find_1-C";
                            default -> "";
                        };
                        assertEquals(delay, eval.getLinkedVariables().causesOfDelay().toString());
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "instanceOf:-1";
                            default -> "instanceOf:1";
                        };
                        assertEquals(linked, eval.getLinkedVariables().toString());

                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expected = d.iteration() < 2 ? "<p:expression>" : "nullable instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expected = d.iteration() < 4 ? "<p:expression>" : "nullable instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "operator".equals(fr.fieldInfo.name)) {
                    if ("scope-unaryOperator:1".equals(fr.scope.toString())) {
                        assertTrue(d.statementId().compareTo("1") >= 0); // exists in 1, 2, and further
                        if ("1".equals(d.statementId())) {
                            assertFalse(d.variableInfoContainer().hasEvaluation());
                            assertTrue(d.variableInfoContainer().hasMerge()); // created in the merge!
                            assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                            assertEquals("scope-unaryOperator:1:2", d.variableInfo().getLinkedVariables().toString());
                        }
                        assertNotEquals("1.0.0", d.statementId()); // cannot exist here!!
                        if ("3".equals(d.statementId())) {
                            assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        }
                    } else if ("unaryOperator".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("1".equals(d.statementId())) {
                            assertTrue(d.variableInfoContainer().hasEvaluation());
                            VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                            assertEquals("expression:2,unaryOperator:2", eval.getLinkedVariables().toString());
                        }
                    }
                }
            }
            if ("apply".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId()); // inside the Lambda

                String lvs = d.variableInfo().getLinkedVariables().toString();
                if (d.variable() instanceof This thisVar && "$2".equals(thisVar.typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("", lvs);
                } else if (d.variable() instanceof This thisVar && "FindInstanceOfPatterns".equals(thisVar.typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                    assertEquals(linked, lvs);
                } else if (d.variable() instanceof FieldReference fr && "operator".equals(fr.fieldInfo.name)) {
                    assertEquals("unaryOperator", fr.scope.toString());
                    String value = switch (d.iteration()) {
                        case 0 -> "<f:unaryOperator.operator>";
                        case 1 -> "<vp:operator:independent@Field_operator>";
                        default -> "nullable instance type Operator";
                    };
                    assertEquals(value, d.currentValue().toString());
                    assertFalse(d.variableInfoContainer().hasEvaluation());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    String linked = d.iteration() == 0 ? "NOT_YET_SET" : "expression:2,unaryOperator:2";
                    assertEquals(linked, lvs);
                } else if (d.variable() instanceof FieldReference fr && "en".equals(fr.fieldInfo.name)) {
                    assertEquals("scope-negation:0", fr.scope.toString());
                    String value = switch (d.iteration()) {
                        case 0 -> "<f:scope-negation:0.en>";
                        case 1 -> "<vp:en:container@Interface_Expression>";
                        case 2 -> "<vp:en:independent@Field_en>";
                        default -> "nullable instance type Expression";
                    };
                    assertEquals(value, d.currentValue().toString());
                    assertEquals(d.iteration() > BIG, d.variableInfoContainer().hasEvaluation());
                    if (d.iteration() > BIG) { // so that there is an evaluation
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    String linked = switch (d.iteration()) {
                        case 0 -> "NOT_YET_SET";
                        default -> "expression:2,scope-negation:0:2";
                    };
                    assertEquals(linked, lvs);
                } else if (d.variable() instanceof ParameterInfo pi && "expression".equals(pi.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String linked = switch (d.iteration()) {
                        case 0 -> "NOT_YET_SET";
                        default -> "unaryOperator:1";
                    };
                    assertEquals(linked, lvs);
                } else if ("scope-negation:0".equals(d.variableName())) {
                    assertEquals(d.iteration() > BIG, d.variableInfoContainer().hasEvaluation());
                    if (d.iteration() > BIG) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    String expected = switch (d.iteration()) {
                        case 0 -> "NOT_YET_SET";
                        default -> "";//""expression:2,scope-negation:0.en:2,unaryOperator:2";
                    };
                    assertEquals(expected, lvs);
                } else if ("unaryOperator".equals(d.variableName())) {
                    assertEquals(d.iteration() > 0, d.variableInfoContainer().hasEvaluation());
                    if (d.iteration() > 0) { // so that there is an Evaluation
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    String expected = switch (d.iteration()) {
                        case 0 -> "NOT_YET_SET";
                        default -> "expression:1";
                    };
                    assertEquals(expected, lvs);
                } else if (d.variable() instanceof FieldReference fr && "positive".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    assertEquals("iop", fr.scopeVariable.simpleName());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("iop:2", lvs);

                } else if (d.variable() instanceof ParameterInfo pi && "iop".equals(pi.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                } else if (d.variable() instanceof FieldReference fr && "instanceOf".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    assertEquals("iop", fr.scopeVariable.simpleName());
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof ReturnVariable) {
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                } else fail("Have " + d.variableName());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("find".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().havePropertiesFromSubAnalysers());
                    assertEquals(6L, d.statementAnalysis().propertiesFromSubAnalysers().count());
                    DV reduced = d.statementAnalysis().propertiesFromSubAnalysers()
                            .map(e -> e.getValue().get(Property.CONTEXT_MODIFIED))
                            .reduce(DV.MIN_INT_DV, DV::max);
                    assertEquals(d.iteration() <= 3, reduced.isDelayed());
                }
            }
            if ("apply".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(d.iteration() >= 4, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("apply".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(d.iteration() >= 4, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            assertFalse(d.evaluationContext().allowBreakDelay());
            if ("en".equals(d.fieldInfo().name) && "Negation".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("en", d.fieldAnalysis().getValue().toString());
            }
            if ("eu".equals(d.fieldInfo().name) && "UnaryOperator".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("eu", d.fieldAnalysis().getValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("subElements".equals(d.methodInfo().name) && "Negation".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("instanceOf".equals(d.methodInfo().name)) {
                assertEquals("InstanceOfPositive", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 4, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
            // $3 is the lambda in statement 3 of FindInstanceOfPatterns.find
            if ("apply".equals(d.methodInfo().name) && "$3".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("find".equals(d.methodInfo().name)) {
                assertFalse(d.methodInfo().methodResolution.get().ignoreMeBecauseOfPartOfCallCycle());
            }
            if ("subElements".equals(d.methodInfo().name) && "Negation".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() < 3 ? "<m:subElements>" : "List.of(en)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("subElements".equals(d.methodInfo().name) && "UnaryOperator".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() <= 1 ? "<m:subElements>" : "List.of(eu)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("clazz".equals(d.methodInfo().name)) {
                ParameterizedType returnType = d.methodInfo().returnType();
                assertEquals("Type java.lang.Class<?>", returnType.toString());
                DV classImmutable = d.evaluationContext().getAnalyserContext().typeImmutable(returnType);
                assertEquals(MultiLevel.MUTABLE_DV, classImmutable);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("InstanceOf".equals(d.typeInfo().simpleName)) {
                if (d.iteration() > 0) {
                    assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
                }
                // without Annotated APIs, Class is mutable
                assertDv(d, 1, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
            if ("InstanceOfPositive".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
            if ("UnaryOperator".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
            if ("Negation".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
            // $3 is the lambda in statement 3 of FindInstanceOfPatterns.find
            if ("$3".equals(d.typeInfo().simpleName)) {
                assertEquals("FindInstanceOfPatterns", d.typeInfo().packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, 5, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Operator".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };
        testClass("InstanceOf_16", 0, 7, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
