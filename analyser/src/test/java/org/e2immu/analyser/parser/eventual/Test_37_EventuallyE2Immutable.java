
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

package org.e2immu.analyser.parser.eventual;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_37_EventuallyE2Immutable extends CommonTestRunner {

    public Test_37_EventuallyE2Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0 -> "null==<f:t>";
                        case 1 -> "null==<f*:t>";
                        default -> "null==t";
                    };
                    assertEquals(expect, d.statementAnalysis().stateData().getPrecondition().expression().toString());
                    assertEquals(expect, d.statementAnalysis().methodLevelData()
                            .combinedPrecondition.get().expression().toString());
                }
                if ("1".equals(d.statementId())) {
                    if (d.iteration() <= 1) {
                        assertNull(d.statementAnalysis().stateData().getPrecondition());
                    } else {
                        assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                    }
                    String expect = switch (d.iteration()) {
                        case 0 -> "null==<f:t>";
                        case 1 -> "null==<f*:t>";
                        default -> "null==t";
                    };
                    assertEquals(expect, d.statementAnalysis().methodLevelData().combinedPrecondition.get().expression().toString());
                }
            }
            if ("set2".equals(d.methodInfo().name)) {
                String expectPrecondition = d.iteration() <= 1 ? "<precondition>" : "null==t";
                assertEquals(expectPrecondition, d.statementAnalysis()
                        .stateData().getPrecondition().expression().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_0".equals(d.typeInfo().simpleName)) {
                String expect = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expect, d.typeAnalysis().getApprovedPreconditionsE2().toString());
                assertEquals(d.iteration() >= 2, d.typeAnalysis().approvedPreconditionsStatus(true).isDone());
                assertDv(d, 2, MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getT".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:getT>" : "t$0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("EventuallyE2Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                String expectPrecondition = d.iteration() <= 1 ? "<precondition>&&<precondition>" : "true";
                assertEquals(expectPrecondition, d.statementAnalysis()
                        .stateData().getPrecondition().expression().toString());
                assertEquals(d.iteration() >= 2, d.statementAnalysis().stateData().preconditionIsFinal());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertTrue(d.iteration() > 0);

                    String expected = d.iteration() <= 1 ? "<f:t>" : "nullable instance type T";
                    assertEquals(expected, d.currentValue().toString());
                    String expectedDelay = d.iteration() == 1 ? "?" : "this.t:0";
                    assertEquals(expectedDelay, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getT".equals(d.methodInfo().name)) {
                String expect = switch (d.iteration()) {
                    case 0 -> "null!=<f:t>";
                    case 1 -> "null!=<vp:t:initial:this.t@Method_setT_1;state:this.t@Method_setT_2;values:this.t@Field_t>";
                    default -> "null!=t";
                };
                assertEquals(expect, d.methodAnalysis().getPrecondition().expression().toString());
            }
            if ("setT".equals(d.methodInfo().name)) {
                String expect = switch (d.iteration()) {
                    case 0 -> "null==<f:t>";
                    case 1 -> "null==<f*:t>";
                    default -> "null==t";
                };
                assertEquals(expect, d.methodAnalysis().getPrecondition().expression().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                String expectLinked = d.iteration() <= 1 ? "t:0" : "";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_1".equals(d.typeInfo().simpleName)) {
                assertEquals("{}", d.typeAnalysis().getApprovedPreconditionsE1().toString());
                String expect2 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expect2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
                assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsStatus(true).isDone());
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("EventuallyE2Immutable_1", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }

            if ("copyInto".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 2, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("copyInto".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof ParameterInfo other && "other".equals(other.name)) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        testClass("EventuallyE2Immutable_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    Use the EXTERNAL_IMMUTABLE property on this, in conjunction with NEXT_CONTEXT_IMMUTABLE and
    variableOccursInEventuallyImmutableContext, to detect that "this" is in the wrong state after the first call.
     */

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("error1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    String expectCondition = switch (d.iteration()) {
                        case 0, 1 -> "!<m:isSet>";
                        default -> "null==other.t";
                    };
                    assertEquals(expectCondition, d.condition().output(Qualification.FULLY_QUALIFIED_NAME).toString());
                }
            }

            if ("error3".equals(d.methodInfo().name)) {
                // 0: setT(t), precondition created by StatementAnalysisImpl.applyPrecondition,
                // originating from EvaluatePreconditionFromMethod
                if ("0".equals(d.statementId())) {
                    assertEquals("CM{parent=CM{}}", d.localConditionManager().toString());
                    String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                            : "Precondition[expression=null==t, causes=[methodCall:setT]]";
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
                    assertEquals(expected, d.statementAnalysis().methodLevelData().combinedPrecondition.get().toString());
                }
                // 1: setT(t), again
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() <= 1
                            ? "CM{pc=Precondition[expression=<precondition>, causes=[]];parent=CM{}}"
                            : "CM{pc=Precondition[expression=null==t, causes=[methodCall:setT]];parent=CM{}}";
                    assertEquals(expected, d.localConditionManager().toString());
                    assertEquals("true", d.absoluteState().toString()); // the absolute state does not take precondition into account
                    if (d.iteration() >= 4) assertNotNull(d.haveError(Message.Label.EVENTUAL_BEFORE_REQUIRED));
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("error3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 3, MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 3, MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_3".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("EventuallyE2Immutable_3", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("error4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "other".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<p:other>"
                                : "nullable instance type EventuallyE2Immutable_4<T>/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 4, MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<p:other>"
                                : "nullable instance type EventuallyE2Immutable_4<T>/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 4, MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                if (d.iteration() >= 3) {
                    assertEquals("Precondition[expression=null==t, causes=[escape]]", d.methodAnalysis().getPreconditionForEventual().toString());
                }
            }
            if ("error4".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expectPc = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 1 -> "Precondition[expression=<precondition>&&<precondition>&&<precondition>&&<precondition>, causes=[]]";
                    // why has other. gone from the preconditions???? in <f:t>
                    case 2 -> "Precondition[expression=null!=t&&null==<f:t>&&null==<f:t>, causes=[methodCall:getT, methodCall:getT]]";
                    default -> "Precondition[expression=null!=t, causes=[methodCall:setT, methodCall:getT, methodCall:setT]]";
                };
                assertEquals(expectPc, d.methodAnalysis().getPreconditionForEventual().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_4".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("EventuallyE2Immutable_4", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("EventuallyE2Immutable_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    in this example we combine the eventual work, based on preconditions,
    with the companion work/instance state changes

    An additional complication is that the precondition discovered is !data.isEmpty(),
    while the instance state says that this.size()>=data.size().
    We should be able to conclude from this that after statement 2 in initialise, !set.isEmpty()
     */
    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:set>" :
                                "instance type HashSet<T>/*this.size()>=data.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().stateData().preconditionIsFinal());
                    } else {
                        assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                        assertEquals("!data.isEmpty()&&set.isEmpty()",
                                d.statementAnalysis().methodLevelData().combinedPrecondition.get().expression().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<precondition>" : "!set.isEmpty()";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().expression().toString());
            }
            if ("initialize".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<precondition>" : "set.isEmpty()";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().expression().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) {
                    assertTrue(d.fieldAnalysis().isTransparentType().isDelayed());
                } else {
                    assertEquals(DV.FALSE_DV, d.fieldAnalysis().isTransparentType());
                }
            }
        };

        testClass("EventuallyE2Immutable_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    The slightly less complicated version of test 6: Here we only have size() not isEmpty.

    This tests uses size()>0 instead of size() !=0, because that expression is not (yet) recognized
    by Filter.individualFieldClause. Similarly, for consistency we use <= 0 instead of != 0 in the precondition.

    Dedicated code in MethodCallIncompatibleWithPrecondition detects the modification wrt the precondition.
     */
    @Test
    public void test_7() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:set>" :
                                "instance type HashSet<T>/*this.size()>=data.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().stateData().preconditionIsFinal());
                    } else {
                        assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                        assertEquals("data.size()>=1&&set.size()<=0",
                                d.statementAnalysis().methodLevelData().combinedPrecondition.get().expression().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("set.size()>=1",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    // need to wait for transparent types
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("set.size()<=0",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_7", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    8 is a bit more complicated than 7: it relies on the invariants of size() to resolve !=0 into >0.
     */
    @Test
    public void test_8() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:set>" :
                                "instance type HashSet<T>/*this.size()>=data.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().stateData().preconditionIsFinal());
                    } else {
                        assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                        assertEquals("0!=data.size()&&0==set.size()",
                                d.statementAnalysis().methodLevelData().combinedPrecondition.get().expression().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("0!=set.size()",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    // need to wait for transparent types
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("0==set.size()",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test_9() throws IOException {
        testClass("EventuallyE2Immutable_9", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_10() throws IOException {
        testClass("EventuallyE2Immutable_10", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // continuation of _3
    @Test
    public void test_11() throws IOException {
        testClass("EventuallyE2Immutable_11", 2, 0, new DebugConfiguration.Builder()
                .build());
    }
}