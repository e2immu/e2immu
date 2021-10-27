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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class Test_17_Container extends CommonTestRunner {

    public Test_17_Container() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_0";
        final String S = TYPE + ".s";
        final String P = TYPE + ".setS(Set<String>,String):0:p";
        final String S0 = TYPE + ".s$0$0-E";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS".equals(d.methodInfo().name)) {
                if (P.equals(d.variableName()) && "0".equals(d.statementId())) {
                    int expect = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expect, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (P.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("0-E", d.variableInfo().getReadId());
                        assertTrue(d.variableInfoContainer().isReadInThisStatement());

                        assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("0-E", d.variableInfo().getReadId());
                        assertFalse(d.variableInfoContainer().isReadInThisStatement());

                        assertEquals("nullable? instance type Set<String>", d.currentValue().toString());
                        assertEquals(Level.DELAY, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if (S.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("p", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:s>" : "instance type Set<String>/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectNne, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                        assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                }
                if (S0.equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("p", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }

            if ("getS".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    assertEquals(S, d.variableName());
                    int expectExtImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.MUTABLE;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "p";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());

                // s is of type Set<String>, so we wait for values
                int expectExtImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectExtImm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setS".equals(d.methodInfo().name)) {
                ParameterAnalysis p = d.parameterAnalyses().get(0);
                int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnn, p.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            assertEquals(MultiLevel.MUTABLE, set.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        };
        testClass("Container_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_1";
        final String S = TYPE + ".s";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name) && "Container_1".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (S.equals(d.variableName()) && "addToS".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            // POTENTIAL NULL POINTER EXCEPTION
            if ("addToS".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addToS".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        // warning to expect: the potential null pointer exception of s
        testClass("Container_1", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_2";
        final String S = TYPE + ".s";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (S.equals(d.variableName()) && "addToS".equals(d.methodInfo().name)) {
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addToS".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    assertEquals("null!=<f:s>", d.condition().toString());
                } else {
                    assertEquals("null!=s", d.condition().toString());
                }
            }
        };

        testClass("Container_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Container_3";
        final String S = TYPE + ".s";
        final String S_0 = "s$0";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:add>" : "instance type boolean";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("set3".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        if (d.iteration() == 0) {
                            assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals("*", d.variableInfo().getLinkedVariables().toString());
                        } else {
                            assertEquals("s$0",
                                    d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                    if ("1.0.0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        if (d.iteration() == 0) {
                            assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                        } else {
                            assertEquals("s$0", d.variableInfo().getLinkedVariables().toString());
                        }
                    }

                }
                // this one tests the linking mechanism from the field into the local copy
                if (S.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());
                        if (d.iteration() == 0) {
                            assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                        } else {
                            assertEquals("", d.variableInfo().getLinkedVariables().toString());
                            assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        if (d.iteration() == 0) {
                            assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                        } else {
                            assertEquals("", d.variableInfo().getLinkedVariables().toString());
                            // set3 -> s$0 -> this.s (-> s$0)
                            assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        }
                    }
                    if ("1".equals(d.statementId())) {
                        // here we merge with the info in "0"
                        if (d.iteration() == 0) {
                            assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                        } else {
                            assertEquals("", d.variableInfo().getLinkedVariables().toString());
                            assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        }
                    }
                }
                if (S_0.equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("this.s", d.variableInfo().getLinkedVariables().toString());
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type Set<String>", d.currentValue().toString());
                    } else if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type Set<String>", d.currentValue().toString());
                    } else if ("1".equals(d.statementId())) {
                        assertEquals("nullable instance type Set<String>", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("1".equals(d.statementId()) && d.iteration() > 0) {
                assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (S.equals(d.fieldInfo().fullyQualifiedName())) {
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Container_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo param0 = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(Level.FALSE,
                    param0.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Container_4".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            final String TYPE = "org.e2immu.analyser.testexample.Container_4";
            final String S = TYPE + ".s";

            if ("crossModify".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof ParameterInfo pi && "in".equals(pi.simpleName())) {
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("m1".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p0 && "modified".equals(p0.name)) {
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("m2".equals(d.methodInfo().name) && "toModifyM2".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "modified2";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("m2".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
            if ("m1".equals(d.methodInfo().name) && S.equals(d.variableName()) && "1".equals(d.statementId())) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("m1".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
            if ("m2".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
            if ("crossModify".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                int expectEnn = MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        testClass("Container_4", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        final String CONTAINER_5 = "Container_5";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (CONTAINER_5.equals(d.methodInfo().name) &&
                    d.variable() instanceof ParameterInfo p && "coll5".equals(p.name)) {
                assertEquals("nullable instance type Collection<String>/*@Identity*/", d.currentValue().toString());
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
                if ("0".equals(d.statementId())) {
                    assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY));
                }
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if (CONTAINER_5.equals(d.methodInfo().name) && n == 0) {
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    String expectValue = "new ArrayList<>()/*0==this.size()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if (CONTAINER_5.equals(d.methodInfo().name) && n == 1) {
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // this is not correct (addAll5 modifies) but the field should hold the modified version anyway
                        String expectValue = "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
            if ("addAll5".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr
                    && "list".equals(fr.fieldInfo.name)) {
                assertEquals(d.iteration() <= 2 ? Level.DELAY : Level.TRUE,
                        d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (CONTAINER_5.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().size() == 1) {
                ParameterAnalysis coll5 = d.parameterAnalyses().get(0);
                assertEquals(d.iteration() <= 2 ? Level.DELAY : Level.FALSE,
                        coll5.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("addAll5".equals(d.methodInfo().name)) {
                ParameterAnalysis collection = d.parameterAnalyses().get(0);
                int expectModifiedParam = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModifiedParam, collection.getProperty(VariableProperty.MODIFIED_VARIABLE));
                int expectModifiedMethod = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModifiedMethod, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("list".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertEquals("instance type ArrayList<String>",
                            d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
            }
        };

        testClass(CONTAINER_5, 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stream = typeMap.get(Stream.class);
            MethodInfo sorted = stream.findUniqueMethod("sorted", 0);
            MethodAnalysis sortedAnalysis = sorted.methodAnalysis.get();
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    sorted.getAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

            // The type is an @E2Container, so @NotModified is implied; but it need not actually be present!!
            assertEquals(Level.FALSE, sortedAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("Container_6", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    @Test
    public void test_7() throws IOException {

        testClass("Container_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
