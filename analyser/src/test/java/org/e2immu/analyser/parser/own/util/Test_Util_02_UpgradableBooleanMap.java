
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_02_UpgradableBooleanMap extends CommonTestRunner {

    public Test_Util_02_UpgradableBooleanMap() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("UpgradableBooleanMap".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
                assertDv(d, 24, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, 24, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT); // FIXME wrong!
                assertDv(d, 32, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }

            if ("$1".equals(d.typeInfo().simpleName)) {
                assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1",
                        d.typeInfo().fullyQualifiedName);
                TypeInfo upgradable = d.typeInfo().packageNameOrEnclosingType.getRight();
                assertEquals("UpgradableBooleanMap", upgradable.simpleName);
                assertTrue(d.typeInfo().isStaticWithRespectTo(InspectionProvider.DEFAULT, upgradable));
                assertDv(d, 25, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE); // FIXME hc!
            }

            if ("$2".equals(d.typeInfo().simpleName)) {
                assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1.$2",
                        d.typeInfo().fullyQualifiedName);
                TypeInfo collector = d.typeInfo().packageNameOrEnclosingType.getRight();
                TypeInfo upgradable = collector.packageNameOrEnclosingType.getRight();
                assertEquals("UpgradableBooleanMap", upgradable.simpleName);
                assertEquals("accumulator", d.typeInspection().enclosingMethod().name);
                assertTrue(d.typeInfo().isStaticWithRespectTo(InspectionProvider.DEFAULT, upgradable));
                assertFalse(d.typeInfo().isStaticWithRespectTo(InspectionProvider.DEFAULT, collector));
                assertDv(d, 26, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE); // FIXME
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("of".equals(d.methodInfo().name) && n == 2) {
                if ("upgradableBooleanMap".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() < 25 ? "<new:UpgradableBooleanMap<T>>" : "new UpgradableBooleanMap<>()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() < 25 ? "<new:UpgradableBooleanMap<T>>" : "instance type UpgradableBooleanMap<T>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "t".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 25, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 25, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof This) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("accumulator".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof This thisVar) {
                    if (thisVar.typeInfo == d.methodInfo().typeInfo) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 25, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    } else if (thisVar.typeInfo == d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight()) {
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 25, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    } else fail();
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof This thisVar) {
                    if (thisVar.typeInfo == d.methodInfo().typeInfo) {
                        // $2
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    } else if (thisVar.typeInfo == d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight()) {
                        // new Collector
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    } else if (thisVar.typeInfo == d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().packageNameOrEnclosingType.getRight()) {
                        // UpgradableBooleanMap
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    } else fail("This: " + thisVar.typeInfo);
                }
                if (d.variable() instanceof ParameterInfo pi && "map".equals(pi.name)) {
                    String linked = d.iteration() < 2 ? "e:-1" : "e:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 25, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // delay caused by t in UBM.put()
                }
                if (d.variable() instanceof ParameterInfo pi && "e".equals(pi.name)) {
                    String linked = d.iteration() < 2 ? "map:-1" : "map:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED); // delay caused by t in UBM.put()
                }
            }
            if ("put".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));

                        // merge
                        assertDv(d, 24, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 24, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 24, "instance type HashMap<T,Boolean>");
                    }
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        String expectedInitial = d.iteration() == 0 ? "<f:map>"
                                : d.iteration() < 24
                                ? "<vp:map:ext_not_null@Field_map;initial:this.map@Method_put_0-C>"
                                : "instance type HashMap<T,Boolean>";
                        assertEquals(expectedInitial, initial.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        String expected = d.iteration() < 24 ? "<f:map>" : "instance type HashMap<T,Boolean>";
                        assertEquals(expected, eval.getValue().toString());

                        // merge
                        assertDv(d, 24, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(d.iteration() >= 25,
                        d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().isDone());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("of".equals(d.methodInfo().name) && n == 2) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 26, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
            }
            if ("of".equals(d.methodInfo().name) && n == 1) {
                String expected = d.iteration() < 25 ? "<m:of>"
                        : "/*inline of*/null==maps||maps.length<=0?new UpgradableBooleanMap<>():instance type UpgradableBooleanMap<T>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 25) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("maps", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("put".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().inConstruction());
                assertDv(d, 24, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 25, DV.FALSE_DV, Property.MODIFIED_VARIABLE); // caused by map, ENN
                assertDv(d.p(1), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertFalse(d.methodInfo().inConstruction());
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 24, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT); // FIXME wrong, hc!
            }
            if ("putAll".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().inConstruction());
                assertEquals("accept,apply,put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
                // ModifiedMethod must be TRUE, CM travels from accept in $4
                assertDv(d, 24, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }

            // uses putAll as a method reference
            if ("accumulator".equals(d.methodInfo().name)) {
                assertDv(d, 25, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("supplier".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("combiner".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("characteristics".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            // accumulator
            if ("accept".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }

            // finisher
            if ("apply".equals(d.methodInfo().name) && "$3".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }

            // putAll
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }

            if ("finisher".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("instance type HashMap<T,Boolean>", d.fieldAnalysis().getValue().toString());
                assertDv(d, 23, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);

                // not assigned; links to other parameters are not included in LV
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                assertDv(d, BIG, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                assertDv(d, BIG, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----M--M-M--M--M---M-MF-----M-M---",
                d.delaySequence());

        testSupportAndUtilClasses(List.of(UpgradableBooleanMap.class),
                0, 0, new DebugConfiguration.Builder()
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build());
    }

}
