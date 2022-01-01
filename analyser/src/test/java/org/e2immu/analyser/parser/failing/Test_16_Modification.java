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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
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
                    String expectLv = d.iteration() == 0 ? "this.set1:0,v:-1" : "this.set1:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set1".equals(d.fieldInfo().name)) {
                assertDv(d, 1, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                Expression e = d.fieldAnalysis().getValue();
                assertEquals("instance type HashSet<String>", e.toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo add = set.findUniqueMethod("add", 1);
            assertEquals(Level.TRUE_DV, add.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            assertEquals(Level.TRUE_DV, addAll.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(Level.FALSE_DV, first.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));

            MethodInfo size = set.findUniqueMethod("size", 0);
            assertEquals(Level.FALSE_DV, size.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(Set.class);
            assertEquals(Level.TRUE_DV, hashSet.typeAnalysis.get().getProperty(Property.CONTAINER));
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
                assertDv(d, 1, Level.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("getFirst".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertNotNull(d.haveError(Message.Label.UNUSED_PARAMETER));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set2")) {
                assertDv(d, 1, Level.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("Modification_1", 0, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test2() throws IOException {
        final String GET_FIRST_VALUE = "set2ter.isEmpty()?\"\":set2ter.stream().findAny().orElseThrow()";
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
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            String name = d.fieldInfo().name;
            if (name.equals("set2ter")) {
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if (name.equals("set2bis")) {
                assertEquals(Level.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 0, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
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
                    assertTrue(d.evaluationResult().causes().isDelayed());
                } else {
                    assertEquals("instance type boolean", d.evaluationResult().value().toString());
                    DV v = d.evaluationResult().changeData().entrySet().stream()
                            .filter(e -> e.getKey().fullyQualifiedName().equals("local3"))
                            .map(Map.Entry::getValue)
                            .map(ecd -> ecd.properties().get(Property.CONTEXT_MODIFIED))
                            .findFirst().orElseThrow();
                    assertEquals(Level.TRUE_DV, v);
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

                    if (d.iteration() == 0) {
                        assertTrue(d.currentValue().isDelayed());
                    } else {
                        assertTrue(d.variableInfo().getValue() instanceof VariableExpression);
                        VariableExpression variableValue = (VariableExpression) d.currentValue();
                        assertTrue(variableValue.variable() instanceof FieldReference);
                        assertEquals("set3", d.currentValue().toString());
                    }
                    assertEquals("local3:0,this.set3:0", d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    //  the READ is written at level 1
                    assertTrue(d.variableInfo().isAssigned());
                    assertTrue(d.variableInfo().isRead());

                    assertTrue(d.variableInfo().getReadId()
                            .compareTo(d.variableInfo().getAssignmentIds().getLatestAssignment()) > 0);
                    if (d.iteration() == 0) {
                        // there is a variable info at levels 0 and 3
                        assertTrue(d.currentValue().isDelayed());
                        assertFalse(d.variableInfoContainer().isInitial());
                    } else {
                        // there is a variable info in level 1, copied from level 1 in statement 0
                        // problem is that there is one in level 3 already, with a NO_VALUE
                        VariableInfo vi1 = d.variableInfoContainer().current();
                        assertEquals("instance type HashSet<String>", vi1.getValue().toString());
                        assertEquals(Level.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                    String expectLv = d.iteration() == 0 ? "local3:0,this.set3:0,v:-1" : "local3:0,this.set3:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("add3".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr && "set3".equals(fr.fieldInfo.name)) {
                assertEquals("org.e2immu.analyser.testexample.Modification_3.set3", d.variableName());
                if ("0".equals(d.statementId())) {
                    assertEquals("local3:0,this.set3:0", d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                    assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                    String expectLv = d.iteration() == 0 ? "local3:0,this.set3:0,v:-1" : "local3:0,this.set3:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                    assertEquals(expectValue, d.variableInfo().getValue().toString());
                    assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() > 1) {
                    assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set3")) {
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals(1, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().size());
                if (d.iteration() > 0) {
                    assertEquals(INSTANCE_TYPE_HASH_SET, d.fieldAnalysis().getValue().toString());
                    if (d.iteration() > 1) {
                        assertEquals(Level.TRUE_DV,
                                d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
                    }
                }
            }
        };


        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addInSet = set.findUniqueMethod("add", 1);
            assertEquals(Level.TRUE_DV, addInSet.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(HashSet.class);
            MethodInfo addInHashSet = hashSet.findUniqueMethod("add", 1);
            assertEquals(Level.TRUE_DV, addInHashSet.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
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
                    assertTrue(d.currentValue().isDelayed());
                } else {
                    assertEquals("0-E", d.variableInfo().getReadId());
                    assertEquals("instance type Set<String>", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    // via statical assignments
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }

            if ("add4".equals(d.methodInfo().name) && "local4".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.getProperty(Property.MODIFIED_VARIABLE).isDelayed());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);

                    String expect = d.iteration() == 0 ? SET4_DELAYED : "set4";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    String expect = d.iteration() == 0 ? "<f:set4>" : "instance type Set<String>";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("Modification_4".equals(d.methodInfo().name) && SET4.equals(d.variableName()) && "0".equals(d.statementId())) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
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
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));

                assertEquals("in4", d.fieldAnalysis().getValue().toString());
                assertEquals("in4:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Modification_4".equals(name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d.p(0), 2, Level.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("add4".equals(name)) {
                FieldInfo set4 = d.methodInfo().typeInfo.getFieldByName("set4", true);
                if (iteration >= 1) {
                    VariableInfo vi = d.getFieldAsVariable(set4);
                    assert vi != null;
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.CONTEXT_NOT_NULL));
                    assertEquals(Level.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
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
                assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }
            if ("Modification_5".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_5.set5".equals(d.variableName()) && "0".equals(d.statementId())) {
                assertDv(d, 3, Level.TRUE_DV, Property.FINAL);
                String expectValue = "new HashSet<>(in5)/*this.size()==in5.size()*/";
                assertEquals(expectValue, d.currentValue().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertEquals(Level.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, Level.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set5".equals(d.fieldInfo().name)) {
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        testClass("Modification_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test6() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_6";
        final String SET6 = TYPE + ".set6";
        final String EXAMPLE6_SET6 = TYPE + ".set6#" + TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String EXAMPLE6 = TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String VALUES6 = TYPE + ".add6(Modification_6,Set<String>):1:values6";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("add6".equals(d.methodInfo().name)) {
                if (VALUES6.equals(d.variableName())) {
                    assertEquals(Level.FALSE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }

                if (EXAMPLE6.equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ?
                            "<parameter:org.e2immu.analyser.testexample.Modification_6.add6(Modification_6,Set<String>):0:example6>" :
                            "nullable? instance type Modification_6";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
                if (EXAMPLE6_SET6.equals(d.variableName())) {
                    if (d.iteration() > 0)
                        assertEquals(Level.TRUE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                }
            }
            if ("Modification_6".equals(d.methodInfo().name)) {
                if (SET6.equals(d.variableName()) && "0".equals(d.statementId()) && d.iteration() == 3) {
                    assertEquals(Level.TRUE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set6")) {

                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                assertEquals("in6", d.fieldAnalysis().getValue().toString());
                assertEquals("in6:0", d.fieldAnalysis().getLinkedVariables().toString());

                if (iteration >= 1) {
                    assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 0, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("Example6".equals(name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d.p(0), 2, Level.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("add6".equals(name)) {
                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);

                FieldInfo set6 = d.methodInfo().typeInfo.getFieldByName("set6", true);
                VariableInfo set6VariableInfo = d.getFieldAsVariable(set6);
                assertNull(set6VariableInfo); // this variable does not occur!

                List<VariableInfo> vis = d.methodAnalysis().getLastStatement()
                        .latestInfoOfVariablesReferringTo(set6, false);
                assertEquals(1, vis.size());
                VariableInfo vi = vis.get(0);
                if (d.iteration() > 0) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.NOT_NULL_EXPRESSION));
                    assertEquals(Level.TRUE_DV, vi.getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo p0 = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.parameterAnalysis.get()
                    .getProperty(Property.NOT_NULL_PARAMETER));
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_8".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr
                    && "set".equals(fr.fieldInfo.name)) {
                assertEquals("input/*@NotNull*/", d.currentValue().toString());
                assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.IMMUTABLE));
            }
        };
        testClass("Modification_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
                    assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 1,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("theSet".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s2>" : "s2";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s2>" : "instance type HashSet<String>";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() == 0 ? "s:-1,theSet:0,this.s2:0" : "theSet:0,this.s2:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (S2.equals(d.variableName())) {
                    if (d.iteration() > 0) {
                        assertEquals("theSet:0,this.s2:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if (("2".equals(d.statementId()) || "3".equals(d.statementId())) && d.iteration() > 1) {
                        assertEquals(Level.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                    if ("3".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (ADD.equals(d.methodInfo().fullyQualifiedName) && d.iteration() > 1) {
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s2".equals(d.fieldInfo().name)) {
                assertDv(d, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo add = set.findUniqueMethod("add", 1);
            ParameterInfo p0Add = add.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.INDEPENDENT_1_DV, p0Add.parameterAnalysis.get()
                    .getProperty(Property.INDEPENDENT));
        };

        // there is no transparent content in this type; as a consequence, the parameter s
        // can never be @Dependent1 (even if it weren't of immutable type String)
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_9".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("addAll".equals(d.methodInfo().name) && "d".equals(d.variableName())) {
                assertEquals(Level.FALSE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
            }
            if ("addAll".equals(d.methodInfo().name) && "c".equals(d.variableName())) {
                assertEquals(Level.TRUE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();

            if ("Modification_10".equals(d.methodInfo().name)) {
                ParameterAnalysis list = d.parameterAnalyses().get(0);
                ParameterAnalysis set3 = d.parameterAnalyses().get(1);

                if (iteration == 0) {
                    assertFalse(list.isAssignedToFieldDelaysResolved());
                } else {
                    assertTrue(list.isAssignedToFieldDelaysResolved());
                    assertEquals("c0=1, c1=2, l0=100, l1=100, l2=100, s0=100, s1=100", list.getAssignedToField()
                            .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(", ")));
                }
                if (iteration >= 2) {
                    assertEquals(Level.FALSE_DV, list.getProperty(Property.MODIFIED_VARIABLE));
                    assertFalse(set3.getAssignedToField().isEmpty());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            FieldInfo fieldInfo = d.fieldInfo();
            if ("c0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    assertEquals(Level.FALSE_DV, d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
                }
            }
            if ("s0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            assertEquals(Level.TRUE_DV, addAll.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(Level.FALSE_DV, first.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));

        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_10".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
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

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    // not a direct assignment!
                    assertEquals("setC:1,this.set:0", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo setC && "setC".equals(setC.name)) {
                    assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                }
            }

            if ("getSet".equals(d.methodInfo().name) && SET_IN_C1.equals(d.variableName())) {
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
            }

            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertDv(d, 2, Level.TRUE_DV, Property.CONTEXT_MODIFIED);

                    String expectLv = d.iteration() <= 1 ? "string:-1,this.set:0" : "this.set:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo s && "string".equals(s.name)) {
                    String expectLv = d.iteration() <= 1 ? "string:0,this.set:-1" : "string:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
            }


            if ("example1".equals(d.methodInfo().name)) {
                if (S2.equals(d.variableName()) && "0".equals(d.statementId())) {
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertEquals("c:-1,this.s2:0", d.variableInfo().getLinkedVariables().toString());
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));

                    }
                    String expectLinked = d.iteration() <= 2 ? "c:0,this.s2:-1" : "this.s2";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 2 ? "<m:addAll>" : "instance type boolean";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("addAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi1 && "d".equals(pi1.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                            d.getProperty(Property.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi0 && "c".equals(pi0.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
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
                        d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("example1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertEquals("c.set:0,localD.set:0,setC:1", d.fieldAnalysis().getLinkedVariables().toString());
                assertEquals("setC/*@NotNull*/", d.fieldAnalysis().getValue().debugOutput());
                // the field analyser sees addAll being used on set in the method addAllOnC
                assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 2, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("s2".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 3, Level.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("addAll".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 1, Level.TRUE_DV, Property.MODIFIED_VARIABLE);

                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(1), 1, Level.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_11".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        testClass("Modification_11", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
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
                assertEquals(Level.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId()) && d.variable() instanceof This) {
                assertEquals(PARENT_CLASS_THIS, d.variableName());
                assertEquals(Level.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    assertEquals("ParentClass", thisVar.typeInfo.simpleName);
                    assertEquals(PARENT_CLASS_THIS, d.variableName());
                    assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                    // we have to wait for clearAndLog in ParentClass, which is analysed AFTER this one
                }
            }

            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    assertEquals("ChildClass", thisVar.explicitlyWriteType.simpleName);
                    assertEquals("ParentClass", thisVar.typeInfo.simpleName);
                    assertEquals(PARENT_CLASS_THIS, d.variableName());
                    assertEquals(Level.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                Expression scope = ((MethodCall) ((ExpressionAsStatement) d.statementAnalysis().statement()).expression).object;
                VariableExpression variableExpression = (VariableExpression) scope;
                This t = (This) variableExpression.variable();
                assertNotNull(t.explicitlyWriteType);
                assertTrue(t.writeSuper);
            }
            // we make sure that super.clearAndLog refers to the method in ParentClass
            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.statementAnalysis().statement() instanceof ExpressionAsStatement expressionAsStatement) {
                    Expression expression = expressionAsStatement.expression;
                    if (expression instanceof MethodCall methodCall) {
                        assertEquals("org.e2immu.analyser.testexample.Modification_12.ParentClass.clearAndLog()",
                                methodCall.methodInfo.fullyQualifiedName);
                    } else fail();
                } else fail();
                if (d.iteration() == 0) {
                    assertFalse(d.statementAnalysis().stateData().preconditionIsFinal());
                    assertFalse(d.statementAnalysis().stateData().preconditionIsEmpty());
                } else {
                    assertTrue(d.statementAnalysis().stateData().preconditionIsFinal());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("clear".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(Level.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("clearAndAdd".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, 1, Level.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("clear".equals(name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(Level.TRUE_DV, d.getThisAsVariable().getProperty(Property.CONTEXT_MODIFIED));
                assertTrue(d.getThisAsVariable().isRead());
                assertEquals(Level.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
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
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
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
                assertEquals(Level.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                assertEquals(Level.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
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
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        if (d.iteration() > 0) {
                            VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.EXTERNAL_NOT_NULL));
                            assertTrue(d.variableInfoContainer().hasMerge());
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                        }
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference
                        && "input".equals(fieldReference.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("input".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
                assertEquals("input", d.fieldAnalysis().getValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
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
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        /*
        in iteration 2, statementAnalysis should copy the IMMUTABLE value of 1 of input into the variable's properties
         */
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_15".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertTrue(d.iteration() < 2 || "1".equals(d.statementId()));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                        assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }

                } else if (d.variable() instanceof FieldReference fr && "input".equals(fr.fieldInfo.name)) {
                    assertEquals("1", d.statementId());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                } else if (d.variable() instanceof This) {
                    assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_IMMUTABLE);
                } else {
                    fail("?" + d.variableName());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_15".equals(d.methodInfo().name)) {
                assertDv(d.p(0), MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("Modification_15", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
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
                        assertDv(d, 1, Level.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, 1, "?", "this.s2");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "c".equals(fr.scope.toString())) {
                    if ("2".equals(d.statementId())) {
                        // delays in iteration 1, because no value yet
                        assertDv(d, 2, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);

                assertDv(d.p(0), 3, Level.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 4, Level.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("addAll".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, Level.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertLinked(d, 1, "?", "setC");
                assertEquals(d.iteration() > 0,
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                if (d.iteration() == 0) assertNull(d.typeAnalysis().getTransparentTypes());
                else
                    assertEquals("[]", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Modification_19", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    /*
    What do we know, when?

    In iteration 1:
    - links of fields 'set' and 's2' are established
    - addAll does not change the parameters

    In iteration 2:
    - in example1(), 'c.set' and 'd.set' are not modified (CM = FALSE)
    - in example1(), 'c' has value, linked variables

     */
    @Test
    public void test20() throws IOException {

        // infinite loop
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertFalse(d.evaluationContext().isMyself(d.variable()));

                    assertEquals("setC", d.currentValue().toString());
                    assertEquals("setC:0,this.set:0", d.variableInfo().getLinkedVariables().toString());
                }
            }

            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This thisVar && "Modification_20".equals(thisVar.typeInfo.simpleName)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, Level.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, Level.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                // applies to c.set and d.set
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertDv(d, 2, Level.FALSE_DV, Property.CONTEXT_MODIFIED);

                    String expectValue = d.iteration() <= 1 ? "<f:set>" : "instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if ("c".equals(d.variableName())) {
                    String expectLinked;
                    String expectValue;

                    if ("2".equals(d.statementId())) {
                        expectValue = d.iteration() <= 1 ? "<new:C1>" : "new C1(s2)";
                        expectLinked = d.iteration() <= 1 ? "c:0,this.s2:-1" : "this.s2";
                    } else {
                        // "0", "1"...
                        expectValue = d.iteration() == 0 ? "<new:C1>" : "new C1(s2)";
                        expectLinked = d.iteration() == 0 ? "c:0,this.s2:-1" : "this.s2";
                    }
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                // is a constructor:
                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(Property.INDEPENDENT));

                assertDv(d.p(0), 3, Level.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            // addAll will not modify its parameters
            if ("addAll".equals(d.methodInfo().name)) {
                assertDv(d.p(0), Level.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), Level.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertLinked(d, 1, "?", "c.set:0,localD.set:0,setC:0");

                assertTrue(((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());

                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                // value from the constructor
                assertEquals("setC", d.fieldAnalysis().getValue().toString());

                assertDv(d, 2, Level.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);

            }
            if ("s2".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("instance type HashSet<String>", d.fieldAnalysis().getValue().toString());
                assertTrue(((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());

                assertDv(d, 3, Level.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo equals = set.findUniqueMethod("equals", 1);
            DV mm = equals.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD);
            assertEquals(Level.FALSE_DV, mm);
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.util.Set<java.lang.String>", d.typeAnalysis().getTransparentTypes().toString());
            }
            if ("Modification_20".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Modification_20", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test21() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.util.Set<java.lang.String>", d.typeAnalysis().getTransparentTypes().toString());
            }
            if ("Modification_21".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Modification_21", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test22() throws IOException {
        testClass("Modification_22", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
