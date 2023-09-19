
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.FirstThen;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Support_04_FirstThen extends CommonTestRunner {

    public Test_Support_04_FirstThen() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 2 ? "<null-check>" : "null==first";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String linked = d.iteration() < 2 ? "this.first:-1,this:-1" : "";
                    assertEquals(linked, d.evaluationResult().value().linkedVariables(d.evaluationResult()).toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("equals".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.state().toString());
            }
            if ("set".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    String expectCondition = switch (d.iteration()) {
                        case 0, 1 -> "<null-check>";
                        default -> "null==first";
                    };
                    assertEquals(expectCondition, d.condition().toString());
                    String list = d.iteration() < 2 ? "[<f:first>=null]" : "[<f:first>=null, first=null]";
                    assertEquals(list, d.statementAnalysis().stateData()
                            .equalityAccordingToStateStream().map(Object::toString).sorted().toList().toString());
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expectPre = switch (d.iteration()) {
                        case 0, 1, 2 -> "!<null-check>";
                        default -> "null!=then";
                    };
                    assertEquals(expectPre, d.statementAnalysis().stateData().getPrecondition().expression().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getFirst".equals(d.methodInfo().name) && "FirstThen.this.first".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                }
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isRead());
                }
            }
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "first".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "null");
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String value = switch (d.iteration()) {
                            case 0 -> "<f:first>";
                            case 1 ->
                                    "<vp:first:initial:this.first@Method_set_1.0.0-C;state:this.first@Method_set_1.0.2-E;values:this.first@Field_first>";
                            default -> "nullable instance type S";
                        };
                        assertEquals(value, eval.getValue().toString());
                        assertEquals("", eval.getLinkedVariables().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        String mergeValue = switch (d.iteration()) {
                            case 0 -> "<f:first>";
                            case 1 -> "<wrapped:first>";
                            default -> "nullable instance type S";
                        };
                        assertEquals(mergeValue, d.variableInfo().getValue().toString());
                        //       assertEquals("this:-1", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasMerge());
                        // cause of EVAL: the state, first==null
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        //      assertEquals("this:-1", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertSame(ParameterizedType.NULL_CONSTANT, d.currentValue().returnType());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("equals".equals(d.methodInfo().name) && "o".equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    assertEquals(DV.FALSE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("first".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;

            if ("set".equals(name)) {
                String expect = switch (d.iteration()) {
                    case 0, 1 -> "Precondition[expression=!<null-check>, causes=[escape]]";
                    default -> "Precondition[expression=null!=first, causes=[escape]]";
                };
                assertEquals(expect, d.methodAnalysis().getPrecondition().toString());
            }

            if ("getFirst".equals(name)) {
                FieldInfo first = d.methodInfo().typeInfo.getFieldByName("first", true);
                VariableInfo vi = d.getFieldAsVariable(first);
                assert vi != null;
                assertTrue(vi.isRead());
            }

            if ("hashCode".equals(name)) {
                FieldInfo first = d.methodInfo().typeInfo.getFieldByName("first", true);
                VariableInfo vi = d.getFieldAsVariable(first);
                assert vi != null;
                assertTrue(vi.isRead());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);

            }

            if ("equals".equals(name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            assertEquals("Type param S,Type param T", d.typeAnalysis().getHiddenContentTypes().types()
                    .stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
            assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsStatus(false).isDone());
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo objects = typeMap.get(Objects.class);
            MethodInfo hash = objects.typeInspection.get().methods().stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
            ParameterInfo objectsParam = hash.methodInspection.get().getParameters().get(0);
            assertEquals(DV.FALSE_DV, objectsParam.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));
        };

        testSupportAndUtilClasses(List.of(FirstThen.class), 0, 0, new DebugConfiguration.Builder()
          //      .addEvaluationResultVisitor(evaluationResultVisitor)
           //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
           //     .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
           //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
           //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
           //     .addStatementAnalyserVisitor(statementAnalyserVisitor)
            //    .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
