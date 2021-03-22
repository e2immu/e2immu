
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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.analyser.FlowData.Execution.CONDITIONALLY;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_01_SMapList extends CommonTestRunner {

    public Test_Util_01_SMapList() {
        super(true);
    }

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("addAll".equals(d.methodInfo().name)) {
            if ("1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:get>" : "destination.get(e$1.getKey())";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1.0.1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "null==<m:get>" : "null==destination.get(e$1.getKey())";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1".equals(d.statementId())) {
                assertEquals("instance type Set<Entry<K,V>>", d.evaluationResult().value().toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("list".equals(d.methodInfo().name)) {
            final String RET_VAR = "org.e2immu.analyser.util.SMapList.list(java.util.Map<A,java.util.List<B>>,A)";
            if (d.variable() instanceof ReturnVariable retVar) {
                assertEquals(RET_VAR, retVar.fqn);
                if ("2".equals(d.statementId())) {
                    // note the absence of null!=a
                    assertEquals("null==map.get(a)?List.of():<return value>", d.currentValue().toString());

                    // IMPORTANT: ENN could also be NULLABLE if we took <return value> into account
                    // see code in MultiExpression
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue().getProperty(d.evaluationContext(),
                            VariableProperty.NOT_NULL_EXPRESSION, true));

                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("3".equals(d.statementId())) {
                    assertEquals("null==map.get(a)?List.of():map.get(a)", d.currentValue().toString());
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                            d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));

                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }

            if ("list".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    assertEquals("map.get(a)", d.currentValue().toString());
                }
            }
        }
        if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo bs && "bs".equals(bs.simpleName())) {
            if ("1".equals(d.statementId())) {
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
            if ("3".equals(d.statementId())) {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        }
        if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo bs && "a".equals(bs.simpleName())) {
            int paramMod = d.evaluationContext().getCurrentMethod()
                    .parameterAnalyses.get(1).getProperty(VariableProperty.CONTEXT_MODIFIED);

            if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, paramMod);
            }
            if ("2".equals(d.statementId()) || "3".equals(d.statementId())) {
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, paramMod);
            }
        }
        if ("add".equals(d.methodInfo().name) && "list".equals(d.variableName())) {
            assertEquals("", d.variableInfo().getLinkedVariables().toString());
        }


        if ("addAll".equals(d.methodInfo().name)) {
            if ("e".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<v:e>" : "instance type Entry<A,List<B>>";
                assertEquals(expectValue, d.currentValue().toString());
            }
            if (d.variable() instanceof ParameterInfo dest && dest.name.equals("destination")) {
                if ("0".equals(d.statementId())) {
                    assertEquals("nullable instance type Map<A,List<B>>", d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("inDestination".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "destination.get(e$1.getKey())";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("change".equals(d.variableName())) {
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<s:boolean>" : "change$1||null==destination.get(e$1.getKey())";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                // 2nd branch, merge of an if-statement
                if ("1.0.1.1.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:addAll>&&<s:boolean>" : "destination.get(e$1.getKey()).addAll(e$1.getValue())";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                // merge of the two above
                if ("1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "null==<m:get>?<s:boolean>:<m:addAll>&&<s:boolean>"
                            : "null==destination.get(e$1.getKey())?change$1||null==destination.get(e$1.getKey()):destination.get(e$1.getKey()).addAll(e$1.getValue())";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("change$1".equals(d.variableName())) {
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "instance type boolean";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change";
                    assertEquals(expectLinked, d.variableInfo().getStaticallyAssignedVariables().toString());
                }
            }
            if ("change$1$1_0_1_0_1-E".equals(d.variableName())) {
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<s:boolean>" : "change$1||null==destination.get(e$1.getKey())";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    assertSame(VariableInLoop.VariableType.LOOP_COPY, d.variableInfoContainer().getVariableInLoop().variableType());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.1.0".equals(d.statementId())) {
                    fail("The variable should not exist here");
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertSame(VariableInLoop.VariableType.LOOP_COPY, d.variableInfoContainer().getVariableInLoop().variableType());
                    String expectValue = d.iteration() == 0 ? "<s:boolean>" : "change$1||null==destination.get(e$1.getKey())";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("list".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                assertEquals("true", d.state().toString());
            }
            if ("1".equals(d.statementId())) {
                // a != null is in the property of parameter, not in precondition
                assertTrue( d.localConditionManager().precondition().isEmpty());
            }
            if ("2.0.0".equals(d.statementId())) {
                assertEquals("null==map.get(a)", d.condition().toString());
                assertEquals("null==map.get(a)", d.absoluteState().toString());
            }
            if ("3".equals(d.statementId())) {
                assertEquals("null!=map.get(a)", d.state().toString());
            }
        }
        if ("addAll".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
            if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                assertSame(CONDITIONALLY, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
            if ("1.0.1.1.0".equals(d.statementId())) {
                String expectCondition = d.iteration() == 0 ? "null!=<m:get>"
                        : "null!=destination.get(e$1.getKey())";
                assertEquals(expectCondition, d.condition().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("list".equals(name)) {
            VariableInfo returnValue1 = d.getReturnAsVariable();
            assertEquals("null==map.get(a)?List.of():map.get(a)",
                    d.getReturnAsVariable().getValue().toString());
            int retValNotNull = returnValue1.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, retValNotNull);
        }
        if ("copy".equals(name)) {
            VariableInfo returnValue = d.getReturnAsVariable();
            assertEquals(MultiLevel.MUTABLE, returnValue.getProperty(VariableProperty.IMMUTABLE));
        }
        if ("add".equals(name) && d.methodInfo().methodInspection.get().getParameters().size() == 3) {
            ParameterInfo parameterInfo = d.methodInfo().methodInspection.get().getParameters().get(2);
            if ("bs".equals(parameterInfo.name)) {
                int expectCmBs = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                int modified = d.parameterAnalyses().get(2).getProperty(VariableProperty.MODIFIED_VARIABLE);
                assertEquals(expectCmBs, modified);
                int expectCmA = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                int modifiedA = d.parameterAnalyses().get(1).getProperty(VariableProperty.MODIFIED_VARIABLE);
                assertEquals(expectCmA, modifiedA);
            }
            if ("b".equals(parameterInfo.name)) {
                int expectCmB = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                int modifiedB = d.parameterAnalyses().get(1).getProperty(VariableProperty.MODIFIED_VARIABLE);
                assertEquals(expectCmB, modifiedB);
            }
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo map = typeMap.get(Map.class);
        MethodInfo entrySet = map.findUniqueMethod("entrySet", 0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
                entrySet.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SMapList"), 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
