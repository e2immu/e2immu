
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

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.testexample.Enum_7;
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
    one field per enum constant, methods "name", "valueOf", "values"
     */
    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "{<f:ONE>,<f:TWO>,<f:THREE>}" : "{ONE,TWO,THREE}";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isSynthetic());
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("{ONE,TWO,THREE}", d.methodAnalysis().getSingleReturnValue().toString());
                }
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("new Enum_0()", d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Enum_0".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
            }
        };

        testClass("Enum_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "this==<v:<m:values>[<v:i>]>" : "instance type Enum_1/*{L }*/==this";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<delayed array length>><v:i>" : "i$0<=2";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() == 0, d.evaluationResult().causes().isDelayed());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
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
                        assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "this==<v:<m:values>[<v:i>]>?<v:i>:<return value>"
                                : "instance type Enum_1/*{L }*/==this?1+i$0:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<delayed array length>><replace:int>?<merge:int>:<return value>"
                                : "instance type int<=2?instance type int:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo math = typeMap.get(Math.class);
            MethodInfo max = math.findUniqueMethod("max", 2);
            assertEquals(Level.FALSE_DV, max.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("best".equals(d.methodInfo().name)) {
                // FIXME implement @StaticSideEffect (current system is not stable (switches TRUE/FALSE)
            }
        };

        testClass("Enum_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        testClass("Enum_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Enum_3";
        final String ONE = TYPE + ".ONE";
        final String TWO = TYPE + ".TWO";
        final String THREE = TYPE + ".THREE";
        final String THIS = TYPE + ".this";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"posInList".equals(d.methodInfo().name)) return;
            if ("array".equals(d.variableName()) && ("0".equals(d.statementId()) || "1".equals(d.statementId()))) {
                String expectValue = d.iteration() == 0 ? "<m:values>" : "{ONE,TWO,THREE}";
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("array[i]".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "this==<new:Enum_3>?<v:array[i]>:<new:Enum_3>" : "instance type Enum_3";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:array[i]>" : "instance type Enum_3";
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
                    assertEquals("new Enum_3(3)", d.currentValue().toString());
                }
            }
            if ("i$2".equals(d.variableName())) {
                if ("2.0.0.0.0".equals(d.statementId())) {
                    assertEquals("i$2:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("i$2$2-E".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) { // starting from statement 0, they'll all have to be there
                assertEquals(d.iteration() > 0, d.statementAnalysis().variableIsSet(ONE));
                assertEquals(d.iteration() > 0, d.statementAnalysis().variableIsSet(TWO));
                assertEquals(d.iteration() > 0, d.statementAnalysis().variableIsSet(THREE));

                if ("2.0.0.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "this==<v:<v:array>[<v:i>]>" : "instance type Enum_3/*{L }*/==this";
                    assertEquals(expectCondition, d.condition().toString());
                }

                if ("2.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "<delayed array length>><v:i>" : "i$2<=2";
                    assertEquals(expectCondition, d.condition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                assertDv(d, 1, Level.TRUE_DV, Property.CONSTANT);
            }
            if ("values".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    assertEquals("{ONE,TWO,THREE}", d.methodAnalysis().getSingleReturnValue().toString());
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertFalse(inlinedMethod.containsVariableFields());
                    } else fail();
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("THREE".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.CONSTANT));
            }
            if ("cnt".equals(d.fieldInfo().name)) {
                assertEquals("cnt", d.fieldAnalysis().getValue().toString());
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals(Level.FALSE_DV, d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d ->
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);

        // expect an "always true" warning on the assert
        testClass("Enum_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
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
                    assertEquals("{cnt=1}", cnt.getAssignedToField().toString());
                }
            }
        };

        testClass("Enum_4", 0, 2, new DebugConfiguration.Builder()
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
                    assertEquals("instance type Enum_5", d.currentValue().toString());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            // shallow analyser
            if ("valueOf".equals(d.methodInfo().name)) {
                assertTrue(d.methodAnalysis().getProperty(Property.IMMUTABLE).isDelayed());
                assertEquals(0, d.iteration());
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
        // private field not read outside constructors
        testClass("Enum_8", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test9() throws IOException {
        // container, nothing immutable about it!
        testClass("Enum_9", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
