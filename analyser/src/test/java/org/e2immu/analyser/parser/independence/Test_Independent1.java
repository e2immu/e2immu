
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

package org.e2immu.analyser.parser.independence;


import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Independent1 extends CommonTestRunner {

    public Test_Independent1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                    String expected = d.iteration() == 0 ? "<p:consumer>"
                            : "nullable instance type Consumer<T>/*@Identity*//*@IgnoreMods*/";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("consumer:3", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                // implicitly present:
                assertDv(d.p(0), MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE); // because of @IgnoreModifications
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("Independent1_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Independent1_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("1".equals(d.statementId())) {
                        assertEquals("ts:4", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "ts".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("this.set:4", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        testClass("Independent1_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_2".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "T");
            }
        };
        testClass("Independent1_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_2_1".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
            }
        };
        testClass("Independent1_2_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_2_2".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getHiddenContentTypes().toString());
            }
        };
        testClass("Independent1_2_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("Independent1_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3_1() throws IOException {
        testClass("Independent1_3_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("t".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        // StatementAnalysisImpl.evaluationOfForEachVariable generates the this.ts:-1,3
                        // the "accept" call generates consumer:-1,3
                        String linked = d.iteration() < 2 ? "consumer:-1,this.ts:-1" : "consumer:3,this.ts:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "ts".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() < 2 ? "consumer:-1" : "consumer:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        testClass("Independent1_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "t:-1,this.ts:-1,tt:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("t".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "consumer:-1,this.ts:-1,tt:-1";
                            case 1 -> "this.ts:-1";
                            default -> "this.ts:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        testClass("Independent1_4_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // IMPORTANT: as of August 22, transparency of a type is ignored
    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 4 ? "<p:consumer>"
                                : "nullable instance type Consumer<One<Integer>>/*@Identity*//*@IgnoreMods*/";
                        assertEquals(expected,
                                d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "one:-1,this.ones:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("one".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "consumer:-1,this.ones:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "generator.get()");
                }
            }
            if ("ImmutableArrayOfTransparentOnes".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "generator".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        String linked = d.iteration() < 2 ? "this.ones:-1" : "this.ones:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("first".equals(d.methodInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("get".equals(d.methodInfo().name)) {
                assertEquals("ImmutableArrayOfTransparentOnes", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                String expected = d.iteration() <= 1 ? "<m:apply>" : "/*inline apply*/generator.get()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                List<Variable> vars = d.methodAnalysis().getSingleReturnValue().variables(true);
                assertEquals("[org.e2immu.analyser.parser.independence.testexample.Independent1_5.ImmutableArrayOfTransparentOnes.ImmutableArrayOfTransparentOnes(org.e2immu.analyser.parser.independence.testexample.Independent1_5.One<java.lang.Integer>[],java.util.function.Supplier<org.e2immu.analyser.parser.independence.testexample.Independent1_5.One<java.lang.Integer>>):1:generator]", vars.toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ImmutableArrayOfTransparentOnes".equals(d.typeInfo().simpleName)) {
                assertHc(d, 2, "");
            }
        };
        testClass("Independent1_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() < 2 ? "one:-1,this.ones:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() < 3 ? "this.ones:-1" : "this.ones:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("one".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        VariableInfo eval0 = d.variableInfoContainer().getPreviousOrInitial();
                        // set in StatementAnalysisImpl.evaluationOfForEachVariable
                        String linkedOE = d.iteration() < 3 ? "this.ones:-1" : "this.ones:3";
                        assertEquals(linkedOE, eval0.getLinkedVariables().toString());

                        // consumer comes in via the method call, this.ones via statement 0, evaluation
                        String linked = d.iteration() < 3 ? "consumer:-1,this.ones:-1" : "consumer:3,this.ones:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("ImmutableArrayOfOnes".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "generator".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        String linked = d.iteration() < 2 ? "this.ones:-1" : "this.ones:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 3
                            ? "index:-1,ones[index]:-1,this.ones:-1"
                            : "ones[index]:1,this.ones:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                assertEquals("ImmutableArrayOfOnes", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 3, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, 3, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                String expected = d.iteration() <= 1 ? "<m:apply>" : "/*inline apply*/generator.get()";
                assertEquals(expected,
                        d.methodAnalysis().getSingleReturnValue().toString());
                List<Variable> vars = d.methodAnalysis().getSingleReturnValue().variables(true);
                assertEquals("[org.e2immu.analyser.parser.independence.testexample.Independent1_6.ImmutableArrayOfOnes.ImmutableArrayOfOnes(int,java.util.function.Supplier<org.e2immu.analyser.parser.independence.testexample.Independent1_6.One<T>>):1:generator]", vars.toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("One".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("ImmutableArrayOfOnes".equals(d.typeInfo().simpleName)) {
                assertHc(d, 1, "T");
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ones".equals(d.fieldInfo().name)) {
                String linked = d.iteration() == 0 ? "consumer:-1,generator:-1" : "";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("t:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };
        testClass("Independent1_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_6_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("One".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "T");
            }
            if ("ImmutableArrayOfOnes".equals(d.typeInfo().simpleName)) {
                assertHc(d, 2, "");
            }
        };
        testClass("Independent1_6_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Data".equals(d.typeInfo().simpleName)) {
                // new One[size] makes One explicit
                assertHc(d, 2, "X");
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            assertFalse(d.allowBreakDelay());
            if ("methodInfo".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isSynthetic());
                assertDv(d, 4, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                //    assertDv(d, 3, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };
        testClass("Independent1_7", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("list".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        testClass("Independent1_8", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("putAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    String linked = d.iteration() < 14 ? "this:-1" : "this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 14 ? "this.map:-1" : "this.map:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("entries".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 14 ? "this:-1" : "this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertCurrentValue(d, 14,
                            "`map`.entrySet().stream().map(/*inline apply*/new ImmutableEntry<>(e.getKey(),e.getValue())).toList()");
                    if (d.iteration() >= 14) {
                        // NOTE: there is no ImmutableEntry here!
                        assertEquals("Type java.util.List<java.util.Map.Entry<T,java.lang.Boolean>>",
                                d.variableInfo().getValue().returnType().toString());
                    }
                    assertDv(d, 14, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                }
            }
            if ("of".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() < 15 ? "maps:-1,result:0" : "maps:4,result:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 14 ? "<m:stream>"
                        : "/*inline stream*/map.entrySet().stream().map(/*inline apply*/new ImmutableEntry<>(e.getKey(),e.getValue()))";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 14, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                if (d.iteration() >= 14) {
                    assertEquals("Type java.util.stream.Stream<org.e2immu.analyser.parser.independence.testexample.Independent1_9.ImmutableEntry<T>>",
                            d.methodAnalysis().getSingleReturnValue().returnType().toString());
                }
            }
            if ("entries".equals(d.methodInfo().name)) {
                assertDv(d, 14, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, 14, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("of".equals(d.methodInfo().name)) {
                /*
                 IMPROVE this should be _HC, but there is no code yet in ComputedParameterAnalyser to look at the method
                 return value rather than at the fields.  A complication will be that variables do not link to the return
                 value currently.
                 */
                assertDv(d.p(0), 16, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_9".equals(d.typeInfo().simpleName)) {
                assertDv(d, 14, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };
        testClass("Independent1_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    /*
    used to test linked variables of method expansions
     */
    @Test
    public void test_9_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("keys".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 11 ? "this:-1" : "this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("of".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "maps".equals(pi.name)) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 12,
                                "nullable instance type Independent1_9_1<T>[]/*@Identity*/");
                        String linked = d.iteration() < 12 ? "map.map:-1,map:-1,result:-1"
                                : "map.map:4,map:4,result:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 12, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("of".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 12 ? "<m:of>"
                        : "/*inline of*/null==maps||maps.length<=0?new Independent1_9_1<>():instance type Independent1_9_1<T>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d, 12, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d.p(0), 13, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 13, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("stream".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 11 ? "<m:stream>" : "/*inline stream*/map.keySet().stream()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 11, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d, 11, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("keys".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 11 ? "<m:keys>" : "/*inline keys*/`map`.keySet().stream().toList()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 11, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d, 11, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("put".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 12, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_9_1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 11, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d, 11, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertDv(d, 10, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----M-M--MF---", d.delaySequence());

        testClass("Independent1_9_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_10() throws IOException {
        // TODO NOT YET IMPLEMENTED
        testClass("Independent1_10", 4, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_11() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("addAllLambda".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    String linked = d.iteration() < 2 ? "other.list:-1,this.list:-1" : "other.list:4,this.list:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("addAllCC".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    String linked = d.iteration() < 2 ? "other.list:-1,this.list:-1" : "other.list:4,this.list:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("addAllMR".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    String linked = d.iteration() < 2 ? "other.list:-1,this.list:-1" : "other.list:4,this.list:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("addAllMR2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    String linked = d.iteration() < 2 ? "other.list:-1,this.list:-1" : "other.list:4,this.list:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        testClass("Independent1_11", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


}
