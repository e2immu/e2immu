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
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_63_TrieSimplified extends CommonTestRunner {

    public Test_63_TrieSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        // null ptr warning
        testClass("TrieSimplified_0", 5, 0, new DebugConfiguration.Builder()
                .build());
    }

    // there should be no null ptr warnings
    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0.1.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() <= 3 ? "<m:get>" : "root.map$0.get(s)";
                    assertEquals(expectCondition, d.evaluationResult().value().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String expectCondition = d.iteration() <= 3 ? "<m:put>" : "nullable instance type TrieNode<T>";
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
                        case 0, 1 -> "<null-check>&&!<null-check>";
                        case 2, 3 -> "<null-check>&&null!=<f:root.map>";
                        default -> "null!=root.map$0&&null==root.map$0.get(s)";
                    };
                    assertEquals(expectCondition, d.absoluteState().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                String expected = d.iteration() <= 2 ? "<f:root>" : "instance type TrieNode<T>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertDv(d, 3, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 3 ? "<m:add>" : "root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("TrieSimplified_1", 0, 0, new DebugConfiguration.Builder()
                //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //  .addEvaluationResultVisitor(evaluationResultVisitor)
                //  .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                //  .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // without the getter and setter
    @Test
    public void test_1_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
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
                String expected = d.iteration() <= 2 ? "<m:add>" : "root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
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

        testClass("TrieSimplified_1_2", 7, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1_2bis() throws IOException {
        testClass("TrieSimplified_1_2", 5, 1,
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
                        case 1 -> "<simplification>";
                        default -> "true";
                    };
                    assertEquals(expectValue, d.evaluationResult().getExpression().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<s:TrieNode<T>>:<vl:node>";
                        case 1 -> "upToPosition><out of scope:i:1>&&(<null-check>||<simplification>)?<vp::initial@Field_map>:upToPosition><out of scope:i:1>?<s:TrieNode<T>>:<vl:node>";
                        default -> "upToPosition>instance type int?null:node";
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
                        case 1 -> "!<simplification>";
                        default -> "false";
                    };
                    assertEquals(expectState, d.state().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 1, d.statementAnalysis().flowData().isUnreachable());
                }
                if ("1.0.2".equals(d.statementId())) {
                    assertEquals(d.iteration() > 1, d.statementAnalysis().flowData().isUnreachable());
                }
                if ("1.0.2.0.0".equals(d.statementId())) {
                    assertTrue(d.iteration() <= 1);

                }
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=(!<null-check>||!<loopIsNotEmptyCondition>)&&(!<null-check>||!<loopIsNotEmptyCondition>);parent=CM{}}";
                        case 1 -> "CM{state=(!<null-check>||instance type int>=upToPosition)&&(!<simplification>||instance type int>=upToPosition);parent=CM{}}";
                        default -> "CM{state=instance type int>=upToPosition;parent=CM{}}";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "(!<null-check>||!<loopIsNotEmptyCondition>)&&(!<null-check>||!<loopIsNotEmptyCondition>)";
                        case 1 -> "(!<null-check>||instance type int>=upToPosition)&&(!<simplification>||instance type int>=upToPosition)";
                        default -> "instance type int>=upToPosition";
                    };
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "map".equals(fieldReference.fieldInfo.name)) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:map>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertEquals("node.map:0", d.variableInfo().getLinkedVariables().toString());

                        if (d.iteration() > 0) {
                            VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                            assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<f:map>";
                            case 1 -> "upToPosition><out of scope:i:1>?<f:map>:null";
                            default -> "null";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<f:map>";
                            case 1 -> "upToPosition><out of scope:i:1>?<f:map>:null";
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

                        assertEquals("node:0,this.root:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("root$0".equals(d.variableName())) {
                    fail(); // root is final, so there will be no copies for a variable field
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
                            case 0 -> "<loopIsNotEmptyCondition>?<s:TrieNode<T>>:<vl:node>";
                            case 1 -> "upToPosition><out of scope:i:1>?<s:TrieNode<T>>:<vl:node>";
                            default -> "nullable instance type TrieNode<T>";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<s:TrieNode<T>>:<vl:node>";
                            case 1 -> "upToPosition><out of scope:i:1>?<s:TrieNode<T>>:<vl:node>";
                            default -> "nullable instance type TrieNode<T>";
                        };
                        assertEquals(expectValue, d.currentValue().toString());

                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node$1".equals(d.variableName())) {
                    assertEquals("nullable instance type TrieNode<T>", d.currentValue().toString());
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION), d.statementId());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<null-check>?<vp::container@Class_TrieNode>:<return value>";
                            case 1 -> "<simplification>?<vp::initial@Field_map>:<return value>";
                            default -> "null";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<return value>";
                            case 1 -> "upToPosition><out of scope:i:1>&&(<null-check>||<simplification>)?<vp::initial@Field_map>:<return value>";
                            default -> "upToPosition>instance type int?null:<return value>";
                        };
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<s:TrieNode<T>>:<vl:node>";
                            case 1 -> "upToPosition><out of scope:i:1>&&(<null-check>||<simplification>)?<vp::initial@Field_map>:upToPosition><out of scope:i:1>?<s:TrieNode<T>>:<vl:node>";
                            default -> "upToPosition>instance type int?null:node";
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

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("null", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                // we need to wait at least one iteration on transparent types
                // iteration 1 delayed because of @Modified of goTo
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_3".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.lang.String[], Type param T",
                        d.typeAnalysis().getTransparentTypes().toString());
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
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    /*
    following the cnn:strings@Method_add_1-E trail, we come to the conclusion that TrieNode.map has no value for
    IMMUTABLE because the field "root", of type TrieNode, has no value. This is a circular reasoning, but not a very
    obvious one.
     */
    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.0.1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("4".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "data".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("s".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<vl:s>" : "instance type String";
                        assertEquals(expected, d.currentValue().toString());
                        assertFalse(d.variableInfoContainer().hasMerge());
                        assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                    // Trail 8 -- things go wrong in the 1.0.1 blocks
                    if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<vl:s>" : "instance type String";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    // Trail 9 -- specifically, here!  node.map.put(s, newTrieNode);
                    if ("1.0.1.0.2".equals(d.statementId())) {
                        String value = d.iteration() <= 2 ? "<vl:s>" : "instance type String";
                        assertEquals(value, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "cnn:strings@Method_add_1-E";
                            case 1 -> "initial@Field_data;initial@Field_map";
                            case 2 -> "cnn:node$1.map@Method_add_1.0.1-C";
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        String value = d.iteration() <= 2 ? "<vl:s>" : "instance type String";
                        assertEquals(value, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String value = d.iteration() <= 2 ? "<vl:s>" : "instance type String";
                        assertEquals(value, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 3, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    }
                }
                if ("node".equals(d.variableName())) {
                    DV cnn = d.getProperty(Property.CONTEXT_NOT_NULL);
                    if ("0".equals(d.statementId())) {
                        String value = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(value, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:this.root@Method_add_0-C";
                            case 1, 2 -> "initial@Field_root";
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
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
                            case 2 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:nullable instance type TrieNode<T>";
                            default -> "strings.length>0?null==node$1.map$0?new TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>";
                        };
                        assertEquals(merge, d.currentValue().toString());
                        assertEquals(d.iteration() >= 3, d.currentValue().isDone());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                        String current = d.iteration() <= 2 ? "<vl:node>" : "nullable instance type TrieNode<T>";
                        assertEquals(current, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:node.map@Method_add_1.0.1-C;initial:node@Method_add_1.0.1-C";
                            case 1 -> "initial@Field_data;initial@Field_map";
                            case 2 -> "cnn:node$1.map@Method_add_1.0.1-C"; // Trail 13
                            default -> "";
                        };
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().causesOfDelay().toString());
                    }
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                        String val = d.iteration() <= 2 ? "<vl:node>" : "nullable instance type TrieNode<T>";
                        assertEquals(val, d.currentValue().toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "initial:node.map@Method_add_1.0.1-C;initial:node@Method_add_1.0.1-C";
                            case 1 -> "initial@Field_data;initial@Field_map";
                            case 2 -> "cnn:node$1.map@Method_add_1.0.1-C"; // Trail 12
                            default -> "";
                        };
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                    }
                    String expected = switch (d.iteration()) {
                        case 0 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        case 1 -> "strings.length>0?null==<f:node.map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        case 2, 3 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:nullable instance type TrieNode<T>";
                        default -> "strings.length>0?null==node$1.map$0?new TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>";
                    };
                    if ("2".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().toString());

                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 4) assertTrue(d.currentValue().isDone());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String value = switch (d.iteration()) {
                            case 0 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                            case 1 -> "strings.length>0?null==<f:node.map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                            case 2, 3 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:nullable instance type TrieNode<T>";
                            default -> "strings.length>0?null==node$1.map$0?new TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>";
                        };
                        assertEquals(value, d.currentValue().toString());
                        if (d.iteration() >= 4) assertTrue(d.currentValue().isDone());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 4) assertTrue(d.currentValue().isDone());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 4) assertTrue(d.currentValue().isDone());
                        assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("newTrieNode".equals(d.variableName())) {
                    DV cnn = d.getProperty(Property.CONTEXT_NOT_NULL);
                    DV nne = d.getProperty(Property.NOT_NULL_EXPRESSION);
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, nne);
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                    }
                    if ("1.0.1.1.1.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, nne);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    if ("node$1".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId()) || "3".equals(d.statementId()) || "4".equals(d.statementId())) {
                            fail("Loop variable, should not exist outside loop");
                        }
                    } else if ("node".equals(fr.scope.toString())) {
                        if ("2.0.0".equals(d.statementId())) {
                            assertEquals("new LinkedList<>()/*0==this.size()*/", d.currentValue().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        }
                    } else if ("strings.length>0?null==node$1.map$0?new TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>".equals(fr.scope.toString())) {
                        assertTrue(d.iteration() >= 3);
                        String expected = d.iteration() == 3 ? "<f:strings.length>0?null==node$1.map$0?new TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>.data>"
                                : "nullable instance type List<T>";
                        assertEquals(expected, d.currentValue().toString());

                        assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertFalse(d.statementId().startsWith("1"));
                    } else fail("Have scope " + fr.scope + " in iteration " + d.iteration() + ", " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("node$1".equals(fr.scope.toString())) {
                        if ("1.0.1".equals(d.statementId())) {
                            assertTrue(d.iteration() > 0);

                            VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                            String expectedPrev = d.iteration() <= 1 ? "<f:node$1.map>" : "nullable instance type Map<String,TrieNode<T>>";
                            assertEquals(expectedPrev, prev.getValue().toString());
                            assertEquals(MultiLevel.NULLABLE_DV, prev.getProperty(Property.CONTEXT_NOT_NULL));

                            assertEquals(d.iteration() > 2, d.variableInfoContainer().hasMerge());
                            String expected = d.iteration() <= 2 ? "<f:node$1.map>" : "nullable instance type Map<String,TrieNode<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("2".equals(d.statementId()) || "3".equals(d.statementId()) || "4".equals(d.statementId())) {
                            fail("Loop variable, should not exist outside loop");
                        }
                    } else if ("node".equals(fr.scope.toString())) {
                        if ("1.0.1.0.0".equals(d.statementId())) {
                            assertEquals("node.map:0", d.variableInfo().getLinkedVariables().toString());
                            assertEquals("", d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                        if ("1.0.1.0.2".equals(d.statementId())) {
                            assertEquals("node.map:0", d.variableInfo().getLinkedVariables().toString());
                            assertEquals("", d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                        if ("1.0.1.1.0".equals(d.statementId())) {
                            // newTrieNode = node.map.get(s)
                            String linked = d.iteration() <= 2 ? "newTrieNode:-1,node.map:0" : "node.map:0";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "cnn:strings@Method_add_1-E"; // Trail 7 --> where does this comes from? s is linked to "strings"!!!
                                case 1 -> "initial:node.map@Method_add_1.0.1-C;initial:node@Method_add_1.0.1-C;initial@Field_data;initial@Field_map";
                                case 2 -> "cnn:node$1.map@Method_add_1.0.1-C";
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                        if ("1.0.1.1.1".equals(d.statementId())) {
                            String linked = d.iteration() <= 2 ? "newTrieNode:-1,node.map:0" : "node.map:0";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "immutable@Class_TrieNode";
                                case 1 -> "initial@Field_data;initial@Field_map";
                                case 2 -> "cnn:node$1.map@Method_add_1.0.1-C"; // Trail 6 -> focus on 7
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                        if ("1.0.1".equals(d.statementId())) {
                            String linked = d.iteration() <= 2 ? "newTrieNode:-1,node.map:0" : "node.map:0";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "immutable@Class_TrieNode";
                                case 1 -> "initial@Field_data;initial@Field_map";
                                case 2 -> "cnn:node$1.map@Method_add_1.0.1-C"; // Trail 5 --> this is a merge, so look into the blocks
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                        String value = switch (d.iteration()) {
                            case 0 -> "strings.length>0&&(<null-check>||<null-check>)?<mmc:map>:<f:map>";
                            case 1 -> "strings.length>0?<null-check>||null==<f:node.map>?<mmc:map>:<f:map>:nullable instance type Map<String,TrieNode<T>>";
                            case 2 -> "strings.length>0?<null-check>?instance type HashMap<String,TrieNode<T>>:<null-check>?<mmc:map>:<f:map>:nullable instance type Map<String,TrieNode<T>>";
                            default -> "strings.length>0&&null==node$1.map$0?instance type HashMap<String,TrieNode<T>>:nullable instance type Map<String,TrieNode<T>>";
                        };
                        if ("2".equals(d.statementId()) || "3".equals(d.statementId())) {
                            assertEquals(value, d.currentValue().toString());
                            String expectLinked = d.iteration() <= 2 ? "node.map:0,node:-1" : "node.map:0";
                            assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "immutable@Class_TrieNode";
                                case 1 -> "initial@Field_data;initial@Field_map";
                                case 2 -> "cnn:node$1.map@Method_add_1.0.1-C";
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                        if ("4".equals(d.statementId())) {
                            assertEquals(value, d.currentValue().toString());
                            String linked = d.iteration() <= 3 ? "node.map:0,node:-1,return add:-1" : "node.map:0";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                            String expected = switch (d.iteration()) {
                                case 0 -> "cnn:strings@Method_add_1-E"; // Trail 4 --> 1.0.1
                                case 1 -> "initial:node.map@Method_add_1.0.1-C;initial:node@Method_add_1.0.1-C;initial@Field_data;initial@Field_map";
                                case 2, 3 -> "cnn:node$1.map@Method_add_1.0.1-C";
                                default -> "";
                            };
                            assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                        }
                    } else fail("Have scope " + fr.scope + " in iteration " + d.iteration() + ", " + d.statementId());
                }
                if(d.variable() instanceof ReturnVariable) {
                    if("4".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("1.0.1.0.0".equals(d.statementId())) {
                    assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                            d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_add_1.0.1.0.0-C";
                        case 1 -> "initial@Field_data;initial@Field_map";
                        case 2 -> "cnn:node$1.map@Method_add_1.0.1-C"; // Trail 11... where does this come from? try node
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.1.0.2".equals(d.statementId())) {
                    String value = d.iteration() <= 1 ? "<m:put>" : "nullable instance type TrieNode<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "cnn:strings@Method_add_1-E"; // Trail 10 ... still this cnn on "strings"
                        case 1 -> "initial@Field_data;initial@Field_map";
                        case 2 -> "cnn:node$1.map@Method_add_1.0.1-C";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
                String expectedDelay = switch (d.iteration()) {
                    case 0 -> "initial:node.data@Method_add_2-C;initial:node.map@Method_add_1.0.1-C";
                    case 1 -> "initial:node.map@Method_add_1.0.1-C;initial:node@Method_add_1.0.1-C;initial@Field_data;initial@Field_map";
                    case 2 -> "cnn:node$1.map@Method_add_1.0.1-C";
                    case 3 -> "cnn:strings.length>0?null==node$1.map$0?new org.e2immu.analyser.parser.own.util.testexample.TrieSimplified_5.TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new org.e2immu.analyser.parser.own.util.testexample.TrieSimplified_5.TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>.data@Method_add_2-C";
                    default -> "";
                };
                if ("2.0.0".equals(d.statementId())) {
                    String value = "new LinkedList<>()/*0==this.size()*/";
                    assertEquals(value, d.evaluationResult().value().toString());
                    assertEquals(expectedDelay, d.evaluationResult().causesOfDelay().toString());
                }
                if ("2".equals(d.statementId())) {
                    String value = switch (d.iteration()) {
                        case 0, 3 -> "<null-check>";
                        case 1, 2 -> "null==<f:node.data>";
                        default -> "null==strings.length>0?null==node$1.map$0?new TrieNode<>():strings.length>0&&null!=node$1.map$0&&null==node$1.map$0.get(instance type String)?new TrieNode<>():node$1.map$0.get(instance type String):nullable instance type TrieNode<T>.data$3";
                    };
                    assertEquals(value, d.evaluationResult().value().toString());
                    assertEquals(expectedDelay, d.evaluationResult().causesOfDelay().toString());
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
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                String expected = switch (d.iteration()) {
                    case 0 -> "cnn:strings@Method_add_1-E"; // Trail 3 --> node.map in add, links delay
                    case 1 -> "initial:node.map@Method_add_1.0.1-C;initial:node@Method_add_1.0.1-C;initial@Field_data;initial@Field_map";
                    case 2, 3 -> "cnn:node$1.map@Method_add_1.0.1-C"; // FIXME
                    default -> "";
                };
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
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
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                // IMPORTANT: assignment outside of type, so to placate the analyser...
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
