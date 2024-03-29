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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class Test_17_Container extends CommonTestRunner {

    public Test_17_Container() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.start.testexample.Container_0";
        final String S = TYPE + ".s";
        final String S0 = TYPE + ".s$0$0-E";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setS".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    // not delayed
                    assertTrue(d.evaluationResult().causesOfDelay().isDone());
                }
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:add>" : "instance type boolean";
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "p".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("0-E", d.variableInfo().getReadId());
                        assertTrue(d.variableInfoContainer().isReadInThisStatement());

                        assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals("nullable instance type Set<String>/*@Identity*/", d.currentValue().toString());

                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("0-E", d.variableInfo().getReadId());
                        assertFalse(d.variableInfoContainer().isReadInThisStatement());

                        String expected = d.iteration() == 0 ? "<p:p>"
                                : "nullable instance type Set<String>/*@Identity*//*this.contains(toAdd)&&this.size()>=1*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                } else if (d.variable() instanceof ParameterInfo p && "toAdd".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:toAdd>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                    }
                } else if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    assertEquals(S, d.variableName());

                    if ("0".equals(d.statementId())) {
                        assertEquals("p", d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                        assertEquals("p:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:s>" : "p";// "nullable instance type Set<String>/*@Identity*//*this.contains(toAdd)&&this.size()>=1*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                } else if (S0.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        fail("Local copy of variable field should not exist here");
                    }
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.iteration() > 0, "Local copy cannot exist in iteration 0");
                        assertEquals("p", d.currentValue().toString());

                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                } else if (d.variable() instanceof This) {
                    assertEquals(TYPE + ".this", d.variableName());
                } else fail("Variable: " + d.variableName());
            }

            if ("getS".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    assertEquals(S, d.variableName());
                    assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("p:0", d.fieldAnalysis().getLinkedVariables().toString());

                final String DELAYED = "constructor-to-instance@Method_setS_1-E;initial:this.s@Method_setS_1-C;values:this.s@Field_s";

                assertDv(d, DELAYED, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, DELAYED, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, DELAYED, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setS".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d.p(0), 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            assertEquals(MultiLevel.MUTABLE_DV, set.typeAnalysis.get().getProperty(Property.IMMUTABLE));
        };

        //WARN in Method org.e2immu.analyser.parser.start.testexample.Container_0.setS(java.util.Set<java.lang.String>,java.lang.String) (line 36, pos 9): Potential null pointer exception: Variable: s
        testClass("Container_0", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.start.testexample.Container_1";
        final String S = TYPE + ".s";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name) && "Container_1".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (S.equals(d.variableName()) && "addToS".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            // POTENTIAL NULL POINTER EXCEPTION
            if ("addToS".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.iteration() > 1) {
                    assertNotNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
                mustSeeIteration(d, 2);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addToS".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
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
        final String TYPE = "org.e2immu.analyser.parser.start.testexample.Container_2";
        final String S = TYPE + ".s";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (S.equals(d.variableName()) && "addToS".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
                if ("0".equals(d.statementId())) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addToS".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    assertEquals("<null-check>", d.condition().toString());
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
        final String TYPE = "org.e2immu.analyser.parser.start.testexample.Container_3";
        final String S = TYPE + ".s";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:add>" : "instance type boolean";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "s3".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("set3".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("this.s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("this.s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("this.s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                // this one tests the linking mechanism from the field into the local copy
                if (S.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());
                        assertEquals("set3:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("set3:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        // NO s3!
                        assertEquals("set3:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("1".equals(d.statementId())) {
                assertEquals(d.iteration() >= 1, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (S.equals(d.fieldInfo().fullyQualifiedName())) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
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
            assertEquals(DV.FALSE_DV,
                    param0.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Container_4".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            final String TYPE = "org.e2immu.analyser.parser.start.testexample.Container_4";
            final String S = TYPE + ".s";

            if ("crossModify".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof ParameterInfo pi && "in".equals(pi.simpleName())) {
                    assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                }
            }

            if ("m1".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p0 && "modified".equals(p0.name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                }
            }

            if ("m2".equals(d.methodInfo().name) && "toModifyM2".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    String linked = d.iteration() == 0 ? "modified2:0,this.s:-1" : "modified2:0";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("m2".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
            }
            if ("m1".equals(d.methodInfo().name) && S.equals(d.variableName()) && "1".equals(d.statementId())) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("m1".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("m2".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("crossModify".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        testClass("Container_4", 0, 0, new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    @Test
    public void test_5() throws IOException {
        final String CONTAINER_5 = "Container_5";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (CONTAINER_5.equals(d.methodInfo().name) &&
                    d.variable() instanceof ParameterInfo p && "coll5".equals(p.name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "<mod:Collection<String>>" : "nullable instance type Collection<String>/*@Identity*/";
                    assertEquals(expected, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? "this:-1" : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                if (d.variable() instanceof ParameterInfo pi && "coll5".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertEquals("nullable instance type Collection<String>/*@Identity*/",
                                d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // even with all the code in EvaluateParameter and MethodCall to transform new objects into instances,
                        // (see Modification_23, _24), we cannot see its effect here (because coll5 is NOT linked to list!!)
                        // However, the field "list" will have an "instance" as value!
                        String expectValue = d.iteration() == 0 ? "<f:list>" : "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if (CONTAINER_5.equals(d.methodInfo().name) && n == 1) {
                if ("1".equals(d.statementId())) {
                    assertFalse(d.context().evaluationContext().delayStatementBecauseOfECI());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (CONTAINER_5.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().size() == 1) {
                assertDv(d.p(0), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("addAll5".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("list".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertEquals("instance type ArrayList<String>", d.fieldAnalysis().getValue().toString());
                }
            }
        };

        testClass(CONTAINER_5, 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
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
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    sorted.getAnalysis().getProperty(Property.NOT_NULL_EXPRESSION));

            // The type is an @E2Container, so @NotModified is implied; but it need not actually be present!!
            assertEquals(DV.FALSE_DV, sortedAnalysis.getProperty(Property.MODIFIED_METHOD));
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
