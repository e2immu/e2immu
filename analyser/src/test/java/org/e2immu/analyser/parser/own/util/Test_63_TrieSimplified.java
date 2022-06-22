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
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

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
                                case 0 -> "<null-check>?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<f:map>";
                                case 1, 2, 3, 4 -> "null==<f:node.map>?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<f:map>";
                                default -> "new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/";
                            };
                            assertEquals(expected, d.currentValue().toString());
                            String linked = d.iteration() <= 4 ? "newTrieNode:-1,node:-1,s:-1,this.root:-1" : "";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            assertDv(d, 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                            assertDv(d, 5, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("1.0.1.1.0".equals(d.statementId())) {
                            assertEquals("<f:map>", d.currentValue().toString());
                            assertEquals("newTrieNode:-1,node:-1,s:-1,this.root:-1",
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
                        case 1, 2, 3, 4 -> "null==<f:node.map>";
                        default -> "true";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData()
                            .valueOfExpression.get().toString());
                }
                if ("1.0.1.1.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_add_1.0.1-C";
                        default -> "wait_for_modification:node@Method_add_1-E";
                        //    default -> fail("Not supposed to reach iteration 7"); // because field analyser does not go beyond subtype
                    };
                    assertEquals(expected, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };
        // null ptr warning
        testClass("TrieSimplified_0", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
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
                        case 0 -> "<null-check>&&!<null-check>";
                        case 1, 2 -> "<null-check>&&null!=<f:root.map>";
                        default -> "null!=root.map$0&&null==root.map$0.get(s)";
                    };
                    assertEquals(expectCondition, d.absoluteState().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                String expected = d.iteration() <= 1 ? "<f:root>" : "instance type TrieNode<T>";
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

        testClass("TrieSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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
                        case 1, 2 -> "null==<f:root.map>";
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
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        // IMPORTANT! branch 0.1 is blocked, and map is never modified
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.1.1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "newTrieNode:-1,root.map:-1,s:-1";
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
                        case 1, 2 -> "initial@Field_root";
                        default -> "FlowData.ALWAYS";
                    };
                    assertEquals(flowData, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String flowData = switch (d.iteration()) {
                        case 0 -> "initial:this.root@Method_add_0-C";
                        case 1, 2 -> "initial:this.root@Method_add_0-C;initial@Field_root";
                        default -> "we're not reaching this iteration anymore";
                    };
                    assertEquals(flowData, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                String expected = d.iteration() <= 1 ? "<f:root>" : "new TrieNode<>()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 2 ? "<m:add>" : "/*inline add*/root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 3) {
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                            "Got " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
                assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("addSynchronized".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_1_2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("TrieSimplified_1_2", 6, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("TrieSimplified_2", 0, 2, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1 -> "null==<f:node.map>";
                        default -> "true";
                    };
                    assertEquals(expectValue, d.evaluationResult().getExpression().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                        case 1 -> "-1+upToPosition>=<oos:i>&&(<null-check>||<null-check>)?<vp::initial@Field_map>:-1-<oos:i>+upToPosition>=0?<m:get>:<f:root>";
                        case 2 -> "-1-(instance type int)+upToPosition>=0?null:<f:root>";
                        default -> "-1-(instance type int)+upToPosition>=0?null:root";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectState = switch (d.iteration()) {
                        case 0 -> "!<null-check>";
                        case 1 -> "null!=<f:node.map>";
                        default -> "false";
                    };
                    assertEquals(expectState, d.state().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 1, d.statementAnalysis().flowData().isUnreachable());
                    String reachable = d.iteration() < 2 ? "initial_flow_value@Method_goTo_1.0.1-C" : "NEVER:0";
                    assertEquals(reachable, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    assertEquals(d.iteration() > 1, d.statementAnalysis().flowData().isUnreachable());
                    String reachable = d.iteration() < 2 ? "initial_flow_value@Method_goTo_1.0.2-C" : "NEVER:0";
                    assertEquals(reachable, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock().toString());
                }
                if ("1.0.2.0.0".equals(d.statementId())) {
                    assertTrue(d.iteration() <= 1);
                }
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=(!<null-check>||!<loopIsNotEmptyCondition>)&&(!<null-check>||!<loopIsNotEmptyCondition>);parent=CM{}}";
                        case 1 -> "CM{state=(!<null-check>||instance type int>=upToPosition)&&(instance type int>=upToPosition||null!=<f:node.map>);parent=CM{}}";
                        default -> "CM{state=instance type int>=upToPosition;parent=CM{}}";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "(!<null-check>||!<loopIsNotEmptyCondition>)&&(!<null-check>||!<loopIsNotEmptyCondition>)";
                        case 1 -> "(!<null-check>||instance type int>=upToPosition)&&(instance type int>=upToPosition||null!=<f:node.map>)";
                        default -> "instance type int>=upToPosition";
                    };
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "map".equals(fieldReference.fieldInfo.name)) {
                    assertEquals("node", fieldReference.scope.toString());
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:map>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());

                        if (d.iteration() > 1) {
                            VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                            assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    // 1.0.0 becomes the last statement in the block by iteration 2
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<f:map>";
                            case 1 -> "-1-<oos:i>+upToPosition>=0?<f:map>:null";
                            default -> "null";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<f:map>";
                            case 1 -> "-1-<oos:i>+upToPosition>=0?<f:map>:null";
                            default -> "null";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        if (d.iteration() >= 2) assertTrue(d.currentValue() instanceof NullConstant);
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference && "root".equals(fieldReference.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2 ? "<f:root>" : "instance type TrieNode<T>/*new TrieNode<>()*/";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertEquals("node:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            case 1 -> "-1-<oos:i>+upToPosition>=0?<m:get>:<f:root>";
                            case 2 -> "-1-(instance type int)+upToPosition>=0?nullable instance type TrieNode<T>:<f:root>";
                            default -> "-1-(instance type int)+upToPosition>=0?nullable instance type TrieNode<T>:root";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0, 1, 2 -> "<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            default -> "-1-(instance type int)+upToPosition>=0?nullable instance type TrieNode<T>:root";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        // FIXME this is wrong, can we live with it?
                        assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<null-check>?<vp::container@Class_TrieNode>:<return value>";
                            case 1 -> "null==<f:node.map>?<vp::initial@Field_map>:<return value>";
                            default -> "null";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<return value>";
                            case 1 -> "-1-<oos:i>+upToPosition>=0&&(<null-check>||null==<f:node.map>)?<vp::initial@Field_map>:<return value>";
                            default -> "-1-(instance type int)+upToPosition>=0?null:<return value>";
                        };
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            case 1 -> "-1+upToPosition>=<oos:i>&&(<null-check>||<null-check>)?<vp::initial@Field_map>:-1-<oos:i>+upToPosition>=0?<m:get>:<f:root>";
                            case 2 -> "-1-(instance type int)+upToPosition>=0?null:<f:root>";
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

                String expected = d.iteration() <= 1 ? "<f:root>" : "new TrieNode<>()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("null", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("goTo".equals(d.methodInfo().name) && n == 1) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 2 ? "<m:goTo>"
                        : "/*inline goTo*/-1-(instance type int)+strings.length>=0?null:`root`";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("goTo".equals(d.methodInfo().name) && n == 2) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 2 ? "<m:goTo>"
                        : "/*inline goTo*/-1-(instance type int)+upToPosition>=0?null:root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                // we need to wait at least one iteration on transparent types
                // iteration 1 delayed because of @Modified of goTo
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_3".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("TrieSimplified_3", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
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
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() <= 2 ? "<f:root>" : "instance type TrieNode<T>/*new TrieNode<>()*/";
                    assertEquals(expect, d.currentValue().toString());

                    // here, ENN is computed in the group 'root', 'node'
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);

                    }
                    // from here on, the return variable is part of the equivalence group
                    // it only receives a value and proper links from iteration 3 onward
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }

                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(expect, d.currentValue().toString(), "statement " + d.statementId());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        // IMPORTANT: the variable is not read in the loop, but we cannot know that in iteration 0
                        // it therefore must participate in the delay scheme, SAApply.setValueForVariablesInLoopDefinedOutsideAssignedInside
                        String expect = switch (d.iteration()) {
                            case 0 -> "<vl:node>";
                            case 1, 2 -> "<f:root>";
                            default -> "root";
                        };
                        assertEquals(expect, d.currentValue().toString(), "statement " + d.statementId());
                    }
                    if ("2".equals(d.statementId())) {
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
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                String expected = d.iteration() <= 1 ? "<f:root>" : "new TrieNode<>()";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
            }
        };

        testClass("TrieSimplified_4", 0, 2, new DebugConfiguration.Builder()
                //     .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    private static final String NODE_DATA = "null==(strings.length>0?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):nullable instance type TrieNode<T>).data$3?instance type TrieNode<T>:strings.length>0?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):nullable instance type TrieNode<T>";

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
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "data".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("s".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                        assertFalse(d.variableInfoContainer().hasMerge());
                        assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                    // Trail 8 -- things go wrong in the 1.0.1 blocks
                    if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.0.1".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    }
                    // Trail 9 -- specifically, here!  node.map.put(s, newTrieNode);
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        String value = d.iteration() <= 1 ? "<v:s>" : "nullable instance type String";
                        assertEquals(value, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:node@Method_add_1.0.1.0.2-C;initial:s@Method_add_1.0.1.0.2-E";
                            case 1 -> "initial:node@Method_add_1.0.1.0.2-C;initial:s@Method_add_1.0.1.0.2-E;initial@Field_data;initial@Field_map";
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        String value = d.iteration() <= 1 ? "<v:s>" : "nullable instance type String";
                        assertEquals(value, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String value = d.iteration() <= 1 ? "<v:s>" : "nullable instance type String";
                        assertEquals(value, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String value = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(value, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1, 2 -> "initial@Field_root";
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                        String linked = d.iteration() <= 2 ? "this.root:0,this:-1" : "this.root:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String linkDelay = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1 -> "initial@Field_data;initial@Field_map;initial@Field_root";
                            case 2 -> "initial@Field_root";
                            default -> "";
                        };
                        assertEquals(linkDelay, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        String value = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(value, prev.getValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1, 2 -> "initial@Field_root";
                            default -> "";
                        };
                        assertEquals(expected, prev.getValue().causesOfDelay().toString());

                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectEval = d.iteration() <= 1 ? "<vl:node>" : "nullable instance type TrieNode<T>";
                        assertEquals(expectEval, eval.getValue().toString());
                        String expectedEval = switch (d.iteration()) {
                            case 0 -> "wait_for_assignment:node@Method_add_1-E";
                            case 1 -> "initial@Field_data;initial@Field_map";
                            default -> "";
                        };
                        assertEquals(expectedEval, eval.getValue().causesOfDelay().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        String merge = switch (d.iteration()) {
                            case 0 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                            case 1 -> "strings.length>0?null==<f:node.map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                            default -> "strings.length>0?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):nullable instance type TrieNode<T>";
                        };
                        assertEquals(merge, d.currentValue().toString());
                        assertEquals(d.iteration() >= 2, d.currentValue().isDone());

                        String links = d.iteration() <= 2 ? "node.map:-1,this.root:0,this:-1" : "node.map:2,this.root:0";
                        assertEquals(links, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        String current = switch (d.iteration()) {
                            case 0 -> "<null-check>?<vp:TrieNode<T>:container@Class_TrieNode>:<vl:node>";
                            case 1 -> "null==<f:node.map>?<vp:TrieNode<T>:container@Class_TrieNode>:<vl:node>";
                            default -> "null==node$1.map$0?instance type TrieNode<T>:nullable instance type TrieNode<T>";
                        };
                        assertEquals(current, d.currentValue().toString());
                    }
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        String val = switch (d.iteration()) {
                            case 0 -> "<vp:TrieNode<T>:container@Class_TrieNode>";
                            case 1 -> "<vp:TrieNode<T>:initial@Field_data;initial@Field_map>";
                            default -> "instance type TrieNode<T>";
                        };
                        assertEquals(val, d.currentValue().toString());
                    }
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    String expected = switch (d.iteration()) {
                        case 0 -> "<null-check>?<vp:TrieNode<T>:container@Class_TrieNode>:strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        case 1 -> "null==<f:node.data>?<vp:TrieNode<T>:initial@Field_data;initial@Field_map>:strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        default -> NODE_DATA;
                    };
                    if ("2".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 2) assertTrue(d.currentValue().isDone());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String value = switch (d.iteration()) {
                            case 0 -> "<vp:TrieNode<T>:container@Class_TrieNode>";
                            case 1 -> "<vp:TrieNode<T>:initial@Field_data;initial@Field_map>";
                            default -> "instance type TrieNode<T>";
                        };
                        assertEquals(value, d.currentValue().toString());
                        if (d.iteration() >= 2) assertTrue(d.currentValue().isDone());
                    }
                    String expected3 = switch (d.iteration()) {
                        case 0, 1 -> "<null-check>?<vp:TrieNode<T>:container@Class_TrieNode>:strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        default -> NODE_DATA;
                    };
                    if ("3".equals(d.statementId())) {
                        assertEquals(expected3, d.currentValue().toString());
                        if (d.iteration() >= 2) assertTrue(d.currentValue().isDone());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(expected3, d.currentValue().toString());
                        if (d.iteration() >= 2) assertTrue(d.currentValue().isDone());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
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
                            String expected = switch (d.iteration()) {
                                case 0 -> "<null-check>?new LinkedList<>()/*0==this.size()*/:<f:data>";
                                case 1 -> "null==<f:node.data>?new LinkedList<>()/*0==this.size()*/:<f:data>";
                                default -> "null==nullable instance type List<T>?new LinkedList<>()/*0==this.size()*/:nullable instance type List<T>";
                            };
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION); // FIXME this is wrong
                        }
                    } else fail("Have scope " + fr.scope + " in iteration " + d.iteration() + ", " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if (fr.scopeVariable != null && "node".equals(fr.scopeVariable.simpleName())) {
                        String linked101 = switch (d.iteration()) {
                            case 0, 1 -> "newTrieNode:-1,node:-1,s:-1,this.root:-1,this:-1";
                            case 2 -> "newTrieNode:-1,node:-1,this.root:-1,this:-1";
                            default -> "newTrieNode:3,node:2,this.root:2";
                        };
                        if ("1.0.1.0.0".equals(d.statementId())) {
                            // checking eval of 1.0.1
                            VariableInfo eval = d.variableInfoContainer().getPreviousOrInitial();
                            String linkedE = d.iteration() <= 2 ? "node:-1,this.root:-1,this:-1" : "node:2,this.root:2";
                            assertEquals(linkedE, eval.getLinkedVariables().toString());
                            String delayE = switch (d.iteration()) {
                                case 0 -> "initial:this.root@Method_add_0-C";
                                case 1 -> "initial@Field_data;initial@Field_map;initial@Field_root";
                                case 2 -> "initial@Field_root";
                                default -> "";
                            };
                            assertEquals(delayE, eval.getLinkedVariables().causesOfDelay().toString());

                            String linked = d.iteration() <= 2 ? "node:-1,this.root:-1,this:-1" : "node:2,this.root:2";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                                    d.currentValue().toString());
                        }
                        if ("1.0.1.0.1".equals(d.statementId())) {
                            assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                                    d.currentValue().toString());
                        }
                        if ("1.0.1.0.2".equals(d.statementId())) {
                            String expected = d.iteration() <= 1 ? "<f:node.map>" : "instance type HashMap<String,TrieNode<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            String linked = d.iteration() <= 2 ? "newTrieNode:-1,node:-1,this.root:-1,this:-1"
                                    : "newTrieNode:3,node:2,this.root:2";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("1.0.1.1.0".equals(d.statementId())) {
                            // newTrieNode = node.map.get(s)

                            assertEquals(linked101, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("1.0.1.1.1".equals(d.statementId())) {
                            assertEquals(linked101, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("1.0.1".equals(d.statementId())) {
                            assertEquals(linked101, d.variableInfo().getLinkedVariables().toString());
                        }
                        String value = switch (d.iteration()) {
                            case 0 -> "<null-check>&&strings.length>0?<f:node.map>:<f:map>";
                            case 1 -> "strings.length>0?<null-check>?<f:node.map>:<f:map>:nullable instance type Map<String,TrieNode<T>>";
                            default -> "strings.length>0&&null==node$1.map$0?instance type HashMap<String,TrieNode<T>>:nullable instance type Map<String,TrieNode<T>>";
                        };
                        if ("3".equals(d.statementId())) {
                            assertEquals(value, d.currentValue().toString());
                            String expectLinked = d.iteration() <= 2 ? "data:-1,node.data:-1,node:-1,this.root:-1,this:-1"
                                    : "data:3,node.data:2,node:2,this.root:2";
                            assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "initial:node@Method_add_1.0.1.1.0-C;initial:this.root@Method_add_0-C";
                                case 1 -> "initial:node@Method_add_1.0.1-C;initial@Field_data;initial@Field_map;initial@Field_root";
                                case 2 -> "initial@Field_root";
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("4".equals(d.statementId())) {
                            assertEquals(value, d.currentValue().toString());
                            String linkedVars = d.iteration() <= 2 ? "data:-1,node.data:-1,node:-1,return add:-1,this.root:-1,this:-1"
                                    : "data:3,node.data:2,node:2,return add:2,this.root:2";
                            assertEquals(linkedVars, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial:this.root@Method_add_0-C";
                                case 1 -> "initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial@Field_data;initial@Field_map;initial@Field_root";
                                case 2 -> "initial@Field_root";
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
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() <= 2 ? "node:0,this:-1" : "node:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String linkDelay = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1 -> "initial@Field_data;initial@Field_map;initial@Field_root";
                            case 2 -> "initial@Field_root";
                            default -> "";
                        };
                        assertEquals(linkDelay, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        // checking on 1-E
                        VariableInfo eval1E = d.variableInfoContainer().getPreviousOrInitial();
                        String linked = d.iteration() <= 2 ? "node:0,this:-1" : "node:0";
                        assertEquals(linked, eval1E.getLinkedVariables().toString());
                        String linkDelay = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1 -> "initial@Field_data;initial@Field_map;initial@Field_root";
                            case 2 -> "initial@Field_root";
                            default -> "";
                        };
                        assertEquals(linkDelay, eval1E.getLinkedVariables().causesOfDelay().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String eqAccordingToState = d.statementAnalysis().stateData().equalityAccordingToStateStream()
                        .map(Object::toString).sorted().collect(Collectors.joining(","));
                if ("1.0.1.0.0".equals(d.statementId())) {
                    assertEquals("", eqAccordingToState);
                    String condition = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1 -> "null==<f:node.map>";
                        default -> "null==node$1.map$0";
                    };
                    assertEquals(condition, d.condition().toString());
                }
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String equality = d.iteration() <= 1 ? "" : "node$1.map$0=null";
                    assertEquals(equality, eqAccordingToState);
                    String condition = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1 -> "null==<f:node.map>";
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
                        case 1 -> "initial:node@Method_add_1.0.1-C;initial@Field_data;initial@Field_map";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.1.0.2".equals(d.statementId())) {
                    String value = d.iteration() <= 1 ? "<m:put>" : "nullable instance type TrieNode<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_add_1.0.1.0.2-C";
                        case 1 -> "initial@Field_data;initial@Field_map";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String value = "new LinkedList<>()/*0==this.size()*/";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expectedDelay = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E";
                        case 1 -> "initial:node@Method_add_1.0.1-C;initial:s@Method_add_1.0.1.1.0-E;initial@Field_data;initial@Field_map";
                        default -> "";
                    };
                    assertEquals(expectedDelay, d.evaluationResult().causesOfDelay().toString());
                }
                if ("2".equals(d.statementId())) {
                    String value = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1 -> "null==<f:node.data>";
                        // TODO isn't this too complicated?
                        default -> "null==(strings.length>0?null==node$1.map$0?new TrieNode<>():null==node$1.map$0.get(nullable instance type String)?new TrieNode<>():node$1.map$0.get(nullable instance type String):nullable instance type TrieNode<T>).data$3";
                    };
                    assertEquals(value, d.evaluationResult().value().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                String expected = d.iteration() <= 1 ? "<f:root>" : "instance type TrieNode<T>";
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
                String linked = d.iteration() <= 2 ? "NOT_YET_SET" : "data:3,node.data:2,this.root:2";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
                String expected = switch (d.iteration()) {
                    case 0, 1, 2 -> "link@NOT_YET_SET";
                    default -> "";
                };
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 3, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                assertDv(d.p(0), 3, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, "initial@Field_data;initial@Field_map", 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_5".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("TrieSimplified_5", 0, 0, new DebugConfiguration.Builder()
                        //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        //    .addEvaluationResultVisitor(evaluationResultVisitor)
                        //    .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        //    .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        //    .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                // IMPORTANT: assignment outside of type, so to placate the analyser...
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
