package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.testexample.Warnings_1;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class Test_04_Warnings extends CommonTestRunner {

    public Test_04_Warnings() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        final String TYPE = "Warnings_0";
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {

            // ERROR: Unused variable "a"
            // ERROR: useless assignment to "a" as well
            if ("Warnings_0".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("ERROR in M:" + TYPE + ":1: Unused local variable: a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
                assertEquals("ERROR in M:" + TYPE + ":1: Useless assignment: a", d.haveError(Message.USELESS_ASSIGNMENT));

                assertEquals(AnalysisStatus.DONE, d.result().analysisStatus);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            // ERROR: b is never read
            if ("b".equals(d.fieldInfo().name) && d.iteration() >= 1) {
                assertNotNull(d.haveError(Message.PRIVATE_FIELD_NOT_READ));
            }
        };

        testClass("Warnings_0", 3, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test1() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Warnings_1";
        final String THIS = TYPE + ".this";
        final String E = VariableInfoContainer.Level.EVALUATION.toString();

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            AnalysisStatus analysisStatus = d.result().analysisStatus;
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
                    assertEquals("ERROR in M:method1:2: Unused local variable: s", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
                    assertNull(d.haveError(Message.USELESS_ASSIGNMENT));
                    if (d.iteration() >= 2) {
                        assertNotNull(d.haveError(Message.IGNORING_RESULT_OF_METHOD_CALL));
                    }
                    assertEquals("t.length()>=19", d.state().toString());
                }
            }
            // ERROR: Unused variable "a"
            // ERROR: useless assignment to "a" as well
            if ("UnusedLocalVariableChecks".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                assertEquals("ERROR in M:UnusedLocalVariableChecks:0: Unused local variable: a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
                assertEquals("ERROR in M:UnusedLocalVariableChecks:0: Useless assignment: a", d.haveError(Message.USELESS_ASSIGNMENT));

                assertEquals(AnalysisStatus.DONE, analysisStatus);
            }
            if ("checkArray".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals(AnalysisStatus.DONE, analysisStatus);
            }
            if ("checkArray2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("ERROR in M:checkArray2:2: Useless assignment: integers[i]", d.haveError(Message.USELESS_ASSIGNMENT));

                assertEquals(AnalysisStatus.DONE, analysisStatus);
            }
            if ("checkForEach".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertFalse(d.statementAnalysis().variables.isSet("loopVar")); // created in 1.0.0
                }
                if ("1.0.0".equals(d.statementId())) {
                    FlowData.Execution expect = d.iteration() == 0 ? FlowData.Execution.DELAYED_EXECUTION :
                            FlowData.Execution.ALWAYS;
                    assertEquals(expect, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                }
            }
            if ("checkForEach".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("WARN in M:checkForEach:1: Unused loop variable: loopVar", d.haveError(Message.UNUSED_LOOP_VARIABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("checkForEach".equals(d.methodInfo().name)) {
                if ("integers".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("{1,2,3}", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        // so that we know that integers.iterator() has been called
                        assertEquals("1" + E, d.variableInfo().getReadId());

                        // in iteration 0 we don't know if integers will be assigned to
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
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
                String assigned = d.variableInfo().getAssignmentId();

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
                        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                                d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION)); // because in scope side
                    } else if ("i".equals(d.variableName())) {
                        assertEquals("1" + E, assigned);
                        assertEquals("2" + E, read);

                        // the standardized name is the evaluation value of expression and index, in this particular case, both constants
                    } else if ("integers[i]".equals(d.variableName())) {
                        assertEquals("2" + E, assigned);
                        assertTrue(read.compareTo(assigned) < 0);
                        assertEquals("3", d.currentValue().toString());
                    } else if (THIS.equals(d.variableName())) {
                        assertFalse(d.variableInfo().isRead());
                    } else fail("Variable named " + d.variableName());
                }
            }
            if ("method5".equals(d.methodInfo().name) && "a".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("5", d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("6", d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
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
                assertNotNull(d.haveError(Message.METHOD_SHOULD_BE_MARKED_STATIC));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            FieldInfo out = system.getFieldByName("out", true);
            int notNull = out.fieldAnalysis.get().getProperty(VariableProperty.EXTERNAL_NOT_NULL);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            assertEquals(Level.TRUE, out.fieldAnalysis.get().getProperty(VariableProperty.IGNORE_MODIFICATIONS));

            TypeInfo myself = typeMap.get(Warnings_1.class);
            MethodInfo constructor = myself.findConstructor(0);
            assertEquals(MethodResolution.CallStatus.PART_OF_CONSTRUCTION, constructor.methodResolution.get().partOfConstruction());
            MethodInfo method1 = myself.findUniqueMethod("method1", 1);
            assertEquals(MethodResolution.CallStatus.NOT_CALLED_AT_ALL, method1.methodResolution.get().partOfConstruction());
        };


        testClass("Warnings_1", 6, 2, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    // division by zero
    @Test
    public void test2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("testDivisionByZero".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.DIVISION_BY_ZERO));
                }
                if ("2".equals(d.statementId())) {
                    assertNull(d.haveError(Message.DIVISION_BY_ZERO));
                }
            }
            if ("testDeadCode".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                }
            }
        };

        testClass("Warnings_2", 3, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    // parameter should not be assigned to
    @Test
    public void test3() throws IOException {
        testClass("Warnings_3", 2, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    // modifying an immutable set
    @Test
    public void test4() throws IOException {
        testClass("Warnings_4", 1, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    // method must be static
    @Test
    public void test5() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stream = typeMap.get(Stream.class);
            assertNotNull(stream);
            MethodInfo of = stream.typeInspection.get().methods().stream().filter(m -> m.name.equals("of")).findAny().orElseThrow();
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    of.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };
        final String T = "org.e2immu.analyser.testexample.Warnings_5.ChildClass.t";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("methodMustNotBeStatic5".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo) {
                assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY));
            }
            if ("apply".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "s".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ?
                                "<p:s>" : "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (T.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        fail();
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:t>" : "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
            if ("ChildClass".equals(d.methodInfo().name)) {
                int enn = d.getProperty(VariableProperty.EXTERNAL_NOT_NULL);
                if (d.variable() instanceof ParameterInfo s && "s".equals(s.name)) {
                    if ("0".equals(d.statementId())) {
                        int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectEnn, enn);
                    }
                    if ("1".equals(d.statementId())) {
                        int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectEnn, enn);
                    }
                }
                if (d.variable() instanceof ParameterInfo t && "t".equals(t.name)) {
                    if ("1".equals(d.statementId())) {
                        int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectEnn, enn);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("methodMustNotBeStatic4".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                VariableInfoContainer vic = d.statementAnalysis().variables.get(T);
                assertTrue(vic.current().isRead());
            }
            if ("apply".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                VariableInfoContainer vic = d.statementAnalysis().variables.get(T);
                assertTrue(vic.current().isRead());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("methodMustNotBeStatic3".equals(d.methodInfo().name)) {
                ParameterAnalysis parameterAnalysis = d.parameterAnalyses().get(0);
                int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                assertEquals(MultiLevel.NULLABLE, parameterAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(Level.FALSE, parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));

                int expectFluent = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectFluent, d.methodAnalysis().getProperty(VariableProperty.FLUENT));
            }
            if ("methodMustNotBeStatic4".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("Stream.of(input).map(null==s?\"null\":s+\"something\"+t).findAny().get()", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("methodMustNotBeStatic5".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("this", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectFluent = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectFluent, d.methodAnalysis().getProperty(VariableProperty.FLUENT));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("t", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
            if ("s".equals(d.fieldInfo().name)) {
                assertTrue(d.fieldInfo().owner.isPrivate());
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals("s", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Warnings_5".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getImplicitlyImmutableDataTypes().isEmpty());
            }
        };

        testClass("Warnings_5", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
