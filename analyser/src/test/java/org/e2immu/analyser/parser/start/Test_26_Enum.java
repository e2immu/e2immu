
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
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.start.testexample.Enum_7;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class Test_26_Enum extends CommonTestRunner {

    public Test_26_Enum() {
        super(false);
    }

    /*
    synthetically generated:
    one field per enum constant, methods "name", "valueOf", "values", in EnumMethods

    used to take 3 iterations, now takes 7 (new allowBreak system)
     */
    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("values".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = switch (d.iteration()) {
                        case 0 -> "{<f:ONE>,<f:TWO>,<f:THREE>}";
                        case 1 -> "{<vp:ONE:container@Enum_Enum_0>,<vp:TWO:container@Enum_Enum_0>,<vp:THREE:container@Enum_Enum_0>}";
                        default -> "{Enum_0.ONE,Enum_0.TWO,Enum_0.THREE}";
                    };
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("isThree".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertFalse(d.allowBreakDelay());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("name".equals(d.methodInfo().name)) {
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("valueOf".equals(d.methodInfo().name)) {
                // contracted, all 3 through @NotNull @NotModified:
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("values".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isSynthetic());
                String expect = d.iteration() < 2 ? "<m:values>" : "/*inline values*/{Enum_0.ONE,Enum_0.TWO,Enum_0.THREE}";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 2, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("new Enum_0()", d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Enum_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("Enum_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertTrue(d.iteration() != 6);
                    String expectValue = switch (d.iteration()) {
                        case 0, 1, 2, 3, 4, 5 -> "this==<array-access:Enum_1>";
                        case 7 -> "<simplification>";
                        default -> "nullable instance type Enum_1==this";
                    };
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() < 8 ? "-1-i+<delayed array length>>=0" : "i<3";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() < 8, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
            if ("values".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                String expected = switch (d.iteration()) {
                    case 0 -> "{<f:ONE>,<f:TWO>,<f:THREE>}";
                    case 1 -> "{<vp:ONE:container@Enum_Enum_1>,<vp:TWO:container@Enum_Enum_1>,<vp:THREE:container@Enum_Enum_1>}";
                    case 2 -> "{<vp:ONE:var_missing:i@Method_posInList_0-C>,<vp:TWO:var_missing:i@Method_posInList_0-C>,<vp:THREE:var_missing:i@Method_posInList_0-C>}";
                    case 3, 4, 5, 7 -> "{<vp:ONE:srv@Method_values>,<vp:TWO:srv@Method_values>,<vp:THREE:srv@Method_values>}";
                    default -> "{Enum_1.ONE,Enum_1.TWO,Enum_1.THREE}";
                };
                assertEquals(expected, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 8, "{Enum_1.ONE,Enum_1.TWO,Enum_1.THREE}");
                    }
                }
            }
            if ("best".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "other".equals(p.name)) {
                    assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                }
            }
            if ("posInList".equals(d.methodInfo().name)) {
                if ("Enum_1.values()[i]".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);

                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("instance type Enum_1", d.currentValue().toString());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }

                    if ("0".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation()); // doesn't exist at EVAL level
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());

                        assertEquals("instance type Enum_1", d.currentValue().toString());
                        assertTrue(d.variableInfo().valueIsSet());

                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_IMMUTABLE));
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.CONTEXT_IMMUTABLE));
                        assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5 -> "this==<array-access:Enum_1>?<v:i>:<return value>";
                            case 6, 7 -> "<simplification>?<v:i>:<return value>";
                            default -> "nullable instance type Enum_1==this?i:<return value>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5 -> "<loopIsNotEmptyCondition>&&this==<array-access:Enum_1>?<oos:i>:<return value>";
                            case 6, 7 -> "<loopIsNotEmptyCondition>&&<simplification>?<oos:i>:<return value>";
                            default -> "instance type int<3&&instance type int>=0&&nullable instance type Enum_1==this?instance type int:<return value>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 8, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo math = typeMap.get(Math.class);
            MethodInfo max = math.findUniqueMethod("max", 2);
            assertEquals(DV.FALSE_DV, max.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Enum_1".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                String delay = switch (d.iteration()) {
                    case 0 -> "var_missing:i@Method_posInList_0-C";
                    case 1, 2, 3, 4, 5 -> "srv@Method_values";
                    default -> "";
                };
                assertDv(d, delay, 6, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertEquals("new Enum_1(1)", d.fieldAnalysis().getValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                String expected = d.iteration() < 8 ? "<m:values>" : "/*inline values*/{Enum_1.ONE,Enum_1.TWO,Enum_1.THREE}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertEquals("Precondition[expression=true, causes=[]]", d.methodAnalysis().getPrecondition().toString());
                String pcEventual = d.iteration() == 0 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(pcEventual, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("posInList".equals(d.methodInfo().name)) {
                assertDv(d, 8, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("best".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);

                // other is not linked to a field
                // context container will not become CONTAINER:5
                assertDv(d.p(0), MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);

                // yet, the type itself becomes container at the end of iteration 1
                assertDv(d.p(0), 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----MFT---", d.delaySequence());

        testClass("Enum_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        testClass("Enum_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.start.testexample.Enum_3";
        final String ONE = TYPE + ".ONE";
        final String TWO = TYPE + ".TWO";
        final String THREE = TYPE + ".THREE";
        final String THIS = TYPE + ".this";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("array".equals(d.variableName()) && ("0".equals(d.statementId()) || "1".equals(d.statementId()))) {
                    String expectValue = d.iteration() < 10 ? "<m:values>" : "{`Enum_3.ONE`,`Enum_3.TWO`,`Enum_3.THREE`}";
                    assertEquals(expectValue, d.currentValue().toString());

                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if ("array[i]".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() < 10 ? "<v:array[i]>" : "nullable instance type Enum_3";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2
                                ? "<v:array[i]>/*{DL array:initial@Enum_Enum_3,array[i]:assigned:1}*/"
                                : "nullable instance type Enum_3";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (THIS.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("instance type Enum_3", d.currentValue().toString());
                    }
                }
                if (THREE.equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertEquals("instance type Enum_3/*new Enum_3(3)*/", d.currentValue().toString());
                    }
                }
            }
            if ("highest".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("Enum_3.THREE:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name())) {
                assertEquals(d.iteration() == 4 || d.iteration() == 6,
                        d.allowBreakDelay());
            }
            if ("posInList".equals(d.methodInfo().name)) { // starting from statement 0, they'll all have to be there
                assertFalse(d.statementAnalysis().variableIsSet(ONE));
                assertFalse(d.statementAnalysis().variableIsSet(TWO));
                assertFalse(d.statementAnalysis().variableIsSet(THREE));

                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 10,
                            null != d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
                if ("2.0.0.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() < 10 ? "<dv:array[i]>==this" : "array[i]==this";
                    assertEquals(expectCondition, d.condition().toString());
                }

                if ("2.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() < 10 ? "<loopIsNotEmptyCondition>" : "-1+array.length>=i";
                    assertEquals(expectCondition, d.condition().toString());
                    assertEquals(d.iteration() < 10, d.condition().isDelayed());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                assertDv(d, 10, DV.TRUE_DV, Property.CONSTANT);
            }
            if ("values".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 10 ? "<m:values>" : "/*inline values*/{Enum_3.ONE,Enum_3.TWO,Enum_3.THREE}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 10) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertFalse(inlinedMethod.containsVariableFields());
                    } else fail();
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("THREE".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.CONSTANT));
                assertEquals("new Enum_3(3)", d.fieldAnalysis().getValue().toString());
            }
            if ("cnt".equals(d.fieldInfo().name)) {
                assertEquals("cnt", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d ->
                assertDv(d, 8, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----M-MFT---", d.delaySequence());

        // expect an "always true" warning on the assert statement
        testClass("Enum_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "1==<m:getCnt>" : "true";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        // two assert statements should return "true"
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("cnt".equals(d.fieldInfo().name)) {
                assertEquals("cnt", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof VariableExpression ve
                        && ve.variable() instanceof ParameterInfo);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Enum_4".equals(d.methodInfo().name)) {
                ParameterAnalysis cnt = d.parameterAnalyses().get(0);
                if (d.iteration() > 0) {
                    assertTrue(cnt.assignedToFieldIsFrozen());
                    assertEquals("{cnt=assigned:1}", cnt.getAssignedToField().toString());
                }
            }
            if ("getCnt".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:getCnt>" : "/*inline getCnt*/cnt";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("Enum_4", 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("returnTwo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    // the result of the hard-coded method call valueOf
                    String expect = d.iteration() == 0 ? "<valueOf:Enum_5>" : "instance type Enum_5";
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            // shallow analyser
            if ("valueOf".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() == 0, d.methodAnalysis().getProperty(Property.IMMUTABLE).isDelayed());
            }
        };
        testClass("Enum_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    /*
    without API, there'll be a conflict
     */
    @Test
    public void test6() throws IOException {
        testClass("Enum_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test7() throws IOException {
        assertEquals("[TWO, THREE, ONE]", Arrays.toString(Enum_7.rearranged()));

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("rearranged".equals(d.methodInfo().name)) {
                if ("v[0]".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                    }
                }
            }
        };
        testClass("Enum_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getMessage".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("0", d.statementId());
                    String expected = d.iteration() <= 1 ? "<f:Position.START.msg>" : "\"S\"";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("START".equals(d.fieldInfo().name)) {
                assertEquals("new Position(\"S\")", d.fieldAnalysis().getValue().toString());
            }
            if ("END".equals(d.fieldInfo().name)) {
                assertEquals("new Position(\"E\")", d.fieldAnalysis().getValue().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Position".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        // private field not read outside constructors: in default field analyser mode
        testClass("Enum_8", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test8_2() throws IOException {
        testClass("Enum_8", 0, 0, new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test9() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("START".equals(d.fieldInfo().name)) {
                assertEquals("new Position(\"S\")", d.fieldAnalysis().getValue().toString());
                assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }

            if ("END".equals(d.fieldInfo().name)) {
                assertEquals("new Position(\"E\")", d.fieldAnalysis().getValue().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Position".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        // private field not read outside constructors, in default field analyser mode, as in test8
        testClass("Enum_9", 1, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test9_2() throws IOException {
        testClass("Enum_9", 0, 0, new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test10() throws IOException {
        // container, nothing immutable about it!
        testClass("Enum_10", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
