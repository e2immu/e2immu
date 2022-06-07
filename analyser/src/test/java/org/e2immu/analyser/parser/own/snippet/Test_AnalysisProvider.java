
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
import org.e2immu.analyser.model.variable.ReturnVariable;
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
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "NOT_INVOLVED_DV".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.isStatic());
                    if ("9".equals(d.statementId())) { // return dynamicBaseValue
                        String linked = switch (d.iteration()) {
                            case 0 -> "AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,baseValue:-1,bestType:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1";
                            case 1, 2 -> "AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,baseValue:-1,bestType:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1";
                            default -> "AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,baseValue:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1";
                            //   default -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,baseValue:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.arrays:-1,parameterizedType.parameters:-1,parameterizedType:-1,return defaultImmutable:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0",
                                d.variableInfo().getLinkedVariables().toString());
                        String ex = switch (d.iteration()) {
                            case 0 -> "<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 1, 2, 3 -> "parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default -> "parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        assertEquals(ex, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String linked = d.iteration() <= 3
                                ? "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,dynamicValue:-1,unboundIsMutable:-1"
                                : "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:1,AnalysisProvider_0.NOT_INVOLVED_DV:1,dynamicValue:1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String ex = switch (d.iteration()) {
                            case 0 -> "<null-check>?<s:DV>:<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 1, 2 -> "<null-check>?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 3 -> "null==`parameterizedType.bestTypeInfo`?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default -> "null==`parameterizedType.bestTypeInfo`?`dynamicValue.value`>=`unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV.value`?dynamicValue:unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        assertEquals(ex, d.currentValue().toString());
                    }
                    if ("8".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,bestType:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                            case 3 -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                            default-> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String ex = switch (d.iteration()) {
                            case 0 -> "<m:isAtLeastE2Immutable>&&!<m:isEmpty>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:<null-check>?<s:DV>:<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 1, 2 -> "<m:isAtLeastE2Immutable>&&!<m:isEmpty>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:<null-check>?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 3 -> "!parameterizedType.parameters.isEmpty()&&<m:isAtLeastE2Immutable>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:null==`parameterizedType.bestTypeInfo`?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default -> "!parameterizedType.parameters.isEmpty()&&<m:isAtLeastE2Immutable>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:null==`parameterizedType.bestTypeInfo`?`dynamicValue.value`>=`unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV.value`?dynamicValue:unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        assertEquals(ex, d.currentValue().toString());
                    }
                    if ("9".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,bestType:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.arrays:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                            default -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.arrays:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
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
                        ? "Precondition[expression=<precondition>, causes=[escape]]"
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