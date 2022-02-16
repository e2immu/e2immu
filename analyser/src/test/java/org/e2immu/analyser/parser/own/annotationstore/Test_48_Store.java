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

package org.e2immu.analyser.parser.own.annotationstore;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_48_Store extends CommonTestRunner {

    public Test_48_Store() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass(List.of("Project_0", "Store_0"), 1, 15, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo mapEntry = typeMap.get(Map.Entry.class);
            MethodInfo getValue = mapEntry.findUniqueMethod("getValue", 0);
            assertTrue(getValue.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD).isDelayed());
            assertTrue(getValue.isAbstract());
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("entry".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        // EVAL level
                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        String expectLinks = d.iteration() == 0 ? "?" : "";
                        assertEquals(expectLinks, eval.getLinkedVariables().toString());
                        String expectValue = d.iteration() == 0 ? "<v:entry>" : "nullable instance type Entry<String,Object>";
                        assertEquals(expectValue, eval.getValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        testClass("Store_1", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1.0.0.0.0.0.0.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "countRemoved" : "countRemoved,countRemoved$1.0.0";
                assertEquals(expect, d.evaluationResult().changeData().keySet().stream()
                        .map(Variable::fullyQualifiedName).sorted().collect(Collectors.joining(",")));
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("countRemoved".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("1.0.0.0.0.0.0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        String expect = d.iteration() == 0 ? "1+<v:countRemoved>" : "1+countRemoved$1.0.0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Store_2", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("project".equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? "<m:getOrCreate>" : "instance type Project_0";
                    assertEquals(expectValue, d.currentValue().toString());

                    // it 1: Store_3 is still immutable delayed
                    String expectLinked = d.iteration() == 0 ? "?" : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof This) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getOrCreate".equals(d.methodInfo().name)) {
                // modified, because .get() is modifying (there is no annotated API)
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);

                // dependent, because only independent if non-modifying (current rule, we may want to get rid of this)
                assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);

                if (d.iteration() == 0) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    Expression value = d.methodAnalysis().getSingleReturnValue();
                    assertEquals("newProject", value.toString());
                    assertTrue(value instanceof VariableExpression, "Have " + value.getClass());
                }
            }
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("projects".equals(d.fieldInfo().name) && "Store_3".equals(d.fieldInfo().owner.simpleName)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Store_3".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass(List.of("Project_0", "Store_3"), 3, 11, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_4() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("flexible".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "LOGGER".equals(fr.fieldInfo.name)) {
                    // OK in 0, 1.0.0, 1.1.0, problem in 1
                    String expectValue = d.iteration() == 0 ? "<f:LOGGER>" : "nullable instance type Logger";
                    assertEquals(expectValue, d.currentValue().toString(), "At statement " + d.statementId());
                }
            }
        };
        testClass("Store_4", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_5() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("flexible".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo object && "object".equals(object.name)) {
                    String expectValue = d.iteration() == 0 ? "<p:object>" : "nullable instance type Object/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if ("s".equals(d.variableName())) {
                    if ("2".equals(d.statementId()) || "3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:toString>" : "object.toString()";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Store_5", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("config".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("ar.result()", d.currentValue().toString());
                        assertTrue(d.variableInfo().valueIsSet());
                        assertTrue(d.currentValue() instanceof MethodCall);
                    }
                }
                if ("port".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        fail("Variable should not exist here");
                    }
                }
            }
        };

        testClass("Store_6", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    used to catch 3 bugs
    - check for null constant in method call modification
    - @Fluent
    - infinite delay loop
     */
    @Test
    public void test_7() throws IOException {

        // transparent types have nothing to do with this, given that there is only one field, of type int
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Store_7".equals(d.typeInfo().simpleName)) {
                assertEquals("[Type param E]", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Store_7", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_8() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("entry".equals(d.variableName())) {
                    assertNotEquals("2", d.statementId());
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable);
                    String expectValue = "nullable instance type Entry<String,Object>";
                    assertEquals(expectValue, d.currentValue().toString(), d.statementId());
                }
                if ("countUpdated".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("0", d.currentValue().toString());
                    }
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:countUpdated>" : "1+countUpdated$1";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals(d.iteration() == 0, d.currentValue().isDelayed());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "!projectName.equals(entry.getValue()/*(String)*/)||null==projectName?1+<v:countUpdated>:0"
                                : "!projectName.equals(entry.getValue()/*(String)*/)||null==projectName?1+countUpdated$1:0";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals(d.iteration() == 0, d.currentValue().isDelayed());
                    }
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "(projectName.equals((nullable instance type Entry<String,Object>).getValue()/*(String)*/)||body.entrySet().isEmpty())&&(body.entrySet().isEmpty()||null!=projectName)?0:1+<v:countUpdated>"
                                : "(projectName.equals((nullable instance type Entry<String,Object>).getValue()/*(String)*/)||body.entrySet().isEmpty())&&(body.entrySet().isEmpty()||null!=projectName)?0:1+countUpdated$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass(List.of("Project_0", "Store_8"), 3, 17, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

}
