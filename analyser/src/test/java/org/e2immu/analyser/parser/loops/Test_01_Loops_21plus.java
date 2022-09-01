
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
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.CONTEXT_MODIFIED;
import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_21plus extends CommonTestRunner {


    public Test_01_Loops_21plus() {
        super(true);
    }

    private final StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method".equals(d.methodInfo().name)) {
            if ("array".equals(d.variableName())) {
                assertEquals("Type java.lang.String[][]", d.currentValue().returnType().toString());
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
                    assertEquals(4 == d.iteration(), d.context().evaluationContext().allowBreakDelay());
                }
                if ("array[i]".equals(d.variableName())) {
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String expected = d.iteration() <= 3
                                ? "<array-access:String[]>"
                                : "instance type String[]";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "array:-1,av-32:17:-1,av-32:17[j]:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1";
                            case 1, 2, 3 -> "array:-1,av-32:17:-1,i:-1";
                            default -> "array:2,av-32:17:1";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.TRUE_DV, CONTEXT_MODIFIED);
                    } else if (!d.statementId().startsWith("2.0.1")) fail("In " + d.statementId());
                }
                if ("array".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String expected = d.iteration() <= 3 ? "<vl:array>" : "instance type String[][]";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "array[i]:-1,av-32:17:-1,av-32:17[j]:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1";
                            case 1, 2, 3 -> "array[i]:-1,av-32:17:-1,i:-1";
                            default -> "array[i]:2,av-32:17:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String linkDelays = switch (d.iteration()) {
                            case 0 -> "initial:array@Method_method_2.0.1.0.2-C;initial:i@Method_method_2.0.1-C;initial:inner@Method_method_2.0.1.0.1-C;initial:j@Method_method_2.0.1-E;initial:outer@Method_method_2.0.1.0.0-C";
                            case 1, 2, 3 -> "wait_for_modification:array@Method_method_2.0.1-E";
                            default -> "";
                        };
                        assertEquals(linkDelays, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        assertDv(d, 4, DV.TRUE_DV, CONTEXT_MODIFIED);
                    }
                    if ("2.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 3 ? "<vl:array>" : "instance type String[][]";
                        assertEquals(expected, d.currentValue().toString());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().toString());
                        assertEquals("", eval.getLinkedVariables().toString());

                        String linked = switch (d.iteration()) {
                            case 0 -> "array[i]:-1,av-32:17:-1,i:-1,inner:-1,outer:-1";
                            case 1, 2, 3 -> "array[i]:-1,av-32:17:-1,i:-1";
                            default -> "array[i]:2,av-32:17:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.TRUE_DV, CONTEXT_MODIFIED);
                    }
                }
                if ("av-32:17[j]".equals(d.variableName())) {
                    assertTrue(d.variable() instanceof DependentVariable);
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable);
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "array:-1,array[i]:-1,av-32:17:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertEquals("2.0.1.0.2-E", d.variableInfo().getAssignmentIds().toString());
                    }
                }
                if ("i".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasMerge());
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
                if ("av-32:17".equals(d.variableName())) {
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "array:-1,array[i]:-1,av-32:17[j]:-1,i:-1,inner:-1,innerMod:-1,j:-1,outer:-1,outerMod:-1";
                            case 1, 2, 3 -> "array:-1,array[i]:-1,i:-1";
                            default -> "array:2,array[i]:1";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, CONTEXT_MODIFIED);
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

                String expected = switch (d.iteration()) {
                    case 0 -> "var_missing:j@Method_method_2.0.1-C";
                    case 1, 2, 3 -> "wait_for_modification:array@Method_method_2.0.1-E";
                    default -> "";
                };
                assertEquals(expected, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
                assertEquals(d.iteration() >= 5, d.statusesAsMap().values().stream().noneMatch(AnalysisStatus::isDelayed));
            }
            if (d.statementId().startsWith("2.0.1.")) {
                assertEquals(d.iteration() >= 5, d.statusesAsMap().values().stream().noneMatch(AnalysisStatus::isDelayed));
            }
        };
        testClass("Loops_21", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_21_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("array".equals(d.variableName())) {
                    if ("2.0.1".equals(d.statementId())) {
                        DV override = d.variableInfoContainer().propertyOverrides().getOrDefaultNull(CONTEXT_MODIFIED);
                        assertEquals(d.iteration() >= 4, DV.TRUE_DV.equals(override));
                    }
                    if ("2.0.1.0.2".equals(d.statementId())) {
                        DV override = d.variableInfoContainer().propertyOverrides().getOrDefaultNull(CONTEXT_MODIFIED);
                        assertEquals(d.iteration() >= 4, DV.TRUE_DV.equals(override));

                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() <= 3 ? "<vl:array>" : "instance type String[][]";
                        assertEquals(expected, d.currentValue().toString());

                        // TRUE instead of false, at the moment, because we don't have a proper guessing
                        // mechanism yet (based on a direct CM, CM on the statically-assigned clustering)
                        assertDv(d, 4, DV.TRUE_DV, CONTEXT_MODIFIED);
                    }
                }
            }
        };
        // potential null pointer array[i][j].length()
        testClass("Loops_21_1", 0, 1, new DebugConfiguration.Builder()
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

                        String linked = d.iteration() <= 4 ? "all:-1,this.xes:-1" : "this.xes:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);
                        assertDv(d, 5, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("xes".equals(d.fieldInfo().name)) {
                assertEquals("xesIn:0", d.fieldAnalysis().getLinkedVariables().toString());
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
