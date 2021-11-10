
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


import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


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
                        case 0 -> "<m:isSet>";
                        case 1 -> "null!=<f:t>";
                        default -> "null!=other.t";
                    };
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    EvaluationResult.ChangeData cd = d.findValueChangeBySubString("other");
                    assertEquals("", cd.linkedVariables().toString());
                    EvaluationResult.ChangeData cdThis = d.findValueChangeByToString("this");
                    assertEquals("", cdThis.linkedVariables().toString());

                    assertEquals(d.iteration() <= 1, d.evaluationResult().someValueWasDelayed());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = switch (d.iteration()) {
                        case 0, 1 -> "<m:set>";
                        default -> "<no return value>";
                    };
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    EvaluationResult.ChangeData cd = d.findValueChangeBySubString("other");
                    assertEquals("", cd.linkedVariables().toString());
                    EvaluationResult.ChangeData cdThis = d.findValueChangeByToString("this");
                    String expectLv = d.iteration() == 0 ? "other:-1" : "other:3";
                    assertEquals(expectLv, cdThis.linkedVariables().toString());

                    assertEquals(d.iteration() <= 1, d.evaluationResult().someValueWasDelayed());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("copy".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    String expectValue = d.iteration() <= 1 ? "<p:other>" :
                            "nullable instance type Basics_21<T>/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());

                    if ("0.0.0".equals(d.statementId())) {

                        String expectLinked = d.iteration() == 0 ? "other:0,this:-1" : "other:0,this:3";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        assertEquals(MultiLevel.MUTABLE, d.variableInfoContainer()
                                .getPreviousOrInitial().getProperty(VariableProperty.CONTEXT_IMMUTABLE));
                        assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE));
                    } else {
                        assertEquals("0", d.statementId());
                        assertEquals(MultiLevel.MUTABLE, d.variableInfoContainer().getPreviousOrInitial()
                                .getProperty(VariableProperty.CONTEXT_IMMUTABLE));
                        assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE));

                        String expectLinked = d.iteration() == 0 ? "other:0,this:-1" : "other:0,this:3";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("copy".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() >= 1,
                        d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
            if ("set".equals(d.methodInfo().name)) {
                assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("get".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("t$0", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("isSet".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("null!=t$0", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Basics_21".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass("Basics_21", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}