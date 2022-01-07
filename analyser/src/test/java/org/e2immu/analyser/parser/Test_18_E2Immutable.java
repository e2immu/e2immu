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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_18_E2Immutable extends CommonTestRunner {
    public Test_18_E2Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isAbc".equals(d.methodInfo().name)) {
                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(Property.INDEPENDENT));
            }
            if ("E2Immutable_0".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d.p(1), 0, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
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
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_1".equals(d.methodInfo().name) &&
                    d.methodInfo().methodInspection.get().getParameters().size() == 2) {
                assertTrue(d.methodInfo().isConstructor);

                if (d.variable() instanceof FieldReference fr && "level2".equals(fr.fieldInfo.name)
                        && fr.scopeIsThis()) {
                    if ("1".equals(d.statementId())) {
                        // we never know in the first iteration...
                        String expectValue = d.iteration() == 0
                                ? "2+<field:org.e2immu.analyser.testexample.E2Immutable_1.level2#parent2Param>"
                                : "2+parent2Param.level2";
                        assertEquals(expectValue, d.currentValue().debugOutput());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && pi.name.equals("parent2Param")) {
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };

        testClass("E2Immutable_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
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
                    assertEquals("this.set3:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_2".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        testClass("E2Immutable_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("strings4".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                        d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));

                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.CONTAINER));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {

            if ("E2Immutable_3".equals(d.methodInfo().name)) {
                FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                VariableInfo vi = d.getFieldAsVariable(strings4);
                assert vi != null;
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, vi.getProperty(Property.NOT_NULL_EXPRESSION));
            }

            if ("mingle".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                    VariableInfo vi = d.getFieldAsVariable(strings4);
                    assert vi != null;

                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                            vi.getProperty(Property.NOT_NULL_EXPRESSION));
                }
                // this method returns the input parameter
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);

                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(Property.INDEPENDENT));
            }

            if ("getStrings4".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    FieldInfo strings4 = d.methodInfo().typeInfo.getFieldByName("strings4", true);
                    VariableInfo vi = d.getFieldAsVariable(strings4);
                    assert vi != null;
                }
                // method not null
                assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);

                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("strings4", d.methodAnalysis().getSingleReturnValue().toString());
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2Immutable_3".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fieldReference &&
                    "strings4".equals(fieldReference.fieldInfo.name)) {
                assertEquals("Set.copyOf(input4)", d.currentValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                // Set<String>, E2 -> ER
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);

                assertEquals("this.strings4:0", d.variableInfo().getLinkedVariables().toString());
            }

            if ("getStrings4".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr &&
                    "strings4".equals(fr.fieldInfo.name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertEquals("return getStrings4:0,this.strings4:0", d.variableInfo().getLinkedVariables().toString());
            }

            if ("mingle".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertEquals("input4:0,return mingle:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "input4".equals(pi.name) && "0".equals(d.statementId())) {
                    assertEquals("input4:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "strings4".equals(fr.fieldInfo.name) && "0".equals(d.statementId())) {
                    assertEquals("this.strings4:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            assertEquals(MultiLevel.MUTABLE_DV, set.typeAnalysis.get().getProperty(Property.IMMUTABLE));
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
                    assertEquals("map5Param:3,this.map5:0", d.variableInfo().getLinkedVariables().toString());
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
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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
                String expectValue = d.iteration() <= 1 ? "<m:setI>" : "<no return value>";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getMap7".equals(d.methodInfo().name) && "incremented".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:HashMap<String,SimpleContainer>>"
                            : "new HashMap<>(map7)/*this.size()==map7.size()*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? "incremented:0,this.map7:-1" : "incremented:0,this.map7:3";
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
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("i$0", d.methodAnalysis().getSingleReturnValue().toString());
                }
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
                .build());
    }


    @Test
    public void test_9() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("E2Immutable_9".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("E2Immutable_9", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "sub".equals(fieldReference.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 2 ? "<f:sub>" : "new Sub()";
                    assertEquals(expectValue, d.currentValue().toString());

                    // no linked variables, but initially delayed
                    String expectLinked = d.iteration() <= 2 ? "*" : "";
                    assertEquals("this.sub:0", d.variableInfo().getLinkedVariables().toString());

                    //int expectBreakDelay = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    //assertEquals(expectBreakDelay, d.getProperty(Property.EXTERNAL_IMMUTABLE_BREAK_DELAY));

                    assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
                if (d.variable() instanceof ReturnVariable) {
                    // no linked variables
                    assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                    assertEquals("return method:0,this.sub.string:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Sub".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("sub".equals(d.fieldInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.fieldAnalysis().getValue());
                } else {
                    assertEquals("new Sub()", d.fieldAnalysis().getValue().toString());
                }
            }
        };

        testClass("E2Immutable_10", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    // variant on MethodReference_3, independent
    @Test
    public void test_11() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("firstEntry".equals(d.methodInfo().name)) {
                Expression v = d.evaluationResult().value();
                String expectValue = d.iteration() == 0 ? "<m:firstEntry>" : "map.firstEntry()";
                assertEquals(expectValue, v.toString());
                String expectLinked = d.iteration() == 0 ? "this.map:-1" : "this.map:3";
                assertEquals(expectLinked, v.linkedVariables(d.evaluationResult().evaluationContext()).toString());
            }

            if ("stream".equals(d.methodInfo().name)) {
                Expression v = d.evaluationResult().value();
                String expectValue = d.iteration() == 0 ? "<m:of>" : "Stream.of(map.firstEntry())";
                assertEquals(expectValue, v.toString());
                String expectLinked = d.iteration() == 0 ? "this.map:-1" : "this.map:3";
                assertEquals(expectLinked, v.linkedVariables(d.evaluationResult().evaluationContext()).toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
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
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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
                String expectLinked1 = d.iteration() == 0 ? "?" : "this.map";
                //      assertEquals(expectLinked1, d.evaluationResult().evaluationContext().linked1Variables(v).toString());
                assertEquals("Type java.util.stream.Stream<java.util.Map.Entry<java.lang.String,T>>", v.returnType().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
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
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
