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

package org.e2immu.analyser.parser.functional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_57_Lambda_AAPI extends CommonTestRunner {

    public Test_57_Lambda_AAPI() {
        super(true);
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("list".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:computeIfAbsent>" : "instance type LinkedList<V>";
                        assertEquals(expected, d.currentValue().toString());
                        // TODO this should be CONTENT_NOT_NULL
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        testClass("Lambda_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("build".equals(d.methodInfo().name)) {
                if ("immMap".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    }
                }
            }
        };
        testClass("Lambda_10", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_11() throws IOException {
        testClass("Lambda_11", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_14() throws IOException {
        testClass("Lambda_14", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_15() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof This thisVar && "Lambda_15".equals(thisVar.typeInfo.simpleName)) {
                    assertEquals("0-E", d.variableInfo().getReadId());
                }
                if (d.variable() instanceof This thisVar && "$2".equals(thisVar.typeInfo.simpleName)) {
                    assertEquals("0-E", d.variableInfo().getReadId());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("same2".equals(d.methodInfo().name) || "same2B".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Properties properties = d.statementAnalysis().propertiesFromSubAnalysers().
                            filter(v -> v.getKey() instanceof This).map(Map.Entry::getValue).findFirst().orElseThrow();
                    DV read = properties.get(Property.READ);
                    assertTrue(read.valueIsTrue());
                }
            }
        };

        testClass("Lambda_15", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_16() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("same1".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "<m:toArray>"
                        : "list.stream().filter(instance type $1).toArray(instance type $2)";
                Expression expression = d.evaluationResult().getExpression();
                assertEquals(expected, expression.toString());
            }
        };
        testClass("Lambda_16", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_17() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, ""));
                    } else if ("1".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "input:-1"), it(2, ""));
                    } else fail();
                }
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, ""));
                    } else if ("1".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "f:-1"), it(2, ""));
                    } else fail();
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("R".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("Lambda_17", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_18() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("finallyMethod".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE); // myself
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("run".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                assertTrue(d.statementAnalysis().methodLevelData().staticSideEffectsHaveBeenFound());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("run".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.STATIC_SIDE_EFFECTS);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        testClass("Lambda_18", 0, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

    /*
    Solved by a hack in ComputeLinkedVariables, writing of ENN
     */

    @Test
    public void test_19_Recursion() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursive".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "k".equals(pi.name)) {
                    VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                    assertEquals("0".equals(d.statementId()), d.variableInfoContainer().isInitial());
                    DV enn1 = vi1.getProperty(Property.EXTERNAL_NOT_NULL);
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, enn1);
                    assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof FieldReference fr && "field".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        assertTrue(d.variableInfoContainer().isInitial());
                        DV enn1 = vi1.getProperty(Property.EXTERNAL_NOT_NULL);
                        if (d.iteration() == 0) {
                            assertTrue(enn1.isDelayed());
                        } else {
                            // copied in from the field analyzer
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, enn1);
                        }
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        // must be NOT_INVOLVED because assigned!
                        assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                        assertFalse(d.variableInfoContainer().hasMerge());

                        assertLinked(d, it(0, "k:0"));
                    }
                    if ("1".equals(d.statementId())) {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        assertFalse(d.variableInfoContainer().isInitial());
                        DV enn1 = vi1.getProperty(Property.EXTERNAL_NOT_NULL);
                        // points to 0-E, which is NOT_INVOLVED because of the assignment
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn1);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof FieldReference fr && "field".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        // the recursive call
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        DV enn1 = vi1.getProperty(Property.EXTERNAL_NOT_NULL);
                        if (d.iteration() == 0) {
                            assertTrue(enn1.isDelayed()); // we'd expect this to be copied from recursive:1, NOT_INVOLVED
                        } else {
                            // copied in from the field analyzer, and FIXME here is the conflict!!!
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, enn1);
                        }

                        // an evaluation is being created in iteration 1; and being overwritten at the same time
                        assertEquals(d.iteration() > 0, d.variableInfoContainer().hasEvaluation());
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "k:0"));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "k".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        DV enn1 = vi1.getProperty(Property.EXTERNAL_NOT_NULL);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn1);

                        assertLinked(d, it0("NOT_YET_SET"), it(1, "this.field:0"));
                        assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursive".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    /*
                     NOTE: firstCallInCycle == true, so there won't be any CM/CNN info coming from the lambda.
                     See StatementAnalyzerImpl.transferFromClosureToResult.
                     */
                    assertEquals("k={read=true:1}, this={read=true:1}",
                            d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
        };

        //warning: potential null pointer on list in statement 2 of recursive
        testClass("Lambda_19Recursion", 0, 1,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_19_Unreachable1() throws IOException {
        testClass("Lambda_19Unreachable", 2, 2,
                new DebugConfiguration.Builder().build());
    }

    /*
    recursive call cycle, on of the methods becomes unreachable. See CMA.makeUnreachable()
     */
    @Test
    public void test_19_Unreachable2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursive".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().isDelayed());
                    } else fail("Unreachable, we should not get here");
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        // first statement always reachable
                        assertTrue(d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().isDone());
                    } else fail("Unreachable, we should not get here");
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertEquals(0, d.iteration(), "Unreachable; cannot be seen after iteration 0");
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("Lambda_19Unreachable2", 2, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build());
    }


    @Test
    public void test_19_Merge() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursive".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("k={read=true:1}, this={read=true:1}",
                            d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof ParameterInfo pi && "k".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "this.field:0"));
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "field".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "k:0"));
                        assertEquals(d.iteration() > 0, d.variableInfoContainer().hasEvaluation());
                        if (d.iteration() > 0) {
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                    }
                }
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("Lambda_19Merge", 1, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build());
    }
}
