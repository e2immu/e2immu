
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.ComputingFieldAnalyser;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_2 extends CommonTestRunner {

    private static final String TYPE = "org.e2immu.analyser.parser.basics.testexample.Basics_2";
    private static final String STRING_PARAMETER = TYPE + ".setString(String):0:string";
    private static final String STRING_FIELD = TYPE + ".string";

    private static final String THIS = TYPE + ".this";
    private static final String COLLECTION = TYPE + ".add(java.util.Collection<String>):0:collection";
    private static final String ADD = TYPE + ".add(java.util.Collection<String>)";

    public Test_00_Basics_2() {
        super(true);
    }

    @Test
    public void test_2() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.iteration() == 0) {
                Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                        ComputingFieldAnalyser.EVALUATE_INITIALISER,
                        ComputingFieldAnalyser.ANALYSE_CONTAINER));
                assertSubMap(expect, d.statuses());
            }
            if (d.iteration() == 1) {
                Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                        ComputingFieldAnalyser.EVALUATE_INITIALISER,
                        ComputingFieldAnalyser.ANALYSE_CONTAINER,
                        ComputingFieldAnalyser.ANALYSE_FINAL,
                        ComputingFieldAnalyser.ANALYSE_FINAL_VALUE,
                        ComputingFieldAnalyser.ANALYSE_NOT_NULL));
                assertSubMap(expect, d.statuses());
            }
            if ("string".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
                assertEquals("string:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo c && "collection".equals(c.simpleName()) && "0".equals(d.statementId())) {
                    assertEquals(COLLECTION, d.variableName());

                    if (d.iteration() == 0) {
                        assertEquals("0" + Stage.EVALUATION, d.variableInfo().getReadId());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertEquals("<p:collection>", d.currentValue().toString());
                        assertTrue(d.currentValue().isDelayed());
                    }
                    assertEquals("nullable instance type Collection<String>/*@Identity*/",
                            d.variableInfoContainer().getPreviousOrInitial().getValue().toString());

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertEquals(DV.TRUE_DV, d.getProperty(CONTEXT_MODIFIED));
                    assertTrue(d.properties().containsKey(CNN_TRAVELS_TO_PRECONDITION));
                    // cannot be content linked to string, because string is recursively immutable
                    assertLinked(d, it(0, "this:4"));
                }
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertEquals(STRING_FIELD, d.variableName());

                    assertCurrentValue(d, 1,
                            "initial:this.string@Method_add_0-C;initial@Field_string",
                            "nullable instance type String");
                    // string occurs in a not-null context, but one of the values is nullable
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertFalse(d.properties().containsKey(CNN_TRAVELS_TO_PRECONDITION));
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    assertDv(d, DV.FALSE_DV, CONTEXT_MODIFIED);
                }
            }
            if ("setString".equals(d.methodInfo().name)) {
                if (STRING_FIELD.equals(d.variableName())) {
                    assertTrue(d.variableInfo().isAssigned());
                    assertEquals("string:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getString".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                }
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertTrue(d.variableInfo().isRead());
                    String expectValue = d.iteration() == 0 ? "<f:string>" : "nullable instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<f:string>" : "string$0";
                    assertEquals(expectValue, d.currentValue().toString());
                    if (d.iteration() == 1) {
                        if (d.currentValue() instanceof VariableExpression ve) {
                            if (ve.getSuffix() instanceof VariableExpression.VariableField vf) {
                                assertEquals(0, vf.statementTime());
                                assertNull(vf.assignmentId());
                            } else fail();
                        } else fail();
                    }
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (TYPE.equals(d.methodInfo().typeInfo.fullyQualifiedName)) {
                FieldInfo string = d.methodInfo().typeInfo.getFieldByName("string", true);
                VariableInfo fieldAsVariable = d.getFieldAsVariable(string);

                if ("getString".equals(d.methodInfo().name)) {
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);

                    assert fieldAsVariable != null;
                    assertTrue(fieldAsVariable.isRead());
                    assertDv(d, DV.FALSE_DV, MODIFIED_METHOD);
                }
                if ("setString".equals(d.methodInfo().name)) {
                    assert fieldAsVariable != null;
                    assertTrue(fieldAsVariable.isAssigned());
                    assertEquals(DV.FALSE_DV, fieldAsVariable.getProperty(CONTEXT_MODIFIED));
                }
                if ("add".equals(d.methodInfo().name)) {
                    assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    assertDv(d, DV.FALSE_DV, MODIFIED_METHOD);
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (d.methodInfo().name.equals("setString") && "0".equals(d.statementId())) {
                assertTrue(d.haveMarkRead(STRING_PARAMETER), d.evaluationResult().toString());
                assertTrue(d.haveMarkRead(THIS));

                EvaluationResult.ChangeData expressionChange = d.findValueChange(STRING_FIELD);
                assertEquals("string", expressionChange.value().toString());

                EvaluationResult.ChangeData cd = d.findValueChange(STRING_FIELD);
                assertEquals("string:0", cd.linkedVariables().toString());
                assertEquals("string", d.evaluationResult().value().toString());
            }
            if (d.methodInfo().name.equals("getString") && "0".equals(d.statementId()) && d.iteration() == 0) {
                assertTrue(d.haveMarkRead(STRING_FIELD));
                assertTrue(d.haveMarkRead(THIS));
            }
            if (d.methodInfo().name.equals("add") && "0".equals(d.statementId())) {
                String expectEvalString = d.iteration() == 0 ? "<m:add>" : "instance type boolean";
                assertEquals(expectEvalString, d.evaluationResult().value().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            // check that the XML annotations have been read properly, and copied into the correct place
            TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo();
            assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, stringType.typeAnalysis.get().getProperty(IMMUTABLE));

            TypeInfo collection = typeMap.get(Collection.class);
            MethodInfo add = collection.findUniqueMethod("add", 1);
            ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    p0.parameterAnalysis.get().getProperty(NOT_NULL_PARAMETER));
            assertEquals(DV.FALSE_DV, p0.parameterAnalysis.get().getProperty(MODIFIED_VARIABLE));
            assertEquals(DV.TRUE_DV, add.methodAnalysis.get().getProperty(MODIFIED_METHOD));
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (ADD.equals(d.methodInfo().fullyQualifiedName)) {
                assertTrue(d.state().isBoolValueTrue());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        testClass("Basics_2", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_2b() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                assertNotNull(d.haveError(Message.Label.FIELD_INITIALIZATION_NOT_NULL_CONFLICT));
            }
        };

        // conflicting @NotNull requirements
        testClass("Basics_2", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)
                .build());
    }
}
