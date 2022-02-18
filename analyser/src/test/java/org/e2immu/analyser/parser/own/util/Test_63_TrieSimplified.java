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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
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
                    String expectCondition = d.iteration() == 0 ? "<m:get>" : "map$0.get(s)";
                    assertEquals(expectCondition, d.evaluationResult().value().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "<m:put>" : "map$1.put(s,newTrieNode)";
                    assertEquals(expectCondition, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0.1.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "null!=<f:map>" : "null!=map$0";
                    assertEquals(expectCondition, d.condition().toString());
                }
                if ("0.1.1.0.1".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "null==<m:get>&&null!=<f:map>" : "null==map$0.get(s)&&null!=map$0";
                    assertEquals(expectCondition, d.absoluteState().toString());
                }
            }
        };
        // 2x unreachable statement, 2x constant eval
        testClass("TrieSimplified_1", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
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
                    String expectValue = d.iteration() == 0 ? "null==<f:map>" : "true";
                    assertEquals(expectValue, d.evaluationResult().getExpression().toString());
                }
                if ("1.0.1".equals(d.statementId()) || "1.0.2".equals(d.statementId())) {
                    // unreachable in iteration 1
                    assertEquals(0, d.iteration());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<v:i>>=<p:upToPosition>?upToPosition><replace:int>?<m:get>:<f:root>:null==<f:map>&&<p:upToPosition>><replace:int>&&<p:upToPosition>-<v:i>>=1?null:<return value>";
                        case 1, 2 -> "instance type int>=upToPosition?<f:root>:null";
                        default -> "instance type int>=upToPosition?root:null";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectState = d.iteration() == 0 ? "null!=<f:map>" : "false";
                    assertEquals(expectState, d.state().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().flowData().isUnreachable());
                }
                if ("1.0.2".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().flowData().isUnreachable());
                }
                if ("1.0.2.0.0".equals(d.statementId())) {
                    assertEquals(0, d.iteration());
                }
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<v:i>>=upToPosition" : "instance type int>=upToPosition";
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fieldReference && "map".equals(fieldReference.fieldInfo.name)) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:map>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLinked = d.iteration() == 0 ? "*" : "";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                        if (d.iteration() > 0) {
                            VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                            assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference && "root".equals(fieldReference.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2 ? "<f:root>" : "instance type TrieNode<T>";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() <= 2 ? "*" : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        //int expectBreak = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                        //assertEquals(expectBreak, d.getProperty(Property.EXTERNAL_IMMUTABLE_BREAK_DELAY));
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("root$0".equals(d.variableName())) {
                    fail(); // root is final, so there will be no copies for a variable field
                }

                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "upToPosition><replace:int>?<m:get>:<f:root>";
                            case 1, 2 -> "<f:root>";
                            default -> "root";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2 ? "upToPosition><replace:int>?<m:get>:<f:root>" : "root";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node$1".equals(d.variableName())) {
                    assertEquals("nullable instance type TrieNode<T>", d.currentValue().toString());
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION), d.statementId());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "null==<f:map>?null:<return value>" : "null";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "upToPosition><replace:int>?null==<v:node>?<s:>:null==<f:map>?null:<return value>:<return value>"
                                : "upToPosition>instance type int?null:<return value>";
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<v:i>>=<p:upToPosition>?upToPosition><replace:int>?<m:get>:<f:root>:null==<f:map>&&<p:upToPosition>><replace:int>&&<p:upToPosition>-<v:i>>=1?null:<return value>";
                            case 1, 2 -> "instance type int>=upToPosition?<f:root>:null";
                            default -> "instance type int>=upToPosition?root:null";
                        };
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                //assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE_BREAK_DELAY));
                // wait until TrieNode is immutable
                if (d.iteration() <= 1) {
                    assertNull(d.fieldAnalysis().getValue());
                } else {
                    assertEquals("instance type TrieNode<T>", d.fieldAnalysis().getValue().toString());
                }
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("null", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                // we need to wait at least one iteration on transparent types
                // iteration 1 delayed because of @Modified of goTo
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TrieSimplified_3".equals(d.typeInfo().simpleName)) {
                assertEquals("[Type param T, Type param T]",
                        d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("TrieSimplified_3", 2, 0, new DebugConfiguration.Builder()
                //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //    .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                //   .addEvaluationResultVisitor(evaluationResultVisitor)
                //   .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
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
                    String expect = d.iteration() <= 2 ? "<f:root>" : "root";
                    assertEquals(expect, d.currentValue().toString(), "statement " + d.statementId());

                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertLinked(d, 1,
                                "immutable@Class_TrieNode;initial:this.root@Method_add_0",
                                "node:0,return add:0,this.root:0");

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
                        String expect = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = d.iteration() <= 2 ? "node:0,return add:0,this.root:0" : "node:0,return add:0,this.root:1";
                        assertLinked(d, 1,
                                "immutable@Class_TrieNode;initial:this.root@Method_add_0",
                                expectLv);
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
    problem to fix: 1 - 5 CNN in statement 2 (node = newTrieNode); node is CNN 5, newTrieNode CNN 1;
    node becomes node$1 in iteration 1
     */
    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    DV cnn = d.getProperty(Property.CONTEXT_NOT_NULL);
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
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
            }
        };
        // 2x potential null pointer warning, seems correct
        testClass("TrieSimplified_5", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
