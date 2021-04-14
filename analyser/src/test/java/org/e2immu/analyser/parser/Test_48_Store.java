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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Test_48_Store extends CommonTestRunner {

    public Test_48_Store() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass(List.of("Project_0", "Store_0"), 1, 7, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo mapEntry = typeMap.get(Map.Entry.class);
            MethodInfo getValue = mapEntry.findUniqueMethod("getValue", 0);
            assertEquals(Level.DELAY, getValue.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            assertTrue(getValue.isAbstract());
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("entry".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        // EVAL level
                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        String expectLinks = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectLinks, eval.getLinkedVariables().toString());
                        String expectValue = d.iteration() == 0 ? "<v:entry>" : "instance type Entry<String,Object>";
                        assertEquals(expectValue, eval.getValue().toString());
                        assertEquals(Level.TRUE, eval.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, eval.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        testClass("Store_1", 0, 3, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1.0.0.0.0.0.0.0.0".equals(d.statementId())) {
                assertEquals(d.iteration() == 0 ? 1 : 2, d.evaluationResult().changeData().size());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("countRemoved".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("1.0.0.0.0.0.0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if ("countRemoved$1.0.0".equals(d.variableName())) {
                    if ("1.0.0.0.0.0.0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
        };
        testClass("Store_2", 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("handleMultiSet".equals(d.methodInfo().name)) {
                if ("project".equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? "<m:getOrCreate>" : "this.getOrCreate()";
                    assertEquals(expectValue, d.currentValue().toString());

                    // it 1: Store_3 is still immutable delayed
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if(d.variable() instanceof This) {
                    int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getOrCreate".equals(d.methodInfo().name)) {
                // modified, because .get() is modifying (there is no annotated API)
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                // dependent, because only independent if non-modifying (current rule, we may want to get rid of this)
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.DEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));

                if (d.iteration() == 0) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    Expression value = d.methodAnalysis().getSingleReturnValue();
                    assertEquals("newProject", value.toString());
                    assertTrue(value instanceof VariableExpression, "Have " + value.getClass());
                }
            }
            if("handleMultiSet".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("projects".equals(d.fieldInfo().name) && "Store_3".equals(d.fieldInfo().owner.simpleName)) {
                int expectMom = d.iteration() == 0 ? Level.DELAY: Level.TRUE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if("Store_3".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass(List.of("Project_0", "Store_3"), 5, 11, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }
}
