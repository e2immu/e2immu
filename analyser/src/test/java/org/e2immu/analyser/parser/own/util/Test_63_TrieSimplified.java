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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_63_TrieSimplified extends CommonTestRunner {

    public Test_63_TrieSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        assertNotNull(fr.scopeVariable);
                        if ("1.0.0".equals(d.statementId())) {
                            fail("Does not exist here");
                        }
                        if ("1.0.1".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "<null-check>?new HashMap<>():<f:node.map>";
                                case 1, 2, 3 -> "null==<f:node.map>?new HashMap<>():<f:node.map>";
                                default -> "new HashMap<>()";
                            };
                            assertEquals(expected, d.currentValue().toString());
                            String linked = d.iteration() < 4
                                    ? "newTrieNode:-1,node:-1,s:-1,this.root:-1,this:-1"
                                    : "node:2,this.root:2,this:3";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                            assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("1.0.1.1.0".equals(d.statementId())) {
                            assertEquals("<f:node.map>", d.currentValue().toString());
                            assertEquals("newTrieNode:-1,node:-1,s:-1,this.root:-1,this:-1",
                                    d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("1.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1, 2, 3 -> "null==<f:node.map>";
                        default -> "true";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpressionGet().toString());
                }
                if ("1.0.1.1.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_add_1.0.1-C";
                        case 1, 2, 3 -> "wait_for_modification:node@Method_add_1-E";
                        default -> "link@Field_root";
                    };
                    // remains delayed, becomes unreachable
                    assertEquals(expected, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S--S--", d.delaySequence());
        // null ptr warning
        testClass("TrieSimplified_0", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }


    @Test
    public void test_0bis() throws IOException {
        testClass("TrieSimplified_0", 2, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    // there should be no null ptr warnings
    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0.1.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() < 3 ? "<m:get>" : "root.map$0.get(s)";
                    assertEquals(expectCondition, d.evaluationResult().value().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String expectCondition = d.iteration() < 3 ? "<m:put>" : "nullable instance type TrieNode<T>";
                    assertEquals(expectCondition, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0.1.0".equals(d.statementId())) {
                    String expectCondition = switch (d.iteration()) {
                        case 0 -> "!<null-check>";
                        case 1, 2 -> "null!=<f:root.map>";
                        default -> "null!=root.map$0";
                    };
                    assertEquals(expectCondition, d.condition().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String expectCondition = switch (d.iteration()) {
                        case 0 -> "!<null-check>&&<null-check>";
                        case 1, 2 -> "null!=<f:root.map>&&<null-check>";
                        default -> "null!=root.map$0&&null==root.map$0.get(s)";
                    };
                    assertEquals(expectCondition, d.absoluteState().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                String expected = d.iteration() < 2 ? "<f:root>" : "instance type TrieNode<T>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 3 ? "<m:add>" : "root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("------", d.delaySequence());

        testClass("TrieSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // without the getter and setter
    @Test
    public void test_1_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1 -> "null==<f:root.map>";
                        default -> "true";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        // IMPORTANT! branch 0.1 is blocked, and map is never modified
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.1.1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "newTrieNode:-1,root.map:-1,s:-1,this:-1";
                            case 2 -> "root.map:-1,s:-1";
                            default -> "we're not reaching this iteration anymore";
                        };
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                        assertTrue(d.iteration() <= 2);
                    }
                }
                if ("newTrieNode".equals(d.variableName())) {
                    if ("0.1.0".equals(d.statementId())) {
                        assertEquals("<m:get>", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0.1.0".equals(d.statementId())) {
                    String flowData = switch (d.iteration()) {
                        case 0 -> "initial:this.root@Method_add_0-C";
                        case 1 -> "initial@Field_root";
                        case 2 -> "link@Field_root";
                        default -> "FlowDataImpl.ALWAYS";
                    };
                    assertEquals(flowData, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String flowData = switch (d.iteration()) {
                        case 0 -> "initial:this.root@Method_add_0-C";
                        case 1 -> "initial:this.root@Method_add_0-C;initial@Field_root";
                        case 2 -> "initial:this.root@Method_add_0-C;initial@Field_root;link@Field_root";
                        default -> "we're not reaching this iteration anymore";
                    };
                    assertEquals(flowData, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                String expected = d.iteration() == 0 ? "<f:root>" : "new TrieNode<>()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 2 ? "<m:add>" : "root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("addSynchronized".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_1_2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("TrieSimplified_1_2", 6, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_1_2bis() throws IOException {
        testClass("TrieSimplified_1_2", 6, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    // see also test_4; difference: before introduction of "Inspector", TrieNode was not analysed, since it has no statements
    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("data".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ_IN_PRIMARY_TYPE));
                }
            }
            if ("map".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ_IN_PRIMARY_TYPE));
                }
            }
        };
        testClass("TrieSimplified_2", 2, 2, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<null-check>" : "true";
                    assertEquals(expectValue, d.evaluationResult().getExpression().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 ->
                                "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                        case 1 -> "-1-(instance type int)+upToPosition>=0?null:<f:root>";
                        default -> "-1-(instance type int)+upToPosition>=0?null:root";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectState = d.iteration() == 0 ? "!<null-check>" : "false";
                    assertEquals(expectState, d.state().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 1, d.statementAnalysis().flowData().isUnreachable());
                    String reachable = d.iteration() == 0 ? "initial_flow_value@Method_goTo_1.0.1-C" : "NEVER:0";
                    assertEquals(reachable, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 1, d.statementAnalysis().flowData().isUnreachable());
                    String reachable = d.iteration() == 0 ? "initial_flow_value@Method_goTo_1.0.2-C" : "NEVER:0";
                    assertEquals(reachable, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock().toString());
                }
                if ("1.0.2.0.0".equals(d.statementId())) {
                    assertEquals(0, d.iteration());
                }
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "CM{state=(!<loopIsNotEmptyCondition>||!<null-check>)&&(!<loopIsNotEmptyCondition>||!<null-check>);ignore=node;parent=CM{}}"
                            : "CM{state=instance type int>=upToPosition;ignore=node;parent=CM{}}";
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "(!<loopIsNotEmptyCondition>||!<null-check>)&&(!<loopIsNotEmptyCondition>||!<null-check>)"
                            : "instance type int>=upToPosition";
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "map".equals(fieldReference.fieldInfo.name)) {
                    assertEquals("node", fieldReference.scope.toString());
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:node.map>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());

                        if (d.iteration() >= 2) {
                            VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                            assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    // 1.0.0 becomes the last statement in the block by iteration 2
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expectValue = d.iteration() == 0 ? "<f:node.map>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expectValue = d.iteration() == 0 ? "<f:node.map>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        if (d.iteration() >= 1) assertTrue(d.currentValue() instanceof NullConstant);
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference && "root".equals(fieldReference.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:root>" : "instance type TrieNode<T>/*new TrieNode<>()*/";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertLinked(d, it(0, "node:0,this:3"));
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:root>" : "root";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            case 1 ->
                                    "-1-(instance type int)+upToPosition>=0?nullable instance type TrieNode<T>:<f:root>";
                            default -> "-1-(instance type int)+upToPosition>=0?nullable instance type TrieNode<T>:root";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0, 1 -> "<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            default -> "-1-(instance type int)+upToPosition>=0?nullable instance type TrieNode<T>:root";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        // FIXME this is wrong, can we live with it?
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "<null-check>?<vp::container@Class_TrieNode>:<return value>"
                                : "null";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "<loopIsNotEmptyCondition>?<null-check>?<vp::container@Class_TrieNode>:<null-check>?<vp::container@Class_TrieNode>:<return value>:<return value>"
                                : "-1-(instance type int)+upToPosition>=0?null:<return value>";
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 ->
                                    "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            case 1 -> "-1-(instance type int)+upToPosition>=0?null:<f:root>";
                            default -> "-1-(instance type int)+upToPosition>=0?null:root";
                        };
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                String expected = d.iteration() == 0 ? "<f:root>" : "new TrieNode<>()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("null", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("goTo".equals(d.methodInfo().name) && n == 1) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 2 ? "<m:goTo>"
                        : "this.goTo(strings,strings.length)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("goTo".equals(d.methodInfo().name) && n == 2) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 2 ? "<m:goTo>"
                        : "-1-(instance type int)+upToPosition>=0?null:root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_3".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("TrieSimplified_3", 2, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_3_1() throws IOException {
        testClass("TrieSimplified_3", 2, 0, new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    // added some code to TrieNode test 2
    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    //    assertEquals("<vl:node>", d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() < 3 ? "<f:root>" : "instance type TrieNode<T>/*new TrieNode<>()*/";
                    assertEquals(expect, d.currentValue().toString());

                    // here, ENN is computed in the group 'root', 'node'
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);

                    }
                    // from here on, the return variable is part of the equivalence group
                    // it only receives a value and proper links from iteration 3 onward
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }

                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() < 3 ? "<f:root>" : "root";
                        assertEquals(expect, d.currentValue().toString(), "statement " + d.statementId());
                        assertLinked(d, it(0, "this.root:0,this:3"));
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        // IMPORTANT: the variable is not read in the loop, but we cannot know that in iteration 0
                        // it therefore must participate in the delay scheme, SAApply.setValueForVariablesInLoopDefinedOutsideAssignedInside
                        assertLinked(d, it0("this.root:0,this:-1"), it(1, "this.root:0,this:3"));
                        String expect = switch (d.iteration()) {
                            case 0 -> "<vl:node>";
                            case 1, 2 -> "<f:root>";
                            default -> "root";
                        };
                        assertEquals(expect, d.currentValue().toString(), "statement " + d.statementId());
                    }
                    if ("2".equals(d.statementId())) {
                        assertLinked(d, it(0, 2, "this.root:0,this:-1"), it(3, "this.root:0,this:3"));
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<vl:node>";
                            case 1, 2 -> "<f:root>";
                            default -> "root";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                String expected = d.iteration() < 2 ? "<f:root>" : "new TrieNode<>()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("TrieSimplified_4", 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    private static final String NODE_DATA = "null==(strings.length>=1?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):root).data$3?instance type TrieNode<T>:strings.length>=1?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):root";

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.0.1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "data:-1,node.map:-1,node:-1,this.root:-1";
                            case 1 -> "node.map:-1,node:-1,this.root:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "data:-1,node.map:-1,node:-1,this.root:-1";
                            case 1 -> "node.map:-1,node:-1,this.root:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "data".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("s".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                        assertFalse(d.variableInfoContainer().hasMerge());
                        assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                    // Trail 8 -- things go wrong in the 1.0.1 blocks
                    if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.0.1".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    }
                    // Trail 9 -- specifically, here!  node.map.put(s, newTrieNode);
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        String value = d.iteration() < 2 ? "<v:s>" : "nullable instance type String";
                        assertEquals(value, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 ->
                                    "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1.0.2-C;initial:s@Method_add_1.0.1.0.2-E";
                            case 1 ->
                                    "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.0.2-E;link@Field_map";
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        String value = d.iteration() < 2 ? "<v:s>" : "nullable instance type String";
                        assertEquals(value, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String value = d.iteration() < 2 ? "<v:s>" : "nullable instance type String";
                        assertEquals(value, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                }
                if ("node".equals(d.variableName())) {
                    String value = switch (d.iteration()) {
                        case 0, 1 -> "<f:root>";
                        case 2 -> "<vp:root:link@Field_root>";
                        default -> "root";
                    };
                    if ("0".equals(d.statementId())) {
                        assertEquals(value, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1 -> "initial@Field_root";
                            case 2 -> "link@Field_root";
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                        assertEquals("this.root:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        // String value = d.iteration() <= 1 ? "<f:root>" : "root";
                        assertEquals(value, prev.getValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1 -> "initial@Field_root";
                            case 2 -> "link@Field_root";
                            default -> "";
                        };
                        assertEquals(expected, prev.getValue().causesOfDelay().toString());

                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectEval = d.iteration() == 0 ? "<vl:node>" : "nullable instance type TrieNode<T>";
                        assertEquals(expectEval, eval.getValue().toString());
                        String expectedEval = d.iteration() == 0 ? "wait_for_assignment:node@Method_add_1-E" : "";
                        assertEquals(expectedEval, eval.getValue().causesOfDelay().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        String merge = switch (d.iteration()) {
                            case 0 ->
                                    "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                            case 1 ->
                                    "strings.length>=1?null==<vp:map:link@Field_map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                            case 2 ->
                                    "strings.length>=1?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):<vp:root:link@Field_root>";
                            default ->
                                    "strings.length>=1?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):root";
                        };
                        assertEquals(merge, d.currentValue().toString());
                        assertEquals(d.iteration() >= 3, d.currentValue().isDone());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        String current = switch (d.iteration()) {
                            case 0, 1 -> "<vl:node>";
                            default ->
                                    "null==node$1.map$0?instance type TrieNode<T>:nullable instance type TrieNode<T>";
                        };
                        assertEquals(current, d.currentValue().toString());
                    }
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        String val = switch (d.iteration()) {
                            case 0, 1 -> "<vl:node>";
                            default -> "instance type TrieNode<T>";
                        };
                        assertEquals(val, d.currentValue().toString());
                    }
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    String expected = switch (d.iteration()) {
                        case 0 ->
                                "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                        case 1 ->
                                "null==<f:node.data>?strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>:strings.length>=1?null==<vp:map:link@Field_map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                        case 2 ->
                                "null==<f:node.data>?strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>:strings.length>=1?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):<vp:root:link@Field_root>";
                        default -> NODE_DATA;
                    };
                    if ("2".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 3) assertTrue(d.currentValue().isDone());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String v = switch (d.iteration()) {
                            case 0, 1, 2 ->
                                    "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                            default -> "instance type TrieNode<T>";
                        };
                        assertEquals(v, d.currentValue().toString());
                        if (d.iteration() >= 3) assertTrue(d.currentValue().isDone());
                    }
                    String expected3 = switch (d.iteration()) {
                        case 0, 1, 2 ->
                                "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                        default -> NODE_DATA;
                    };
                    if ("3".equals(d.statementId())) {
                        assertEquals(expected3, d.currentValue().toString());
                        if (d.iteration() >= 3) assertTrue(d.currentValue().isDone());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(expected3, d.currentValue().toString());
                        if (d.iteration() >= 3) assertTrue(d.currentValue().isDone());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("newTrieNode".equals(d.variableName())) {
                    DV nne = d.getProperty(Property.NOT_NULL_EXPRESSION);
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, nne);
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.1.1.1.0.0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, nne);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("node$1".equals(d.variableName())) {
                    fail(); // this does not exist! only VE's have $1
                }
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("2.0.0".equals(d.statementId())) {
                            assertEquals("new LinkedList<>()/*0==this.size()*/", d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        }
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 3, "null==nullable instance type List<T>?new LinkedList<>()/*0==this.size()*/:nullable instance type List<T>");
                            assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION); // FIXME this is wrong
                        }
                    } else fail("Have scope " + fr.scope + " in iteration " + d.iteration() + ", " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if (fr.scopeVariable != null && "node".equals(fr.scopeVariable.simpleName())) {
                        String linked101 = switch (d.iteration()) {
                            case 0 -> "data:-1,newTrieNode:-1,node:-1,s:-1,this.root:-1";
                            case 1 -> "newTrieNode:-1,node:-1,s:-1,this.root:-1";
                            default -> "newTrieNode:4,node:2,this.root:2";
                        };
                        if ("1.0.1.0.0".equals(d.statementId())) {
                            // checking eval of 1.0.1
                            VariableInfo eval = d.variableInfoContainer().getPreviousOrInitial();
                            String linkedE = "node:2,this.root:2";
                            assertEquals(linkedE, eval.getLinkedVariables().toString());

                            String linked = "node:2,this.root:2";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                                    d.currentValue().toString());
                        }
                        if ("1.0.1.0.1".equals(d.statementId())) {
                            assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                                    d.currentValue().toString());
                        }
                        if ("1.0.1.0.2".equals(d.statementId())) {
                            String expected = d.iteration() < 2 ? "<f:node.map>" : "instance type HashMap<String,TrieNode<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            String linked = d.iteration() == 0
                                    ? "data:-1,newTrieNode:-1,node:-1,this.root:-1"
                                    : "node:2,this.root:2";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("1.0.1.1.0".equals(d.statementId())) {
                            // newTrieNode = node.map.get(s)
                            String linked = d.iteration() < 2
                                    ? "newTrieNode:-1,node:-1,s:-1,this.root:-1"
                                    : "newTrieNode:4,node:2,this.root:2";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("1.0.1.1.1".equals(d.statementId())) {
                            assertEquals(linked101, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("1.0.1".equals(d.statementId())) {
                            assertEquals(linked101, d.variableInfo().getLinkedVariables().toString());
                        }
                        String value = switch (d.iteration()) {
                            case 0 -> "strings.length>=1?<f:node.map>:<f:node.map>";
                            case 1 -> "strings.length>=1?<f:node.map>:<vp:map:link@Field_map>";
                            default ->
                                    "strings.length>=1&&null==node$1.map$0?instance type HashMap<String,TrieNode<T>>:nullable instance type Map<String,TrieNode<T>>";
                        };
                        if ("3".equals(d.statementId())) {
                            assertEquals(value, d.currentValue().toString());
                            String expectLinked = switch (d.iteration()) {
                                case 0 -> "data:-1,node.data:-1,node:-1,strings:-1,this.root:-1";
                                case 1 -> "node:-1,strings:-1,this.root:-1";
                                default -> "node:2,this.root:2";
                            };
                            assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 ->
                                        "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:data@Method_add_2-E;initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial:this.root@Method_add_0-C";
                                case 1 -> "initial:node@Method_add_1.0.1-C;link@Field_map";
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("4".equals(d.statementId())) {
                            assertEquals(value, d.currentValue().toString());
                            String linkedVars = switch (d.iteration()) {
                                case 0 -> "data:-1,node.data:-1,node:-1,strings:-1,this.root:-1";
                                case 1 -> "node:-1,strings:-1,this.root:-1";
                                default -> "node:2,this.root:2";
                            };
                            assertEquals(linkedVars, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 ->
                                        "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:data@Method_add_2-E;initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial:this.root@Method_add_0-C";
                                case 1 -> "initial:node@Method_add_1.0.1-C;link@Field_map";
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                    } else
                        fail("Have scope variable " + fr.scopeVariable + " in iteration " + d.iteration() + ", "
                                + d.statementId());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL); // by definition
                    }
                }
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("node:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        // checking on 1-E
                        VariableInfo eval1E = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("node:0", eval1E.getLinkedVariables().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String eqAccordingToState = d.statementAnalysis().stateData().equalityAccordingToStateStream()
                        .map(Object::toString).sorted().collect(Collectors.joining(","));
                if ("1.0.1.0.0".equals(d.statementId())) {
                    String eq = switch (d.iteration()) {
                        case 0 -> "<f:node.map>=null";
                        default -> "<f:node.map>=null,node$1.map$0=null";
                    };
                    assertEquals(eq, eqAccordingToState);
                    String condition = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        default -> "null==node$1.map$0";
                    };
                    assertEquals(condition, d.condition().toString());
                }
                if ("1.0.1.0.1".equals(d.statementId())) {
                    assertEquals("", eqAccordingToState);
                    String condition = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        default -> "null==node$1.map$0";
                    };
                    assertEquals(condition, d.condition().toString());
                    assertEquals(1, d.statementAnalysis().statementTime(Stage.EVALUATION));
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("", d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.1.0.0".equals(d.statementId())) {
                    assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                            d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_add_1.0.1.0.0-C";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.1.0.2".equals(d.statementId())) {
                    String value = d.iteration() < 2 ? "<m:put>" : "nullable instance type TrieNode<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1.0.2-C";
                        case 1 ->
                                "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1-C;link@Field_map";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String value = "new LinkedList<>()/*0==this.size()*/";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expectedDelay = switch (d.iteration()) {
                        case 0 ->
                                "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial:this.root@Method_add_0-C";
                        case 1 ->
                                "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial:this.root@Method_add_0-C;initial@Field_root;link@Field_map";
                        case 2 ->
                                "constructor-to-instance@Method_add_1.0.1.0.2-E;initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial:this.root@Method_add_0-C;initial@Field_root;link@Field_map;link@Field_root";
                        default -> "";
                    };
                    assertEquals(expectedDelay, d.evaluationResult().causesOfDelay().toString());
                }
                if ("2".equals(d.statementId())) {
                    String value = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1, 2 -> "null==<f:node.data>";
                        // TODO isn't this too complicated?
                        default ->
                                "null==(strings.length>=1?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):root).data$3";
                    };
                    assertEquals(value, d.evaluationResult().value().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                String expected = d.iteration() == 0 ? "<f:root>" : "instance type TrieNode<T>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
            }
            if ("data".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals("null", ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
            }
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals("null", ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(),
                        it0("data:-1,node.data:-1,node:-1,strings:-1,this.root:-1,this:-1"),
                        it1("data:-1,node.data:-1,node:-1,this.root:-1,this:-1"),
                        it(2, "node.data:4,this.root:2,this:3"));
                String expected = d.iteration() < 2 ? "link@Field_map" : "";
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_5".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("TrieSimplified_5", 0, 0, new DebugConfiguration.Builder()
                        //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        //     .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                // IMPORTANT: assignment outside of type, so to placate the analyser...
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
