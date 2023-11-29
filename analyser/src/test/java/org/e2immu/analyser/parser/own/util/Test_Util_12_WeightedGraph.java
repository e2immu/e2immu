
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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.*;
import org.e2immu.analyser.analyser.util.WeightedGraph;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.PackedIntMap;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.graph.analyser.PackedInt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.TreeMap;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_12_WeightedGraph extends CommonTestRunner {

    public Test_Util_12_WeightedGraph() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("compareTo".equals(d.methodInfo().name) && "VariableCause".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "cause".equals(fr.fieldInfo().name)) {
                    if (fr.scopeIsThis()) {
                        // this.cause
                        if ("0.0.0".equals(d.statementId())) {
                            String expected = d.iteration() < 2 ? "<f:cause>" : "instance type Cause";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                            assertLinked(d, it(0, 1, "c:-1,o:-1,this:-1,vc.cause:-1,vc:-1"), it(2, ""));
                        }
                        if ("0".equals(d.statementId())) {
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                            assertLinked(d,
                                    it(0, 1, "o:-1,scope-vc:0.cause:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.location:-1,this.variable:-1,this:-1"),
                                    it(2, 2, "o:-1,scope-vc:0.cause:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.location:-1,this.variable:-1"),
                                    it(3, ""));
                            assertTrue(d.variableInfoContainer().hasMerge());
                        }
                        if ("1".equals(d.statementId())) {
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                            assertLinked(d,
                                    it(0, 1, "o:-1,scope-vc:0.cause:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.location:-1,this.variable:-1,this:-1"),
                                    it(2, 2, "o:-1,scope-vc:0.cause:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.location:-1,this.variable:-1"),
                                    it(3, ""));
                        }
                    } else if ("vc".equals(fr.scope().toString())) {
                        // vc.cause
                        String expected = d.iteration() < 2 ? "<f:vc.cause>" : "instance type Cause";
                        assertEquals(expected, d.currentValue().toString());
                        if ("0.0.0".equals(d.statementId())) {
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                            assertLinked(d, it(0, 1, "c:-1,o:-1,this.cause:-1,this:-1,vc:-1"), it(2, "o:2,vc:2"));
                        }
                        if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                            fail();
                        }
                    } else if ("scope-vc:0".equals(fr.scope().toString())) {
                        if ("0".equals(d.statementId())) {
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                            assertLinked(d,
                                    it(0, 1, "o:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.cause:-1,this.location:-1,this.variable:-1,this:-1"),
                                    it(2, 2, "o:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.cause:-1,this.location:-1,this.variable:-1"),
                                    it(3, "o:2,scope-vc:0:2"));
                            assertTrue(d.variableInfoContainer().hasMerge());
                        }
                        if ("1".equals(d.statementId())) {
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                            assertLinked(d,
                                    it(0, 1, "o:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.cause:-1,this.location:-1,this.variable:-1,this:-1"),
                                    it(2, 2, "o:-1,scope-vc:0.location:-1,scope-vc:0.variable:-1,scope-vc:0:-1,this.cause:-1,this.location:-1,this.variable:-1"),
                                    it(3, "o:2,scope-vc:0:2"));
                        }
                    } else fail(fr.scope().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "0==<m:compareTo>?<return value>:<m:compareTo>"
                                : "0==cause.compareTo(o/*(VariableCause)*/.cause)?<return value>:cause.compareTo(o/*(VariableCause)*/.cause)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0.0.4".equals(d.statementId())) {
                        assertCurrentValue(d, 3, "0==cause.compareTo(o/*(VariableCause)*/.cause)?0==variable.fullyQualifiedName().compareTo(o/*(VariableCause)*/.variable.fullyQualifiedName())?location.compareTo(o/*(VariableCause)*/.location):variable.fullyQualifiedName().compareTo(o/*(VariableCause)*/.variable.fullyQualifiedName()):cause.compareTo(o/*(VariableCause)*/.cause)");                    }
                    if ("0".equals(d.statementId()) && d.iteration() >= 3) {
                        assertEquals("o instanceof VariableCause?0==cause.compareTo(scope-vc:0.cause)?0==variable.fullyQualifiedName().compareTo(scope-vc:0.variable.fullyQualifiedName())?location.compareTo(scope-vc:0.location):variable.fullyQualifiedName().compareTo(scope-vc:0.variable.fullyQualifiedName()):cause.compareTo(scope-vc:0.cause):<return value>",
                                d.currentValue().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.iteration() >= 3) {
                        assertTrue(d.variableInfo().linkedVariablesIsSet());
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                }
            }
            if ("translate".equals(d.methodInfo().name) && "VariableCause".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:variable>==<m:translateVariable>?this:<s:VariableCause>";
                            case 1 -> "<f:variable>==<m:translateVariable>?this:<new:VariableCause>";
                            default -> "this";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        // no this:0 ?? linked variables of InlineConditional
                        assertLinked(d, it(0, 1, "inspectionProvider:-1,this.cause:-1,this.location:-1,this.variable:-1,this:0,translated:-1,translationMap:-1"),
                                it(2, "this:0"));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("compareTo".equals(d.methodInfo().name) && "VariableCause".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("CM{state=null==o||!(o instanceof VariableCause);parent=CM{}}", d.conditionManagerForNextStatement().toString());
                }
            }

            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    DV guaranteedToBeReachedInMethod = d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod();
                    if (d.iteration() < 400) {
                        assertTrue(guaranteedToBeReachedInMethod.isDelayed());
                    } else {
                        assertEquals("", guaranteedToBeReachedInMethod.toString());
                    }
                }
                if ("3".equals(d.statementId())) {
                    DV guaranteedToBeReachedInMethod = d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod();
                    assertEquals(FlowDataConstants.ALWAYS, guaranteedToBeReachedInMethod);

                }
            }
            if ("statementTime".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    mustSeeIteration(d, 2);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "recursivelyComputeLinks".equals(d.enclosingMethod().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

            }
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("VariableCause".equals(d.fieldInfo().owner.simpleName)) {
                if ("cause".equals(d.fieldInfo().name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if ("location".equals(d.fieldInfo().name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if ("variable".equals(d.fieldInfo().name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
            }
        };

        List<Class<?>> classes = List.of(
                PackedIntMap.class,
                TranslationMap.class,
                InspectionProvider.class,

                // target
                WeightedGraph.class,

                // group 1: all related to DV
                AbstractDelay.class, CausesOfDelay.class, CauseOfDelay.class, DV.class, Inconclusive.class,
                NoDelay.class, NotDelayed.class, ProgressWrapper.class, AnalysisStatus.class,

                // group 2: related to Variable
                VariableCause.class, Location.class, Variable.class,

                MultiLevel.class,
                LinkedVariables.class);

        testSupportAndUtilClasses(classes,
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build());
    }

}
