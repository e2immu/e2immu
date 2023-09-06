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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_21_VariableInLoop extends CommonTestRunner {

    public Test_21_VariableInLoop() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("found".equals(d.variableName())) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                            assertEquals("1", outside.statementIndex());
                        } else fail();
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<vl:found>" : "instance type boolean";
                        assertEquals(expected, vi1.getValue().toString());

                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().toString());
                        String expectMerge = d.iteration() == 0 ? "<vl:found>" : "true";
                        assertEquals(expectMerge, d.currentValue().toString()); // result of merge, should always be "true"
                    }
                    if ("1.0.0.0.0.0.0".equals(d.statementId())) {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<vl:found>" : "instance type boolean";
                        assertEquals(expected, vi1.getValue().toString());
                        assertEquals("true", d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<v:found>" : "!found$1";
                    assertEquals(expected, d.condition().toString());
                }
            }
        };
        testClass("VariableInLoop_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("findFirstStatementWithDelays".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<null-check>?<m:isPresent>?<null-check>?<v:sa>:<return value>:<v:sa>:<return value>";
                            case 1 -> "null==sa$1?<return value>:<m:isPresent>?<null-check>?<vl:sa>:<return value>:<vl:sa>";
                            default -> "null==sa$1?<return value>:(sa$1.navigationData()).next.isPresent()?null==(sa$1.navigationData()).next.get().orElse(null)?sa$1:<return value>:sa$1";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<null-check>&&(!<m:isPresent>||<null-check>)?<v:sa>:null";
                            case 1 -> "(<m:isPresent>||<null-check>)&&(!<null-check>||<null-check>)?null:<vl:sa>";
                            default -> "(((null==sa$1?firstStatementAnalyser:(sa$1.navigationData()).next.isPresent()&&null!=(sa$1.navigationData()).next.get().orElse(null)?(sa$1.navigationData()).next.get().get():sa$1).navigationData()).next.isPresent()||null==(null==sa$1?firstStatementAnalyser:(sa$1.navigationData()).next.isPresent()&&null!=(sa$1.navigationData()).next.get().orElse(null)?(sa$1.navigationData()).next.get().get():sa$1))&&(null!=((null==sa$1?firstStatementAnalyser:(sa$1.navigationData()).next.isPresent()&&null!=(sa$1.navigationData()).next.get().orElse(null)?(sa$1.navigationData()).next.get().get():sa$1).navigationData()).next.get().orElse(null)||null==(null==sa$1?firstStatementAnalyser:(sa$1.navigationData()).next.isPresent()&&null!=(sa$1.navigationData()).next.get().orElse(null)?(sa$1.navigationData()).next.get().get():sa$1))?null:null==sa$1?firstStatementAnalyser:(sa$1.navigationData()).next.isPresent()&&null!=(sa$1.navigationData()).next.get().orElse(null)?(sa$1.navigationData()).next.get().get():sa$1";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("sa".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        if (d.iteration() == 0) {
                            assertTrue(eval.getProperty(Property.CONTEXT_NOT_NULL).isDelayed());
                        } else {
                            // should be CONTENT_NOT_NULL, but in competition with the null check
                            assertEquals(MultiLevel.NULLABLE_DV, eval.getProperty(Property.CONTEXT_NOT_NULL));
                        }
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    // then comes an assignment...
                    if ("1.0.3".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    // and therefore "1" remains nullable, 1st round
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "firstStatementAnalyser:0,sa.navigationData().next:-1,scope-59:18:-1,scope-60:47:-1";
                            default -> "firstStatementAnalyser:0,sa.navigationData().next:3,scope-59:18:2,scope-60:47:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<null-check>?<m:get>:firstStatementAnalyser";
                            case 1 -> "null==sa$1?firstStatementAnalyser:<s:StatementAnalyser>";
                            default -> "null==sa$1?firstStatementAnalyser:(sa$1.navigationData()).next.isPresent()&&null!=(sa$1.navigationData()).next.get().orElse(null)?(sa$1.navigationData()).next.get().get():sa$1";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        // nullable or content not null? delayed or not in iteration 0?
                        // either @NotNull1 or @Nullable; it is the expression of the return value
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("scope-59:18".equals(d.variableName())) {
                    // scope variable
                    assertNotEquals("0", d.statementId());
                    if (d.statementId().equals("1.0.0")) {
                        // this value is assigned during the evaluation of 1.0.0, in VariableExpression.evaluateScope
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:navigationData>";
                            case 1 -> "<vp:NavigationData:cm@Parameter_next;mom@Parameter_next>";
                            default -> "sa$1.navigationData()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.ScopeVariable sv) {
                            assertNull(sv.getBeyondIndex());
                            assertNull(sv.getIndexCreatedInMerge());
                        } else fail();
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<null-check>?<m:navigationData>:nullable instance type NavigationData";
                            case 1 -> "null==sa$1?nullable instance type NavigationData:<m:navigationData>";
                            default -> "null==sa$1?nullable instance type NavigationData:sa$1.navigationData()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("findFirstStatementWithDelays".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals(0L, d.statementAnalysis().messageStream().count());
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d-> {
            if("StatementAnalyser".equals(d.typeInfo().simpleName)) {
               assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        // sa.navigationData(), x2
        testClass("VariableInLoop_1", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("loadBytes".equals(d.methodInfo().name)) {
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0 ? 4 : 2, d.evaluationResult().changeData().size());
                    String expected = d.iteration() == 0 ? "<m:get>" : "data.get(path.split(\"/\"))";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    EvaluationResult.ChangeData cd = d.findValueChange("urls");
                    assertFalse(cd.properties().containsKey(Property.IN_NOT_NULL_CONTEXT));
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("loadBytes".equals(d.methodInfo().name)) {
                if ("urls".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:get>" : "data.get(path.split(\"/\"))";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("url".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<vl:url>" : "nullable instance type URL";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("ResourceAccessException".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "msg".equals(pi.name)) {
                    // statement is an explicit constructor invocation (ECI)
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("0", d.statementId());
                }
            }
        };

        testClass("VariableInLoop_2", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    /*
    tests 2 things:
    - context not null in 2.0.2: first iteration, CNN has to be delayed!!!
    - value of the to-do variable: it is modified in the loop, so it should start the loop with an "instance"
     */
    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("changed".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertEquals("1-E,2.0.2-E,2:M", d.variableInfo().getAssignmentIds().toString());
                        // note: the 2:M is part of an exception for loops implemented in MergeHelper.mergeReadId
                        // would otherwise have been 2-E
                        assertEquals("2:M", d.variableInfo().getReadId());
                    }
                }
                if ("toDo".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new HashSet<>(strings)/*this.size()==strings.size()*/",
                                d.currentValue().toString());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<vl:toDo>" : "instance type HashSet<String>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<vl:toDo>" : "instance type Set<String>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("s".equals(d.variableName())) {
                    if ("2.0.2".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<c:boolean>" : "null!=supplier.get()";
                    assertEquals(expected, d.state().toString());
                    String stateVars = "[org.e2immu.analyser.parser.loops.testexample.VariableInLoop_3.method(java.util.Set<String>,java.util.function.Supplier<String>):1:supplier]";
                    assertEquals(stateVars, d.state().variables().toString());
                    String flow = d.iteration() == 0 ? "initial_flow_value@Method_method_2.0.2-C" : "CONDITIONALLY:1";
                    assertEquals(flow, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };
        testClass("VariableInLoop_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
