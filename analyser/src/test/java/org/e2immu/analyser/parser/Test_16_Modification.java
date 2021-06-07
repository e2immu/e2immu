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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification extends CommonTestRunner {

    public Test_16_Modification() {
        super(true);
    }

    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set1".equals(fr.fieldInfo.name)) {
                    assertTrue(d.variableInfoContainer().hasEvaluation() && !d.variableInfoContainer().hasMerge());
                    assertTrue(d.variableInfo().isRead());
                    String expectValue = d.iteration() == 0 ?
                            "<field:org.e2immu.analyser.testexample.Modification_0.set1>" :
                            "instance type HashSet<String>";
                    assertEquals(expectValue, d.currentValue().debugOutput());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                    int expectContextModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectContextModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectModified, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set1".equals(d.fieldInfo().name)) {
                int expect = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expect, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));

                Expression e = d.fieldAnalysis().getEffectivelyFinalValue();
                assertEquals("instance type HashSet<String>", e.toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            assertEquals(AnnotationMode.GREEN, set.typeInspection.get().annotationMode());
            MethodInfo add = set.findUniqueMethod("add", 1);
            assertEquals(Level.TRUE, add.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));

            MethodInfo size = set.findUniqueMethod("size", 0);
            assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(Set.class);
            assertEquals(Level.TRUE, hashSet.typeAnalysis.get().getProperty(VariableProperty.CONTAINER));
        };

        testClass("Modification_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("size".equals(d.methodInfo().name) && "Modification_1".equals(d.methodInfo().typeInfo.simpleName)) {
                int expect = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expect, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("getFirst".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertNotNull(d.haveError(Message.Label.UNUSED_PARAMETER));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set2")) {
                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                if (d.iteration() == 0) {
                    assertEquals(Level.DELAY, modified);
                } else {
                    assertEquals(Level.FALSE, modified);
                }
            }
        };

        testClass("Modification_1", 0, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test2() throws IOException {
        final String GET_FIRST_VALUE = "set2ter.isEmpty()?\"\":(instance type Stream<E>/*@Dependent2*/).findAny().orElseThrow()";
        final String GET_FIRST_VALUE_DELAYED = "<m:isEmpty>?\"\":<m:orElseThrow>";
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getFirst".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? GET_FIRST_VALUE_DELAYED : GET_FIRST_VALUE;
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("org.e2immu.analyser.testexample.Modification_2.Example2ter.getFirst(String)".equals(d.variableName())) {
                String expect = d.iteration() == 0 ? GET_FIRST_VALUE_DELAYED : GET_FIRST_VALUE;

                assertEquals(expect, d.currentValue().toString());
                assertNotSame(LinkedVariables.DELAY, d.currentValue().linkedVariables(d.evaluationContext()));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set2ter")) {
                int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
                assertEquals(Level.TRUE, effFinal);

                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                int expectModified = iteration == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, modified);
            }
            if (name.equals("set2bis")) {
                int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
                int expectFinal = Level.FALSE;
                assertEquals(expectFinal, effFinal);

                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                int expectModified = Level.TRUE;
                assertEquals(expectModified, modified);
            }
        };

        testClass("Modification_2", 1, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

    }

    @Test
    public void test3() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    assertTrue(d.evaluationResult().someValueWasDelayed());
                } else {
                    assertEquals("set3.add(v)", d.evaluationResult().value().toString());
                    int v = d.evaluationResult().changeData().entrySet().stream()
                            .filter(e -> e.getKey().fullyQualifiedName().equals("local3"))
                            .map(Map.Entry::getValue)
                            .mapToInt(ecd -> ecd.properties().get(VariableProperty.CONTEXT_MODIFIED))
                            .findFirst().orElseThrow();
                    assertEquals(Level.TRUE, v);
                }
            }
        };
        final String INSTANCE_TYPE_HASH_SET = "instance type HashSet<String>";
        final String SET3_DELAYED = "<f:set3>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "local3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isAssigned());
                    assertFalse(d.variableInfo().isRead());
                    assertEquals("this.set3", d.variableInfo().getStaticallyAssignedVariables().toString());

                    if (d.iteration() == 0) {
                        assertTrue(d.currentValueIsDelayed());
                    } else {
                        assertTrue(d.variableInfo().getValue() instanceof VariableExpression);
                        VariableExpression variableValue = (VariableExpression) d.currentValue();
                        assertTrue(variableValue.variable() instanceof FieldReference);
                        assertEquals("set3", d.currentValue().toString());
                    }
                    if (d.iteration() > 0) {
                        assertEquals("this.set3", d.variableInfo().getLinkedVariables().toString());
                    } else {
                        assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                    }
                }
                if ("1".equals(d.statementId())) {
                    //  the READ is written at level 1
                    assertTrue(d.variableInfo().isAssigned());
                    assertTrue(d.variableInfo().isRead());
                    assertEquals("this.set3", d.variableInfo().getStaticallyAssignedVariables().toString());

                    assertTrue(d.variableInfo().getReadId().compareTo(d.variableInfo().getAssignmentId()) > 0);
                    if (d.iteration() == 0) {
                        // there is a variable info at levels 0 and 3
                        assertTrue(d.currentValueIsDelayed());
                        assertFalse(d.variableInfoContainer().isInitial());
                    } else {
                        // there is a variable info in level 1, copied from level 1 in statement 0
                        // problem is that there is one in level 3 already, with a NO_VALUE
                        VariableInfo vi1 = d.variableInfoContainer().current();
                        assertEquals("instance type HashSet<String>", vi1.getValue().toString());
                        assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if (d.iteration() > 0) {
                        assertEquals("this.set3", d.variableInfo().getLinkedVariables().toString());
                    } else {
                        assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                    }
                }
            }
            if ("add3".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_3.set3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                    assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                    String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                    assertEquals(expectValue, d.variableInfo().getValue().toString());
                    int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() > 1) {
                    assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set3")) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals(1, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().expressions().length);
                if (d.iteration() > 0) {
                    assertEquals(INSTANCE_TYPE_HASH_SET, d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    if (d.iteration() > 1) {
                        assertEquals(Level.TRUE,
                                d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                    }
                }
            }
        };


        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addInSet = set.findUniqueMethod("add", 1);
            assertEquals(Level.TRUE, addInSet.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(HashSet.class);
            MethodInfo addInHashSet = hashSet.findUniqueMethod("add", 1);
            assertEquals(Level.TRUE, addInHashSet.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("Modification_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    /*
    What happens in each iteration?
    IT 0: READ, ASSIGNED; set4 FINAL in field analyser, gets value and linked variables
    IT 1: set4 gets a value in add4; set4 linked to in4
    IT 2: set4 MODIFIED, NOT_NULL;
     */

    @Test
    public void test4() throws IOException {
        final String SET4 = "org.e2immu.analyser.testexample.Modification_4.set4";
        final String SET4_DELAYED = "<f:set4>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && SET4.equals(d.variableName())) {
                if (d.iteration() == 0) {
                    assertTrue(d.currentValueIsDelayed());
                } else {
                    assertEquals("0-E", d.variableInfo().getReadId());
                    assertEquals("instance type Set<String>", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    // via statical assignments
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("add4".equals(d.methodInfo().name) && "local4".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals(Level.DELAY, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    String expect = d.iteration() == 0 ? SET4_DELAYED : "set4";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    String expect = d.iteration() == 0 ? "<f:set4>" : "instance type Set<String>";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("Modification_4".equals(d.methodInfo().name) && SET4.equals(d.variableName()) && "0".equals(d.statementId())) {
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMv, d.getProperty(VariableProperty.MODIFIED_VARIABLE));

                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                assertEquals("in4", d.currentValue().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertNull(d.haveError(Message.Label.NULL_POINTER_EXCEPTION));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set4")) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                assertEquals("in4", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "in4";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Modification_4".equals(name)) {
                ParameterAnalysis in4 = d.parameterAnalyses().get(0);
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNN, in4.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, in4.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("add4".equals(name)) {
                FieldInfo set4 = d.methodInfo().typeInfo.getFieldByName("set4", true);
                if (iteration >= 1) {
                    VariableInfo vi = d.getFieldAsVariable(set4);
                    assert vi != null;
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                }
            }
        };

        testClass("Modification_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p &&
                    "in5".equals(p.name) && "0".equals(d.statementId())) {
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
            if ("Modification_5".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_5.set5".equals(d.variableName()) && "0".equals(d.statementId())) {
                assertEquals(d.iteration() <= 1 ? Level.DELAY : Level.TRUE, d.getProperty(VariableProperty.FINAL));
                String expectValue = "new HashSet<>(in5)/*this.size()==in5.size()*/";
                assertEquals(expectValue, d.currentValue().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name)) {
                ParameterAnalysis in5 = d.parameterAnalyses().get(0);
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, in5.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Modification_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test6() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_6";
        final String SET6 = TYPE + ".set6";
        final String IN6 = TYPE + ".Modification_6(Set<String>):0:in6";
        final String EXAMPLE6_SET6 = TYPE + ".set6#" + TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String EXAMPLE6 = TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String VALUES6 = TYPE + ".add6(Modification_6,Set<String>):1:values6";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("add6".equals(d.methodInfo().name)) {
                if (VALUES6.equals(d.variableName())) {
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }

                if (EXAMPLE6.equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ?
                            "<parameter:org.e2immu.analyser.testexample.Modification_6.add6(Modification_6,Set<String>):0:example6>" :
                            "nullable? instance type Modification_6";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (EXAMPLE6_SET6.equals(d.variableName())) {
                    if (d.iteration() > 0)
                        assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    if (d.iteration() > 1)
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("Modification_6".equals(d.methodInfo().name)) {
                if (SET6.equals(d.variableName()) && "0".equals(d.statementId()) && d.iteration() == 3) {
                    assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (IN6.equals(d.variableName()) && "0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertFalse(d.hasProperty(VariableProperty.MODIFIED_VARIABLE));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set6")) {
                if (iteration == 0) {
                    assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                }
                if (iteration >= 1) {
                    assertEquals("in6", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    assertEquals("in6", d.fieldAnalysis().getLinkedVariables().toString());
                }
                if (iteration >= 2) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                    assertEquals(Level.TRUE, modified);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Example6".equals(name)) {
                ParameterAnalysis in6 = d.parameterAnalyses().get(0);

                int expectIn6NotNull = iteration < 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectIn6NotNull, in6.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                int expectIn6Modified = iteration < 2 ? Level.FALSE : Level.TRUE;
                assertEquals(expectIn6Modified, in6.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("add6".equals(name)) {
                ParameterAnalysis values6 = d.parameterAnalyses().get(1);
                int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectNnp, values6.getProperty(VariableProperty.NOT_NULL_PARAMETER));

                FieldInfo set6 = d.methodInfo().typeInfo.getFieldByName("set6", true);
                VariableInfo set6VariableInfo = d.getFieldAsVariable(set6);
                assertNull(set6VariableInfo); // this variable does not occur!

                List<VariableInfo> vis = d.methodAnalysis().getLastStatement()
                        .latestInfoOfVariablesReferringTo(set6, false);
                assertEquals(1, vis.size());
                VariableInfo vi = vis.get(0);
                if (d.iteration() > 0) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals(Level.TRUE, vi.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo p0 = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.parameterAnalysis.get()
                    .getProperty(VariableProperty.NOT_NULL_PARAMETER));
        };

        testClass("Modification_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test7() throws IOException {
        testClass("Modification_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test8() throws IOException {
        testClass("Modification_8", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test9() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_9";
        final String S2 = TYPE + ".s2";
        final String ADD = TYPE + ".add(String)";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 1,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "theSet".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if ("2".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if (d.iteration() == 0) {
                    assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                } else {
                    assertEquals("this.s2", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.statementId().equals("1") && d.iteration() > 0) {
                    assertEquals("s2", d.currentValue().toString());
                }
                if (d.statementId().equals("2") && d.iteration() > 0) {
                    assertEquals("instance type HashSet<String>", d.currentValue().toString());
                }
            }
            if ("add".equals(d.methodInfo().name) && S2.equals(d.variableName())) {
                if (d.iteration() > 0) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (("2".equals(d.statementId()) || "3".equals(d.statementId())) && d.iteration() > 1) {
                    assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if ("3".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (ADD.equals(d.methodInfo().fullyQualifiedName) && d.iteration() > 1) {
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s2".equals(d.fieldInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("addAll".equals(d.methodInfo().name) && "d".equals(d.variableName())) {
                assertEquals(0, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("addAll".equals(d.methodInfo().name) && "c".equals(d.variableName())) {
                assertEquals(1, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();

            if ("Modification_10".equals(d.methodInfo().name)) {
                ParameterAnalysis list = d.parameterAnalyses().get(0);
                ParameterAnalysis set3 = d.parameterAnalyses().get(1);

                if (iteration == 0) {
                    assertFalse(list.isAssignedToFieldDelaysResolved());
                } else if (iteration == 1) {
                    assertFalse(list.isAssignedToFieldDelaysResolved());
                    assertEquals("c0=ASSIGNED, l0=NO, l1=NO, l2=NO, s1=NO", list.getAssignedToField()
                            .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(", ")));
                } else {
                    assertTrue(list.isAssignedToFieldDelaysResolved());
                    assertEquals("c0=ASSIGNED, c1=LINKED, l0=NO, l1=NO, l2=NO, s0=NO, s1=NO", list.getAssignedToField()
                            .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(", ")));
                }
                if (iteration >= 2) {
                    assertEquals(0, list.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    assertFalse(set3.getAssignedToField().isEmpty());
                    // assertEquals(1, set3.getProperty(VariableProperty.MODIFIED)); // directly assigned to s0
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            FieldInfo fieldInfo = d.fieldInfo();
            if ("c0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    assertEquals(0, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                }
            }
            if ("s0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    assertEquals(1, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));

            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));

        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_10".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getImplicitlyImmutableDataTypes().isEmpty());
            }
        };

        testClass("Modification_10", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test11() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_11";
        final String SET_IN_C1 = TYPE + ".C1.set";
        final String SET_IN_C1_DELAYED = "<f:set>";
        final String S2 = TYPE + ".s2";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("example1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ChangeData s2 = d.findValueChange(S2);
                int expectCnn = d.iteration() <= 1 ? Level.TRUE : Level.DELAY;
                assertEquals(expectCnn, s2.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    // not a direct assignment!
                    assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "setC";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo setC && "setC".equals(setC.name)) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("getSet".equals(d.methodInfo().name) && SET_IN_C1.equals(d.variableName())) {
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
            }

            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    int expectCNN = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCNN, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    String expectLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo s && "string".equals(s.name)) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }


            if ("example1".equals(d.methodInfo().name)) {
                if (S2.equals(d.variableName()) && "0".equals(d.statementId())) {
                    int expectCnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                    assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                    assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                    }
                    String expectLinked = d.iteration() <= 2 ? LinkedVariables.DELAY_STRING : "this.s2";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "<m:addAll>" : "c.set.addAll(localD.set)";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("addAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi1 && "d".equals(pi1.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                            d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi0 && "c".equals(pi0.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
            if ("Modification_11".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() < 2 ? "<m:getSet>" : "set2";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(d.iteration() >= 2,
                        d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
            if ("example1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "setC";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
                assertEquals("setC/*@NotNull*/", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
                // the field analyser sees addAll being used on set in the method addAllOnC
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectMm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMm, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
            if ("s2".equals(d.fieldInfo().name)) {
                int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectNnp = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectNnp, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("addAll".equals(d.methodInfo().name)) {
                ParameterAnalysis p1 = d.parameterAnalyses().get(1);
                int expectNnp1 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                assertEquals(expectNnp1, p1.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                int expectMp1 = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMp1, p1.getProperty(VariableProperty.MODIFIED_VARIABLE));

                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectNnp0 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNnp0, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                int expectMp0 = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMp0, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_11".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getImplicitlyImmutableDataTypes().isEmpty());
            }
        };

        testClass("Modification_11", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }


    @Test
    public void test12() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_12";
        final String PARENT_CLASS_THIS = TYPE + ".ParentClass.this";
        final String PARENT_CLASS_SET = TYPE + ".ParentClass.set";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && PARENT_CLASS_SET.equals(d.variableName())) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId()) && d.variable() instanceof This) {
                assertEquals(PARENT_CLASS_THIS, d.variableName());
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    assertEquals("ParentClass", thisVar.typeInfo.simpleName);
                    assertEquals(PARENT_CLASS_THIS, d.variableName());
                    int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    // we have to wait for clearAndLog in ParentClass, which is analysed AFTER this one
                    assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    assertEquals("ChildClass", thisVar.explicitlyWriteType.simpleName);
                    assertEquals("ParentClass", thisVar.typeInfo.simpleName);
                    assertEquals(PARENT_CLASS_THIS, d.variableName());
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                Expression scope = ((MethodCall) ((ExpressionAsStatement) d.statementAnalysis().statement).expression).object;
                VariableExpression variableExpression = (VariableExpression) scope;
                This t = (This) variableExpression.variable();
                assertNotNull(t.explicitlyWriteType);
                assertTrue(t.writeSuper);
            }
            // we make sure that super.clearAndLog refers to the method in ParentClass
            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof ExpressionAsStatement expressionAsStatement) {
                    Expression expression = expressionAsStatement.expression;
                    if (expression instanceof MethodCall methodCall) {
                        assertEquals("org.e2immu.analyser.testexample.Modification_12.ParentClass.clearAndLog()",
                                methodCall.methodInfo.fullyQualifiedName);
                    } else fail();
                } else fail();
                if (d.iteration() == 0) {
                    assertFalse(d.statementAnalysis().stateData.preconditionIsFinal());
                    assertFalse(d.statementAnalysis().stateData.preconditionIsEmpty());
                } else {
                    assertTrue(d.statementAnalysis().stateData.preconditionIsFinal());
                    assertTrue(d.statementAnalysis().stateData.getPrecondition().isEmpty());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("clear".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("clearAndAdd".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("clear".equals(name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.getThisAsVariable().getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertTrue(d.getThisAsVariable().isRead());
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("clearAndLog".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
            if ("clearAndLog".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPrecondition());
                } else {
                    assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
                }
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            TypeInfo typeInfo = d.typeInfo();
            if ("ParentClass".equals(typeInfo.simpleName)) {
                assertEquals("Modification_12", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
            }
            if ("ChildClass".equals(typeInfo.simpleName)) {
                assertEquals("Modification_12", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
            }
            if ("InnerOfChild".equals(typeInfo.simpleName)) {
                assertEquals("ChildClass", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                int expectImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
            if ("ModifiedThis".equals(typeInfo.simpleName)) {
                assertEquals("org.e2immu.analyser.testexample", typeInfo.packageNameOrEnclosingType.getLeft());
            }
        };

        testClass("Modification_12", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test13() throws IOException {
        final String INNER_THIS = "org.e2immu.analyser.testexample.Modification_13.Inner.this";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name) && INNER_THIS.equals(d.variableName())) {
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE; // TRUE good, FALSE would have been acceptable as well
                assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };
        testClass("Modification_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_14".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo input && "input".equals(input.name)) {
                    if ("0".equals(d.statementId())) {
                        int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        assertEquals(expectEnn, eval.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference
                        && "input".equals(fieldReference.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("input".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals("input", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        // ! no warning @NotNull on field -> if(...)
        testClass("Modification_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test15() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass("Modification_15", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test16() throws IOException {
        // one on the type, one error on the method
        testClass("Modification_16_M", 2, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test17() throws IOException {
        // statics
        testClass("Modification_17", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // static method reference
    @Test
    public void test18() throws IOException {
        // statics
        testClass("Modification_18", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test19() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This thisVar && "Modification_19".equals(thisVar.typeInfo.simpleName)) {
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                ParameterAnalysis setC = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMv, setC.getProperty(VariableProperty.MODIFIED_VARIABLE));

                int expectMm = d.iteration() <= 3 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
                String expectLinked1 = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "setC";
                assertEquals(expectLinked1, d.fieldAnalysis().getLinked1Variables().toString());
                assertEquals(d.iteration() > 0,
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished.isSet());

                // however, the field "set" is @Modified from iteration 2 onwards
                int expectMom = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };
        testClass("Modification_19", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test20() throws IOException {
        final int HIGH = 50;

        // infinite loop
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This thisVar && "Modification_20".equals(thisVar.typeInfo.simpleName)) {
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        //   assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() <= HIGH ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                // applies to c.set and d.set
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                    String expectValue = d.iteration() <= 1 ? "<f:set>" : "nullable instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());

                    String expectLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                ParameterAnalysis setC = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= HIGH ? Level.DELAY : Level.TRUE;
                assertEquals(expectMv, setC.getProperty(VariableProperty.MODIFIED_VARIABLE));

                int expectMm = d.iteration() <= HIGH ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            // addAll will not modify its parameters
            if ("addAll".equals(d.methodInfo().name)) {
                int expectMv = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
                ParameterAnalysis p1 = d.parameterAnalyses().get(1);
                assertEquals(expectMv, p1.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
                String expectLinked1 = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "setC";
                assertEquals(expectLinked1, d.fieldAnalysis().getLinked1Variables().toString());
                assertEquals(d.iteration() > 0,
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished.isSet());

                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                // value from the constructor
                assertEquals("setC", d.fieldAnalysis().getEffectivelyFinalValue().toString());

                int expectMom = d.iteration() <= HIGH ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
            if ("s2".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals("instance type HashSet<String>", d.fieldAnalysis().getEffectivelyFinalValue().toString());

                int expectMom = d.iteration() <= HIGH ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo equals = set.findUniqueMethod("equals", 1);
            int mm = equals.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD);
            assertEquals(Level.FALSE, mm);
        };

        testClass("Modification_20", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }
}
