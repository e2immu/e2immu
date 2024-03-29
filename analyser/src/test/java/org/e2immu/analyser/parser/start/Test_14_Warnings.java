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

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.start.testexample.Warnings_1;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_14_Warnings extends CommonTestRunner {

    public Test_14_Warnings() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {

            // ERROR: Unused variable "a"
            // ERROR: useless assignment to "a" as well
            if ("Warnings_0".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Message message = d.haveError(Message.Label.UNUSED_LOCAL_VARIABLE);
                assertNotNull(message);
                assertNull(d.haveError(Message.Label.USELESS_ASSIGNMENT));
                assertDv(d, 1, AnalysisStatus.DONE, d.result().analysisStatus());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            // ERROR: b is never read
            if ("b".equals(d.fieldInfo().name) && d.iteration() >= 1) {
                assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ));
            }
        };

        testClass("Warnings_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.start.testexample.Warnings_1";
        final String THIS = TYPE + ".this";
        final String E = Stage.EVALUATION.toString();

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            AnalysisStatus analysisStatus = d.result().analysisStatus();

            if ("method1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("true", d.state().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("t.length()>=19", d.absoluteState().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("t.length()<=18", d.absoluteState().toString());
                }
                // ERROR: t.trim() result is not used
                if ("2".equals(d.statementId())) {
                    // ERROR: unused variable "s"
                    LocationImpl location = (LocationImpl) d.haveError(Message.Label.UNUSED_LOCAL_VARIABLE).location();
                    assertEquals("org.e2immu.analyser.parser.start.testexample.Warnings_1.method1(java.lang.String)",
                            location.info.fullyQualifiedName());
                    assertNull(d.haveError(Message.Label.USELESS_ASSIGNMENT));
                    if (d.iteration() >= 2) {
                        assertNotNull(d.haveError(Message.Label.IGNORING_RESULT_OF_METHOD_CALL));
                    }
                    assertEquals("t.length()>=19", d.state().toString());
                }
            }
            if ("Warnings_1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                LocationImpl location = (LocationImpl) d.haveError(Message.Label.UNUSED_LOCAL_VARIABLE).location();
                assertEquals("org.e2immu.analyser.parser.start.testexample.Warnings_1.Warnings_1()",
                        location.info.fullyQualifiedName());
                // 1 rather than 0 because EXT_IMMUTABLE on "this"
                assertDv(d, 1, AnalysisStatus.DONE, analysisStatus);
            }
            if ("checkArray".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertDv(d, 1, AnalysisStatus.DONE, analysisStatus);
            }
            if ("checkArray2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                if (d.iteration() > 1) {
                    assertTrue(d.haveError(Message.Label.USELESS_ASSIGNMENT).detailedMessage().endsWith("integers[0]"));
                    assertEquals(AnalysisStatus.DONE, analysisStatus);
                }
            }
            if ("checkForEach".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertFalse(d.statementAnalysis().variableIsSet("loopVar")); // created in 1.0.0
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertDv(d, 0, FlowData.ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                }
            }
            if ("checkForEach".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertTrue(d.haveError(Message.Label.UNUSED_LOOP_VARIABLE).detailedMessage().endsWith("loopVar"));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("checkForEach".equals(d.methodInfo().name)) {
                if ("integers".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("{1,2,3}", d.currentValue().toString());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        // so that we know that integers.iterator() has been called
                        assertEquals("1" + E, d.variableInfo().getReadId());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                }
                if ("loopVar".equals(d.variableName())) {
                    assertFalse(d.variableInfo().isRead());
                }
            }
            if ("method1".equals(d.methodInfo().name) && "s".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertFalse(d.variableInfo().isAssigned());
                }
            }
            if ("checkArray2".equals(d.methodInfo().name)) {
                String read = d.variableInfo().getReadId();
                String assigned = d.variableInfo().getAssignmentIds().getLatestAssignment();

                if ("0".equals(d.statementId()) && "integers".equals(d.variableName())) {
                    assertEquals("0" + E, assigned); // integers=, and integers[i]=
                    assertEquals(VariableInfoContainer.NOT_YET_READ, read);
                    assertEquals("{1,2,3}", d.currentValue().toString());
                }
                if ("1".equals(d.statementId()) && "i".equals(d.variableName())) {
                    assertEquals("1" + E, assigned); // integers=, and integers[i]=
                    assertEquals(VariableInfoContainer.NOT_YET_READ, read);
                    assertEquals("0", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    if ("integers".equals(d.variableName())) {
                        assertEquals("0" + E, assigned); // integers=, NOT integers[i]=
                        assertEquals("2" + E, read);
                        assertEquals("{1,2,3}", d.currentValue().toString());
                    } else if ("i".equals(d.variableName())) {
                        assertEquals("1" + E, assigned);
                        assertEquals("2" + E, read);

                        // the standardized name is the evaluation value of expression and index, in this particular case, both constants
                    } else if ("integers[0]".equals(d.variableName())) {
                        assertEquals("2" + E, assigned);
                        assertTrue(read.compareTo(assigned) < 0);
                        assertEquals("3", d.currentValue().toString());
                    } else if (THIS.equals(d.variableName())) {
                        assertFalse(d.variableInfo().isRead());
                    } else if ("integers[i]".equals(d.variableName())) {
                        assertEquals("3", d.currentValue().toString());
                    } else fail("Variable named " + d.variableName());
                }
            }
            if ("method5".equals(d.methodInfo().name) && "a".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("5", d.currentValue().toString());
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("6", d.currentValue().toString());
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("checkArray2".equals(d.methodInfo().name)) {
                // int[] integers = {1, 2, 3};
                if ("0".equals(d.statementId())) {
                    assertEquals("{1,2,3}", d.evaluationResult().value().toString());
                    Variable integers = d.evaluationResult().changeData().keySet().stream().findFirst().orElseThrow();
                    assertEquals("integers", integers.fullyQualifiedName());
                    assertTrue(integers instanceof LocalVariableReference);
                    assertEquals("{1,2,3}", d.evaluationResult().changeData().get(integers).value().toString());
                }
            }
            if ("method1".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("t.length()<=18", d.evaluationResult().value().toString());
                assertTrue(d.evaluationResult().value().isInstanceOf(GreaterThanZero.class));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                // ERROR: method should be static
                assertNotNull(d.haveError(Message.Label.METHOD_SHOULD_BE_MARKED_STATIC));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            FieldInfo out = system.getFieldByName("out", true);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, out.fieldAnalysis.get().getProperty(EXTERNAL_NOT_NULL));
            assertEquals(MultiLevel.IGNORE_MODS_DV, out.fieldAnalysis.get().getProperty(IGNORE_MODIFICATIONS));

            TypeInfo myself = typeMap.get(Warnings_1.class);
            MethodInfo constructor = myself.findConstructor(0);
            assertEquals(MethodResolution.CallStatus.PART_OF_CONSTRUCTION, constructor.methodResolution.get().callStatus());
            MethodInfo method1 = myself.findUniqueMethod("method1", 1);
            assertEquals(MethodResolution.CallStatus.NOT_CALLED_AT_ALL, method1.methodResolution.get().callStatus());
        };

        testClass("Warnings_1", 5, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    // division by zero
    @Test
    public void test2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("testDivisionByZero".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.Label.DIVISION_BY_ZERO));
                }
                if ("2".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.DIVISION_BY_ZERO));
                }
            }
            if ("testDeadCode".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.Label.UNREACHABLE_STATEMENT));
                }
            }
        };

        testClass("Warnings_2", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // parameter should not be assigned to
    @Test
    public void test3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Warnings_3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo b && "b".equals(b.name)) {
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_IMMUTABLE)); // b is never assigned to a field; in constructor
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Warnings_3".equals(d.methodInfo().name)) {
                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
            }
        };

        testClass("Warnings_3", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // modifying an immutable set
    @Test
    public void test4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Warnings_4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertEquals("Set.copyOf(input)", d.currentValue().toString());
                    assertEquals("Type java.util.Set<java.lang.String>", d.currentValue().returnType().toString());
                    // because immutableOfHiddenContent = @ERContainer
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    // we wait because of hidden content
                    assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, null != d.haveError(Message.Label.CALLING_MODIFYING_METHOD_ON_E2IMMU));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d ->
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());

        testClass("Warnings_4", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    // method must be static
    @Test
    public void test5() throws IOException {
        final String NULLABLE_INSTANCE_TYPE_STRING = "nullable instance type String";
        final String NULLABLE_INSTANCE_TYPE_STRING_IDENTITY = "nullable instance type String/*@Identity*/";

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stream = typeMap.get(Stream.class);
            assertNotNull(stream);
            MethodInfo of = stream.typeInspection.get().methods().stream().filter(m -> m.name.equals("of")).findAny().orElseThrow();
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    of.methodAnalysis.get().getProperty(NOT_NULL_EXPRESSION));
        };
        final String T = "org.e2immu.analyser.parser.start.testexample.Warnings_5.ChildClass.t";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("methodMustNotBeStatic2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:s>" : NULLABLE_INSTANCE_TYPE_STRING;
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("apply".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "s".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(NULLABLE_INSTANCE_TYPE_STRING_IDENTITY, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:s>" : NULLABLE_INSTANCE_TYPE_STRING_IDENTITY;
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (T.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        fail();
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:t>" : NULLABLE_INSTANCE_TYPE_STRING;
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
            if ("ChildClass".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo t && "t".equals(t.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("methodMustNotBeStatic4".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                VariableInfoContainer vic = d.statementAnalysis().getVariableOrDefaultNull(T);
                assertNotNull(vic, "t should have been in the variable access report");
                assertTrue(vic.current().isRead());
            }
            if ("apply".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                VariableInfoContainer vic = d.statementAnalysis().getVariable(T);
                assertTrue(vic.current().isRead());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("methodMustNotBeStatic3".equals(d.methodInfo().name)) {
                // parameter: unused, but we see that only in the 2nd iteration
                assertDv(d.p(0), 1, DV.FALSE_DV, MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, NOT_NULL_PARAMETER);

                // method
                assertEquals("/*inline methodMustNotBeStatic3*/this", d.methodAnalysis().getSingleReturnValue().toString());
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(FLUENT));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.methodAnalysis().getProperty(NOT_NULL_EXPRESSION));
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            }
            if ("methodMustNotBeStatic4".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:methodMustNotBeStatic4>"
                        : "/*inline methodMustNotBeStatic4*/Stream.of(input).map(/*inline apply*/null==s?\"null\":s+\"something\"+t).findAny().get()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("methodMustNotBeStatic5".equals(d.methodInfo().name)) {
                String expected = "/*inline methodMustNotBeStatic5*/this";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, DV.FALSE_DV, MODIFIED_METHOD);
                assertDv(d, DV.TRUE_DV, FLUENT);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("t", d.fieldAnalysis().getValue().toString());
                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            }
            if ("s".equals(d.fieldInfo().name)) {
                assertTrue(d.fieldInfo().owner.isPrivate());
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals("s", d.fieldAnalysis().getValue().toString());
                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Warnings_5".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        testClass("Warnings_5", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test6() throws IOException {
        // none on the type, none on the methods
        testClass("Warnings_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test7() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("IsNotAContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, CONTAINER);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addToSet".equals(d.methodInfo().name)) {

                if ("MustBeContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertTrue(d.methodInfo().methodResolution.get().overrides().isEmpty());

                    assertDv(d, MultiLevel.CONTAINER_DV, CONTAINER);
                    assertDv(d, DV.TRUE_DV, MODIFIED_METHOD); // default value for @Container, void method
                    assertDv(d.p(0), DV.FALSE_DV, MODIFIED_VARIABLE);
                    assertDv(d.p(0), MultiLevel.DEPENDENT_DV, INDEPENDENT); // default for modifying methods
                }

                if ("IsNotAContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                    Set<MethodAnalysis> overrides = d.methodAnalysis()
                            .getOverrides(d.evaluationContext().getAnalyserContext());
                    assertFalse(overrides.isEmpty());

                    assertDv(d, 1, DV.FALSE_DV, MODIFIED_METHOD); // method modifies parameter, not a field!
                    // whatever happens, the set remains independent (the int added is independent)
                    assertDv(d, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
                }
            }
        };

        // one on the method
        testClass("Warnings_7", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test8() throws IOException {
        // field initializer
        testClass("Warnings_8", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test9() throws IOException {
        // overwriting previous assignment
        testClass("Warnings_9", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test10() throws IOException {
        // assigning a variable to itself
        // method2: nothing (we'll allow this, transform our way out of this bad programming)
        // method3: overwriting previous assignment
        testClass("Warnings_10", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test11() throws IOException {
        // assigning a variable to its current value
        // assigning to itself
        testClass("Warnings_11", 1, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test12() throws IOException {
        // empty method with @Modified parameter
        testClass("Warnings_12", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test13() throws IOException {
        // potential null pointer, but context properties over all methods must be false!
        testClass("Warnings_13", 0, 1, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(false)
                        .build());
    }
}
