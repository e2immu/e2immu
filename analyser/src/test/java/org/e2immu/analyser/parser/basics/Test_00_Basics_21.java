
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


import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class Test_00_Basics_21 extends CommonTestRunner {
    public Test_00_Basics_21() {
        super(true);
    }

    /*
    set, get, isSet are analysed before copy; so context modified should be false in 0.0.0 from the start.
    However, the single return value of these methods takes one iteration, so delays are unavoidable.
     */
    @Test
    public void test_21() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("copy".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = switch (d.iteration()) {
                        case 0, 1 -> "<m:isSet>";
                        default -> "null!=`other.t`";
                    };
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    EvaluationResult.ChangeData cd = d.findValueChangeBySubString("other");
                    assertEquals("", cd.linkedVariables().toString());

                    assertEquals(d.iteration() <= 1, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = switch (d.iteration()) {
                        case 0, 1 -> "<m:set>";
                        default -> "<no return value>";
                    };
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    EvaluationResult.ChangeData cd = d.findValueChangeBySubString("other");
                    assertEquals("", cd.linkedVariables().toString());

                    // this is linked to other at :4 common HC
                    EvaluationResult.ChangeData cdThis = d.findValueChangeByToString("this");
                    String expectLv = d.iteration() <= 1 ? "other:-1" : "other:4"; // common_hc
                    assertEquals(expectLv, cdThis.linkedVariables().toString());

                    assertEquals(d.iteration() <= 2, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
            if ("set".equals(d.methodInfo().name)) {
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals("t", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("copy".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = switch (d.iteration()) {
                            case 0, 1 -> "<p:other>";
                            case 2 -> "<mod:T>";
                            default -> "nullable instance type Basics_21<T>/*@Identity*/";
                        };
                        assertEquals(expectValue, d.currentValue().toString());

                        // other is linked to this with common HC
                        String expectLinked = d.iteration() <= 1 ? "this:-1" : "this:4";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 2, DV.FALSE_DV, CONTEXT_MODIFIED);

                        if (d.iteration() >= 3) {
                            assertEquals(MultiLevel.MUTABLE_DV, d.variableInfoContainer()
                                    .getPreviousOrInitial().getProperty(CONTEXT_IMMUTABLE));
                        }
                        assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV, CONTEXT_IMMUTABLE);
                    } else {
                        assertEquals("0", d.statementId());
                        String expectValue = switch (d.iteration()) {
                            case 0, 1 -> "<m:isSet>?<p:other>:nullable instance type Basics_21<T>/*@Identity*/";
                            case 2 -> "null==`other.t`?nullable instance type Basics_21<T>/*@Identity*/:<mod:T>";
                            default -> "nullable instance type Basics_21<T>/*@Identity*/";
                        };
                        assertEquals(expectValue, d.currentValue().toString());

                        assertEquals(MultiLevel.MUTABLE_DV, d.variableInfoContainer().getPreviousOrInitial()
                                .getProperty(CONTEXT_IMMUTABLE));
                        assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_HC_AFTER_MARK_DV, CONTEXT_IMMUTABLE);

                        String expectLinked = d.iteration() <= 1 ? "this:-1" : "this:4";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 2, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof This) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
                        assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, EXTERNAL_IMMUTABLE);
                    }
                }
            }
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertEquals("this", fr.scope.toString());
                    assertNotNull(fr.scopeVariable);
                    assertEquals("this", fr.scopeVariable.toString());
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, CONTEXT_MODIFIED);
                        assertEquals("1.0.1-E", d.variableInfo().getAssignmentIds().getLatestAssignment());
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, CONTEXT_MODIFIED);
                        assertEquals("1:M", d.variableInfo().getAssignmentIds().getLatestAssignment());
                    }
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("this.t:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("copy".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() >= 2,
                        d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
                assertDv(d.p(0), 3, DV.FALSE_DV, CONTEXT_MODIFIED);
                assertDv(d.p(0), 3, DV.FALSE_DV, MODIFIED_VARIABLE);
            }
            if ("set".equals(d.methodInfo().name)) {
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            }
            if ("get".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, MODIFIED_METHOD);
                String expect = d.iteration() <= 1 ? "<m:get>" : "/*inline get*/t$0";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("isSet".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, MODIFIED_METHOD);
                String expect = d.iteration() <= 1 ? "<m:isSet>" : "/*inline isSet*/null!=t$0";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, MODIFIED_OUTSIDE_METHOD);
                assertDv(d, DV.FALSE_DV, FINAL);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Basics_21".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, IMMUTABLE);
                assertDv(d, 3, MultiLevel.INDEPENDENT_HC_DV, INDEPENDENT);
            }
        };

        testClass("Basics_21", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
