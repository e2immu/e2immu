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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
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
        testClass("TrieSimplified_0", 0, 1, new DebugConfiguration.Builder()
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
        // potential null ptr
        testClass("TrieSimplified_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                fail(); // no code
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() == 0 ? "<f:root>" : "instance type TrieNode<T>";
                    assertEquals(expect, d.currentValue().toString());

                    String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }

                if ("node".equals(d.variableName())) {
                    String expect = d.iteration() == 0 ? "<f:root>" : "root";
                    assertEquals(expect, d.currentValue().toString());

                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if ("2".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "this.root";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        //   int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        //   assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());

                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:root>" : "root";
                        assertEquals(expect, d.currentValue().toString());

                        //    String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                        //     assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        //     int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        //     assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
            }
        };

        testClass("TrieSimplified_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                    String expectState = d.iteration() == 0 ? "null!=<f:map>" : "null!=map$0";
                    assertEquals(expectState, d.state().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    String expectState = d.iteration() == 0 ? "null!=<v:node>&&null!=<f:map>" :
                            "null!=map$0.get(nullable instance type String)&&null!=map$0";
                    assertEquals(expectState, d.state().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("1.0.1".equals(d.statementId()) || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        //    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED), d.statementId());
                    }
                }
                if (d.variable() instanceof DependentVariable dv) {
                    assertEquals("org.e2immu.analyser.testexample.TrieSimplified_3.goTo(java.lang.String[],int):0:strings[i]", dv.simpleName());
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:root>" : "root";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:get>" : "null==map$0?node$1:map$0.get(nullable instance type String)";
                        assertEquals(expectValue, d.currentValue().toString());
                        int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "upToPosition-<replace:int>>=1?<m:get>:<f:root>" :
                                "-(instance type int)+upToPosition>=1?null==map$0?nullable instance type TrieNode<T>:map$0.get(nullable instance type String):root";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 2 ? "upToPosition-<replace:int>>=1?<m:get>:<f:root>" :
                                "-(instance type int)+upToPosition>=1?null==map$0?nullable instance type TrieNode<T>:map$0.get(nullable instance type String):root";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if ("node$1".equals(d.variableName())) {
                    assertEquals("nullable instance type TrieNode<T>", d.currentValue().toString());
                    int expectNne;
                    if ("1.0.0.0.0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        expectNne = MultiLevel.EFFECTIVELY_NOT_NULL;
                    } else {
                        expectNne = MultiLevel.NULLABLE;
                    }
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION), d.statementId());
                }
                if ("node$1$1.0.1-E".equals(d.variableName())) {
                    assertNotEquals("1.0.0", d.statementId());

                    String expectValue = d.iteration() == 0 ? "<m:get>"
                            : "null==map$0?node$1:map$0.get(nullable instance type String)";
                    assertEquals(expectValue, d.currentValue().toString());

                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "null==<f:map>?null:<return value>" : "null==map$0?null:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "null==<v:node>?<s:>:null==<f:map>?null:<return value>"
                                : "null==map$0.get(nullable instance type String)||null==map$0?null:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "upToPosition-<replace:int>>=1?null==<v:node>?<s:>:null==<f:map>?null:<return value>:<return value>"
                                : "-(instance type int)+upToPosition>=1&&(null==map$0.get(nullable instance type String)||null==map$0)?null:<return value>";
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "<replace:int>>=upToPosition?upToPosition-<replace:int>>=1?<m:get>:<f:root>:null==<f:map>&&upToPosition-<replace:int>>=1&&upToPosition-<replace:int>>=1&&upToPosition-<replace:int>>=1&&upToPosition-<replace:int>>=1&&upToPosition-<replace:int>>=1&&upToPosition-<replace:int>>=1&&upToPosition-<replace:int>>=1?null:<return value>"
                                : "instance type int>=upToPosition?root:-(instance type int)+upToPosition>=1&&(null==<m:get>||null==(-(instance type int)+upToPosition>=1?nullable instance type Map<String,TrieNode<T>>:<v:map$0>))?null:<return value>";
                        assertEquals(expectValue, d.currentValue().toString(), d.variableName());
                    }
                }
            }
        };

        // FIXME the problem is statement 2; full of delays, in node -> in retvar -> ...; statement 1 looks fully OK

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                ParameterAnalysis strings = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, strings.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(expectMv, strings.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        testClass("TrieSimplified_3", 0, 0, new DebugConfiguration.Builder()
                //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                //       .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // added some code to TrieNode test 2
    @Test
    public void test_4() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TrieNode".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() <= 2 ? "<f:root>" : "new TrieNode<>()";
                    assertEquals(expect, d.currentValue().toString());

                    String expectLv = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    // here, ENN is computed in the group 'root', 'node'
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        int enn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(enn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL), d.statementId());
                    }
                    // from here on, the return variable is part of the equivalence group
                    // it only receives a value and proper links from iteration 3 onward
                    if ("2".equals(d.statementId())) {
                        int enn = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(enn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL), d.statementId());
                    }
                }

                if ("node".equals(d.variableName())) {
                    String expect = d.iteration() <= 2 ? "<f:root>" : "root";
                    assertEquals(expect, d.currentValue().toString(), "statement " + d.statementId());

                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        int enn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(enn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL), d.statementId());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectLv = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());

                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(expect, d.currentValue().toString());

                        String expectLv = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("root".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                if (d.iteration() <= 1) {
                    assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                } else {
                    assertEquals("new TrieNode<>()", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        testClass("TrieSimplified_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
