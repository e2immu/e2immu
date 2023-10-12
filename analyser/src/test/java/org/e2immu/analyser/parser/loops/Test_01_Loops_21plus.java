
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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_21plus extends CommonTestRunner {


    public Test_01_Loops_21plus() {
        super(true);
    }

    private final StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method".equals(d.methodInfo().name)) {
            if ("array".equals(d.variableName())) {
                assertEquals("Type String[][]", d.currentValue().returnType().toString());
            }
            if ("inner".equals(d.variableName())) {
                assertTrue(d.statementId().startsWith("2"), "Known in statement " + d.statementId());
                if (d.statementId().startsWith("2.0.1")) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outsideLoop) {
                        assertEquals("2.0.1", outsideLoop.statementIndex());
                    } else fail();
                } else {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable normalLocalVariable) {
                        assertEquals("2", normalLocalVariable.parentBlockIndex);
                    } else fail("In statement " + d.statementId());
                }
            }
            if ("outer".equals(d.variableName())) {
                if (d.statementId().startsWith("2.0.1")) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outsideLoop) {
                        assertEquals("2.0.1", outsideLoop.statementIndex(), "in " + d.statementId());
                    } else fail();
                } else if (d.statementId().startsWith("2")) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outsideLoop) {
                        assertEquals("2", outsideLoop.statementIndex(), "in " + d.statementId());
                    } else fail();
                } else {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable normalLocalVariable) {
                        assertEquals("", normalLocalVariable.parentBlockIndex);
                    } else fail("In statement " + d.statementId());
                }
            }
            if ("outerMod".equals(d.variableName())) {
                assertTrue(d.statementId().startsWith("2.0.1"), "Known in statement " + d.statementId());
            }
        }
    };

    @Test
    public void test_21() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(4 == d.iteration(), d.allowBreakDelay());
                }
                if ("array[i]".equals(d.variableName())) {
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String expected = d.iteration() <= 3
                                ? "<array-access:String[]>"
                                : "instance type String[]";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() < 4
                                ? "array:-1,array[i][j]:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1"
                                : "array:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
                    }
                    if (d.statementId().startsWith("2.0.1")) {
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    } else fail();
                }
                if ("array[i][j]".equals(d.variableName())) {
                    assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    if ("2".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().isInitial());
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertTrue(d.variableInfoContainer().hasMerge());

                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String prevValue = d.iteration() == 0 ? "<array-access:String>" : "nullable instance type String";
                        assertEquals(prevValue, prev.getValue().toString());

                        String mergeValue = switch (d.iteration()) {
                            case 0 ->
                                    "<loopIsNotEmptyCondition>&&<loopIsNotEmptyCondition>?<m:charAt>+\"->\"+<m:charAt>:<array-access:String>";
                            case 1, 2, 3 ->
                                    "-1-<oos:i>+n>0&&-1-<oos:j>+m>=0?<m:charAt>+\"->\"+<m:charAt>:nullable instance type String";
                            default ->
                                    "-1-(instance type int)+m>=0&&-1!(-1-(instance type int)+m>=0?instance type int:instance type int)+n>0?outer$2.0.1.charAt((-1-(instance type int)+m>=0?instance type int:instance type int)%outer$2.0.1.length())+\"->\"+(-1-(instance type int)+m>=0?instance type String:\"xzy\").charAt(instance type int%(-1-(instance type int)+m>=0?instance type String:\"xzy\").length()):nullable instance type String";
                        };
                        assertEquals(mergeValue, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().isInitial());
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertTrue(d.variableInfoContainer().hasMerge());

                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String prevValue = d.iteration() == 0 ? "<array-access:String>" : "nullable instance type String";
                        assertEquals(prevValue, prev.getValue().toString());
                        String mergeValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<m:charAt>+\"->\"+<m:charAt>:<array-access:String>";
                            case 1, 2, 3 ->
                                    "-1-<oos:j>+m>=0?<m:charAt>+\"->\"+<m:charAt>:nullable instance type String";
                            default ->
                                    "-1-(instance type int)+m>=0?outer$2.0.1.charAt(i$2.0.1%outer$2.0.1.length())+\"->\"+inner$2.0.1.charAt(instance type int%inner$2.0.1.length()):nullable instance type String";
                        };
                        assertEquals(mergeValue, d.currentValue().toString());
                    }
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String expected = d.iteration() <= 3
                                ? "<m:charAt>+\"->\"+<m:charAt>"
                                : "outer$2.0.1.charAt(i$2.0.1%outer$2.0.1.length())+\"->\"+inner$2.0.1.charAt(j%inner$2.0.1.length())";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() < 4
                                ? "array:-1,array[i]:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1"
                                : "array:3,array[i]:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("array".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String expected = d.iteration() < 4 ? "<vl:array>" : "instance type String[][]";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() < 4
                                ? "array[i]:-1,array[i][j]:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1"
                                : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    }
                    if ("2.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vl:array>";
                            case 1, 2, 3 -> "-1-<oos:j>+m>=0?<vl:array>:instance type String[][]";
                            default -> "-1-(instance type int)+m>=0?instance type String[][]:instance type String[][]";
                        };
                        assertEquals(expected, d.currentValue().toString());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectedE = d.iteration() == 0 ? "<vl:array>" : "instance type String[][]";
                        assertEquals(expectedE, eval.getValue().toString());
                        assertEquals("", eval.getLinkedVariables().toString());

                        String linked = d.iteration() < 4 ? "array[i]:-1,array[i][j]:-1,i:-1,inner:-1,outer:-1"
                                : "array[i]:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("i".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasMerge());
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, CONTEXT_CONTAINER);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, CONTAINER_RESTRICTION);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("2".equals(d.statementId())) {
                String range = d.iteration() == 0 ? "DELAYED RANGE" : "NO RANGE";
                assertEquals(range, d.statementAnalysis().rangeData().getRange().toString());
            }
            if ("2.0.1".equals(d.statementId())) {
                String range = d.iteration() == 0 ? "DELAYED RANGE" : "NO RANGE";
                assertEquals(range, d.statementAnalysis().rangeData().getRange().toString());

                assertEquals(d.iteration() >= 5, d.statusesAsMap().values().stream().noneMatch(AnalysisStatus::isDelayed));
            }
            if (d.statementId().startsWith("2.0.1.")) {
                assertEquals(d.iteration() >= 5, d.statusesAsMap().values().stream().noneMatch(AnalysisStatus::isDelayed));
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S-", d.delaySequence());

        testClass("Loops_21", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_21_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("array".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<vl:array>:new String[n][m]";
                            case 1, 2, 3 -> "-1-<oos:i>+n>0?<vl:array>:new String[n][m]";
                            default ->
                                    "-1!(-1-(instance type int)+m>=0?instance type int:instance type int)+n>0?-1-(instance type int)+m>=0?instance type String[][]:instance type String[][]:new String[n][m]";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(DV.FALSE_DV, eval.getProperty(CONTEXT_MODIFIED));
                    }
                    if ("2.0.1".equals(d.statementId())) {
                        DV override = d.variableInfoContainer().propertyOverrides().getOrDefaultNull(CONTEXT_MODIFIED);
                        assertEquals(d.iteration() >= 4, DV.TRUE_DV.equals(override));
                    }
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        DV override = d.variableInfoContainer().propertyOverrides().getOrDefaultNull(CONTEXT_MODIFIED);
                        assertEquals(d.iteration() >= 4, DV.TRUE_DV.equals(override));

                        assertLinked(d, it(0, 3, "array[i]:-1,array[i][j]:-1"), it(4, ""));
                        String expected = d.iteration() < 4 ? "<vl:array>" : "instance type String[][]";
                        assertEquals(expected, d.currentValue().toString());

                        // TRUE instead of false, at the moment, because we don't have a proper guessing
                        // mechanism yet (based on a direct CM, CM on the statically-assigned clustering)
                        assertDv(d, 4, DV.TRUE_DV, CONTEXT_MODIFIED);
                    }
                }
                if ("array[i]".equals(d.variableName())) {
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                        String value = d.iteration() < 4 ? "<array-access:String[]>" : "instance type String[]";
                        assertEquals(value, d.currentValue().toString());
                        assertLinked(d, it(0, 3, "array:-1,array[i][j]:-1"),
                                it(4, "array:3"));
                    }
                }
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S-", d.delaySequence());

        // potential null pointer array[i][j].length()
        testClass("Loops_21_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_21_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variableName().startsWith("array")) {
                    if ("array".equals(d.variableName())) {
                        if ("1".equals(d.statementId())) {
                            assertEquals("new String[n][]", d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        }
                        if ("2.0.0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<vl:array>" : "instance type String[][]";
                            assertEquals(expected, d.currentValue().toString());
                            // IMPROVE even though after the assignment, it has become content not null
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        }
                    } else if ("array[i]".equals(d.variableName())) {
                        if ("2.0.0".equals(d.statementId())) {
                            assertEquals("new String[m]", d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        }
                        if ("2.0.2.0.2".equals(d.statementId())) {
                            String expected = d.iteration() < 4 ? "<vl:array[i]>" : "instance type String[]";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        }
                    } else if (!"array[i][j]".equals(d.variableName())) {
                        fail("?: " + d.variableName());
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S-", d.delaySequence());
        testClass("Loops_21_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_21_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variableName().startsWith("array")) {
                    if ("array".equals(d.variableName())) {
                        if ("1".equals(d.statementId())) {
                            assertEquals("new String[n][]", d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        }
                        if ("2.0.0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<vl:array>" : "instance type String[][]";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        }
                    } else if ("array[i]".equals(d.variableName())) {
                        if ("2.0.1.0.2".equals(d.statementId())) {
                            String expected = d.iteration() < 4 ? "<array-access:String[]>" : "nullable instance type String[]";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 4, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                        }
                    } else {
                        assertEquals("array[i][j]", d.variableName());
                    }
                }
            }
        };
        testClass("Loops_21_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // identical, but for a statement at 2.0.2
    @Test
    public void test_22() throws IOException {
        testClass("Loops_22", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // similar topic
    // note that the CNN on "x" does not travel to the field, given the analyser configuration. See _23_1.
    @Test
    public void test_23() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("all".equals(d.variableName())) {
                    if ("2".equals(d.statementId()) || "0".equals(d.statementId()) || "5".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable normalLocalVariable) {
                            assertEquals("", normalLocalVariable.parentBlockIndex);
                        } else fail("In statement " + d.statementId());
                    }
                }
            }
        };
        testClass("Loops_23", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // the not-null on x travels to xes as ContentNotNull, and on to the parameter
    @Test
    public void test_23_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "xes".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 5, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                }
                if ("x".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() <= 4 ? "<vl:x>" : "instance type X";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 5, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        assertDv(d, 5, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);

                        assertLinked(d,
                                it(0, 4, "all:-1,this.xes:-1,this:-1"),
                                it(5, "this.xes:3,this:3"));
                        assertDv(d, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);
                        assertDv(d, 5, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("xes".equals(d.fieldInfo().name)) {
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(),
                        it(0, 0, "System.out:-1,all:-1,xesIn:-1"),
                        it(1, 4, "all:-1,xesIn:-1"),
                        it(5, "xesIn:0"));
                assertEquals("xesIn", d.fieldAnalysis().getValue().toString());
                assertDv(d, 4, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Loops_23".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "{}" : "{xes=assigned:1}";
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(expected, p0.getAssignedToField().toString());
                assertEquals(d.iteration() >= 6, p0.assignedToFieldDelays().isDone());
                assertDv(d.p(0), 5, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d.p(0), 5, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };
        testClass("Loops_23", 0, 0, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    // Same topic as _17: not null moving from variable to source of loop
    @Test
    public void test_24() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(CONTEXT_NOT_NULL));

                        // merge:
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        // as the sourceOfLoop of entry
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(d.iteration() <= 2, eval.getProperty(CONTEXT_NOT_NULL).isDelayed());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        testClass("Loops_24", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    @Test
    public void test_24_1() throws IOException {
        testClass("Loops_24_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    @Test
    public void test_24_2() throws IOException {
        testClass("Loops_24_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

}
