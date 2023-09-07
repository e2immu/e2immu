
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_02_UpgradableBooleanMap extends CommonTestRunner {

    public Test_Util_02_UpgradableBooleanMap() {
        super(true);
    }

    @Disabled("Missing hc=true in method and parameter")
    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("of".equals(d.methodInfo().name) && n == 2) {
                if ("upgradableBooleanMap".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() < 30 ? "<new:UpgradableBooleanMap<T>>" : "new UpgradableBooleanMap<>()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() < 30 ? "<new:UpgradableBooleanMap<T>>" : "instance type UpgradableBooleanMap<T>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "t".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 30, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String linked = d.iteration() == 0 ? "upgradableBooleanMap:-1" : "upgradableBooleanMap:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 30, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                    String linked = d.iteration() < 25 ? "e:-1" : "e:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 24, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // delay caused by t in UBM.put()
                }
                if (d.variable() instanceof ParameterInfo pi && "e".equals(pi.name)) {
                    String linked = d.iteration() < 25 ? "map:-1" : "map:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 25, DV.FALSE_DV, Property.CONTEXT_MODIFIED); // delay caused by t in UBM.put()
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
                        assertDv(d, 23, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 23, MultiLevel.NOT_IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                        assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);

                        assertDv(d, 23, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 23, "instance type HashMap<T,Boolean>");
                        // link t --3-->map, not in the other direction
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        String expectedInitial = d.iteration() == 0 ? "<f:map>"
                                : d.iteration() < 23
                                ? "<vp:map:ext_not_null@Field_map;initial:this.map@Method_put_0-C>"
                                : "instance type HashMap<T,Boolean>";
                        assertEquals(expectedInitial, initial.getValue().toString());
                        assertEquals(d.iteration() >= 1, initial.getProperty(Property.IGNORE_MODIFICATIONS).isDone());

                        // EXT_IGN_MOD is already known because we're in construction:
                        assertEquals(MultiLevel.NOT_IGNORE_MODS_DV, initial.getProperty(Property.EXTERNAL_IGNORE_MODIFICATIONS));
                        assertTrue(d.context().evaluationContext().inConstruction());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        String expected = d.iteration() < 23 ? "<f:map>" : "instance type HashMap<T,Boolean>";
                        assertEquals(expected, eval.getValue().toString());
                        // link t --3-->map, not in the other direction
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());

                        // merge: blocked by delay on condition manager
                        assertDv(d, 23, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("putAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This thisVar) {
                    assert thisVar.typeInfo == d.methodInfo().typeInfo;
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() < 24 ? "other:-1" : "other:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() < 24 ? "this:-1" : "this:4";
                        // FIXME 21: this:-1, (22 only field), 23: empty, 24: this:4
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                if ("putAll".equals(d.enclosingMethod().name)) {
                    assertEquals("0", d.statementId());
                    assertEquals("$3", d.methodInfo().typeInfo.simpleName);
                    if (d.variable() instanceof ParameterInfo pi && "e".equals(pi.name)) {
                        String linked = d.iteration() < 24 ? "this:-1" : "this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if (d.variable() instanceof This thisVar && "UpgradableBooleanMap".equals(thisVar.typeInfo.simpleName)) {
                        String linked = d.iteration() == 0 ? "NOT_YET_SET"
                                : d.iteration() < 24 ? "e:-1" : "e:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
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
                assertTrue(d.methodInfo().methodInspection.get().isFactoryMethod());
                // because only directional from parameter to result, not the other way around
                assertDv(d, 30, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 31, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("of".equals(d.methodInfo().name) && n == 1) {
                String expected = d.iteration() < 30 ? "<m:of>"
                        : "/*inline of*/null==maps||maps.length<=0?new UpgradableBooleanMap<>():instance type UpgradableBooleanMap<T>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 30, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                if (d.iteration() >= 30) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("maps", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
                // IMPROVE should be HC, but code is not there yet in ComputedParameterAnalyser
                assertDv(d.p(0), 26, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("put".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().inConstruction());
                assertDv(d, 23, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 24, DV.FALSE_DV, Property.MODIFIED_VARIABLE); // caused by map, ENN
                assertDv(d.p(1), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertFalse(d.methodInfo().inConstruction());
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 23, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("putAll".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().inConstruction());
                assertEquals("accept,apply,put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
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
            if ("accept".equals(d.methodInfo().name) && "accumulator".equals(d.enclosingMethod().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("apply".equals(d.methodInfo().name) && "finisher".equals(d.enclosingMethod().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("accept".equals(d.methodInfo().name) && "putAll".equals(d.enclosingMethod().name)) {
                assertDv(d, 23, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("finisher".equals(d.methodInfo().name) && "collector".equals(d.enclosingMethod().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("instance type HashMap<T,Boolean>", d.fieldAnalysis().getValue().toString());
                assertDv(d, 22, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);

                // only link is from t --3--> map, which is not included (at this point)
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                // NOT modified outside method, because the put/putAll are part of construction!!!
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                // consequence of linking: no direct assignment, no outgoing links
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("UpgradableBooleanMap".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
                assertDv(d, 23, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, 29, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d, 31, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }

            if ("$1".equals(d.typeInfo().simpleName)) {
                assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1",
                        d.typeInfo().fullyQualifiedName);
                TypeInfo upgradable = d.typeInfo().packageNameOrEnclosingType.getRight();
                assertEquals("UpgradableBooleanMap", upgradable.simpleName);
                assertTrue(d.typeInfo().recursivelyInConstructionOrStaticWithRespectTo(InspectionProvider.DEFAULT, upgradable));
                assertDv(d, 25, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }

            if ("$2".equals(d.typeInfo().simpleName)) {
                assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1.$2",
                        d.typeInfo().fullyQualifiedName);
                TypeInfo collector = d.typeInfo().packageNameOrEnclosingType.getRight();
                TypeInfo upgradable = collector.packageNameOrEnclosingType.getRight();
                assertEquals("UpgradableBooleanMap", upgradable.simpleName);
                assertEquals("accumulator", d.typeInspection().enclosingMethod().name);
                assertTrue(d.typeInfo().recursivelyInConstructionOrStaticWithRespectTo(InspectionProvider.DEFAULT, upgradable));
                assertFalse(d.typeInfo().recursivelyInConstructionOrStaticWithRespectTo(InspectionProvider.DEFAULT, collector));
                assertDv(d, 26, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----M--M-M--M--M---M-MF------M----",
                d.delaySequence());

        testSupportAndUtilClasses(List.of(UpgradableBooleanMap.class),
                0, 0, new DebugConfiguration.Builder()
                   //     .addStatementAnalyserVisitor(statementAnalyserVisitor)
                    //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                    //    .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                    //    .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build());
    }

}
