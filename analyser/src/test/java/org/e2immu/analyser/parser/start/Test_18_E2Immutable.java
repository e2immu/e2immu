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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_18_E2Immutable extends CommonTestRunner {
    public Test_18_E2Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isAbc".equals(d.methodInfo().name)) {
                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(INDEPENDENT));
            }
            if ("E2Immutable_0".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
                assertDv(d.p(1), 0, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("level1".equals(d.fieldInfo().name)) {
                assertEquals("level1Param:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("value1".equals(d.fieldInfo().name)) {
                assertEquals("value:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        testClass("E2Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            // the constructor with 1 parameter
            if ("E2Immutable_1".equals(d.methodInfo().name) &&
                    d.methodInfo().methodInspection.get().getParameters().size() == 1) {
                // this.parent2

                /*
                problematic here is that the null value relies on the value properties of the primary type, which have
                not been determined yet. We wrap (see FieldReference_3) the null because we cannot write delayed value
                properties on a non-delayed value (null).
                 */
                if (d.variable() instanceof FieldReference fr && "parent2".equals(fr.fieldInfo.name) && fr.scopeIsThis()) {
                    String expected = d.iteration() <= 3 ? "<wrapped:parent2>" : "null";
                    assertEquals(expected, d.currentValue().toString());
                    mustSeeIteration(d, 4);
                }
            }
            // the constructor with 2 parameters
            if ("E2Immutable_1".equals(d.methodInfo().name) &&
                    d.methodInfo().methodInspection.get().getParameters().size() == 2) {
                assertTrue(d.methodInfo().isConstructor);

                // parent2Param.level2
                if (d.variable() instanceof FieldReference fr && "level2".equals(fr.fieldInfo.name) &&
                        fr.scope instanceof VariableExpression ve && "parent2Param".equals(ve.variable().simpleName())) {
                    assertNotEquals("0", d.statementId());
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:level2>" : "instance type int";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }

                // this.level2
                if (d.variable() instanceof FieldReference fr && "level2".equals(fr.fieldInfo.name) && fr.scopeIsThis()) {
                    if ("1".equals(d.statementId())) {
                        /*
                         we never know in the first iteration...
                         note the * in field*: this is an indication for the field analyser to break the delay

                         this is the 2nd delay breaking after the null value in the other constructor
                         */
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "2+<f:parent2Param.level2>";
                            case 1 -> "<wrapped:level2>";
                            default -> "2+parent2Param.level2";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }

                // this.parent2
                if (d.variable() instanceof FieldReference fr && "parent2".equals(fr.fieldInfo.name) && fr.scopeIsThis()) {
                    assertEquals("parent2Param", d.currentValue().toString());
                }

                // parent2Param
                if (d.variable() instanceof ParameterInfo pi && pi.name.equals("parent2Param")) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type E2Immutable_1/*@Identity*/",
                                d.currentValue().toString());
                        assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:parent2Param>" :
                                "nullable instance type E2Immutable_1/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, IMMUTABLE);
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("parent2".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, FINAL);
                String expected = "[null,parent2Param]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
            }
            if ("level2".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, FINAL);
                String expected = d.iteration() == 0 ? "<f:level2>" : "[99,instance type int]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                if (d.iteration() > 0) {
                    assertTrue(d.fieldAnalysis().getValue() instanceof MultiValue);
                }
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_1".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
        };

        testClass("E2Immutable_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_1_1() throws IOException {
        testClass("E2Immutable_1_1", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_2() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set3".equals(d.fieldInfo().name)) {
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_2".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "set3".equals(fr.fieldInfo.name)) {
                    assertEquals("new HashSet<>(set3Param)/*this.size()==set3Param.size()*/", d.currentValue().toString());
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_2".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        testClass("E2Immutable_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_3".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fieldReference &&
                    "strings4".equals(fieldReference.fieldInfo.name)) {
                String expected = d.iteration() == 0
                        ? "<vp:Set<String>:initial@Class_E2Immutable_3>" : "Set.copyOf(input4)";
                assertEquals(expected, d.currentValue().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                // Set<String>, E2 -> ER
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
                assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
            }

            if ("getStrings4".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr &&
                    "strings4".equals(fr.fieldInfo.name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                String linked = d.iteration() <= 1 ? "return getStrings4:0,this:-1" : "return getStrings4:0";
                assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
            }

            if ("mingle".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertEquals("input4:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "input4".equals(pi.name) && "0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
                if (d.variable() instanceof FieldReference fr && "strings4".equals(fr.fieldInfo.name) && "0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {

            if ("E2Immutable_3".equals(d.methodInfo().name)) {
                FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                VariableInfo vi = d.getFieldAsVariable(strings4);
                assert vi != null;
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, vi.getProperty(NOT_NULL_EXPRESSION));
            }

            if ("mingle".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                    VariableInfo vi = d.getFieldAsVariable(strings4);
                    assert vi != null;
                }
                // this method returns the input parameter
                assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(INDEPENDENT));

                // parameter input4
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
            }

            if ("getStrings4".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                    VariableInfo vi = d.getFieldAsVariable(strings4);
                    assert vi != null;
                }
                // method not null
                assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL_EXPRESSION);

                String expect = d.iteration() <= 1 ? "<m:getStrings4>" : "/*inline getStrings4*/strings4";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 1) {
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("strings4".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(FINAL));

                String expected = d.iteration() == 0 ? "<f:strings4>" : "Set.copyOf(input4)";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.CONTAINER_DV, EXTERNAL_CONTAINER);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            assertEquals(MultiLevel.MUTABLE_DV, set.typeAnalysis.get().getProperty(IMMUTABLE));
        };

        testClass("E2Immutable_3", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("E2Immutable_4", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_5() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map5".equals(d.fieldInfo().name)) {
                assertEquals("map5Param:3", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_5".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "map5".equals(fr.fieldInfo.name)) {
                    assertEquals("new HashMap<>(map5Param)/*this.size()==map5Param.size()*/", d.currentValue().toString());
                    assertEquals("map5Param:3", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_5".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("E2Immutable_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("E2Immutable_6", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                String expectValue = d.iteration() == 0 ? "<m:setI>" : "<no return value>";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getMap7".equals(d.methodInfo().name) && "incremented".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:HashMap<String,SimpleContainer>>"
                            : "new HashMap<>(map7)/*this.size()==map7.size()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? "this.map7:-1,this:-1" : "this.map7:3";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:HashMap<String,SimpleContainer>>"
                            : "new HashMap<>(map7)/*this.size()==map7.size()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setI".equals(d.methodInfo().name)) {
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, MODIFIED_METHOD);
                String expect = d.iteration() == 0 ? "<m:getI>" : "/*inline getI*/i$0";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("E2Immutable_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("E2Immutable_8", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }


    @Test
    public void test_9() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_9".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
        };

        testClass("E2Immutable_9", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "sub".equals(fieldReference.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 3 ? "<f:sub>" : "instance type Sub/*new Sub()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    String linked = d.iteration() <= 3 ? "return method:-1,sub.string:-1,this:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 2, DV.FALSE_DV, CONTEXT_MODIFIED);
                    assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                }
                if (d.variable() instanceof ReturnVariable) {
                    // no linked variables
                    String linked = d.iteration() <= 3 ? "sub.string:0,this.sub:-1,this:-1" : "sub.string:0";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Sub".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("sub".equals(d.fieldInfo().name)) {
                String expected = d.iteration() <= 2 ? "<f:sub>" : "new Sub()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
            }
        };

        // whether we get an error for a field not read depends on the analyser configuration
        // if we compute across the whole type, we don't raise the error
        testClass("E2Immutable_10", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_10_2() throws IOException {
        testClass("E2Immutable_10", 1, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }


    // variant on MethodReference_3, independent
    @Test
    public void test_11() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("firstEntry".equals(d.methodInfo().name)) {
                Expression v = d.evaluationResult().value();
                String expectValue = d.iteration() == 0 ? "<m:firstEntry>" : "map.firstEntry()";
                assertEquals(expectValue, v.toString());
                String expectLinked = d.iteration() == 0 ? "" : "this.map:3";
                assertEquals(expectLinked, v.linkedVariables(d.evaluationResult()).toString());
            }

            if ("stream".equals(d.methodInfo().name)) {
                Expression v = d.evaluationResult().value();
                String expectValue = d.iteration() == 0 ? "<m:of>" : "Stream.of(map.firstEntry())";
                assertEquals(expectValue, v.toString());
                String expectLinked = d.iteration() == 0 ? "" : "this.map:3";
                assertEquals(expectLinked, v.linkedVariables(d.evaluationResult()).toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_11".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        // 2x potential null pointer exception (empty map)
        testClass("E2Immutable_11", 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // variant on MethodReference_3, E2Immutable_11, E3Container
    // again, map.entry is not transparent
    @Test
    public void test_12() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                Expression v = d.evaluationResult().value();
                String expectValue = d.iteration() == 0 ? "<m:of>" : "Stream.of(map.firstEntry())";
                assertEquals(expectValue, v.toString());
                assertEquals("Type java.util.stream.Stream<java.util.Map.Entry<java.lang.String,T>>",
                        v.returnType().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_12".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        // 2x potential null ptr
        testClass("E2Immutable_12", 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // variant on MethodReference_3, E2Immutable_11
    // Map.Entry is still not transparent (we cannot simply exchange it for a type parameter)
    @Test
    public void test_13() throws IOException {

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_13".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        // 2x potential null ptr
        testClass("E2Immutable_13", 0, 1, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // See also ECInContext_0
    @Test
    public void test_14() throws IOException {

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_14".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("E2Immutable_14", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    /*
    Current difference between the two new Suffix() {} implementations: PARTIAL_IMMUTABLE
    Also cause of the exception
     */

    @Test
    public void test_15() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("suffix".equals(d.methodInfo().name)) {
                if ("VariableDefinedOutsideLoop".equals(d.methodInfo().typeInfo.simpleName)) {
                    if (d.variable() instanceof ReturnVariable) {
                        if ("0".equals(d.statementId())) {
                            String expected = d.iteration() <= 2 ? "<new:Suffix>" : "new Suffix(){}";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                            assertDv(d, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
                        }
                    }
                } else if ("E2Immutable_15".equals(d.methodInfo().typeInfo.simpleName)) {
                    if (d.variable() instanceof ReturnVariable) {
                        if ("0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<f:NO_SUFFIX>" : "E2Immutable_15.NO_SUFFIX";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 1, MultiLevel.MUTABLE_DV, IMMUTABLE);
                        }
                    }
                } else fail();
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_15".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            } else if ("Suffix".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            } else if ("VariableDefinedOutsideLoop".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
            } else if ("$1".equals(d.typeInfo().simpleName)) {
                // new Suffix() {}
                assertEquals("VariableDefinedOutsideLoop", d.typeInfo().packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, PARTIAL_IMMUTABLE);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
            } else if ("$2".equals(d.typeInfo().simpleName)) {
                // new Suffix() {}
                assertEquals("E2Immutable_15", d.typeInfo().packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            } else fail("type " + d.typeInfo());
        };
        testClass("E2Immutable_15", 0, 0, new DebugConfiguration.Builder()
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_16() throws IOException {

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_16".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
            if ("OneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE); // FIXME is this correct? should it not be @ERContainer, given no methods?
            }
            if ("Variable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("Record".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
            }
        };

        // 2x potential null ptr, 1x real error: @Modified on variable() is worse than implied @NotModified in interface
        testClass("E2Immutable_16", 1, 2, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
