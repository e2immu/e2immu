
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

package org.e2immu.analyser.parser.own.snippet;

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_AnalysisProvider extends CommonTestRunner {

    public static final String X_EQUALS = "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)";

    public Test_AnalysisProvider() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$5".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "priority".equals(fr.fieldInfo.name)) {
                    if ("Cause.TYPE_ANALYSIS".equals(fr.scope.toString())) {
                        String expected = d.iteration() <= 4 ? "<f:Cause.TYPE_ANALYSIS.priority>"
                                : "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$5".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 1 -> "Precondition[expression=<inline>, causes=[]]";
                    default -> "Precondition[expression=1==`cause`.priority, causes=[methodCall:containsCauseOfDelay]]";
                };
                assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("containsCauseOfDelay".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "Precondition[expression=<m:highPriority>, causes=[escape]]"
                        : "Precondition[expression=1==cause.priority, causes=[escape]]";
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
            }
        };
        testClass("AnalysisProvider_0", 0, 5,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    static final String CALL_CYCLE = "apply,apply,defaultImmutable,defaultImmutable,getTypeAnalysisNullWhenAbsent,highPriority,isAtLeastE2Immutable,sumImmutableLevels";

    // without the NOT_INVOLVED, making a call cycle of 3 instead of 2
    @Test
    public void test_1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if (numParams == 2) {
                    assertTrue(methodResolution.partOfCallCycle());
                    assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                    assertEquals(CALL_CYCLE, methodResolution
                            .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                } else if (numParams == 3) {
                    assertTrue(methodResolution.partOfCallCycle());
                    assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                    assertEquals(CALL_CYCLE, methodResolution
                            .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                } else fail();
            }
            if ("apply".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(CALL_CYCLE, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                assertTrue(methodResolution.partOfCallCycle());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
            }
        };
        testClass("AnalysisProvider_1", 0, 6,
                new DebugConfiguration.Builder()
                        //       .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }
}