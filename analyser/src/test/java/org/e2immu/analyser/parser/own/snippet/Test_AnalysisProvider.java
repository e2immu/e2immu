
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
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
                            case 1, 2, 3, 4 -> "parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default -> "parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        assertEquals(ex, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String linked = d.iteration() <= 4
                                ? "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,dynamicValue:-1,unboundIsMutable:-1"
                                : "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:1,AnalysisProvider_0.NOT_INVOLVED_DV:1,dynamicValue:1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String ex = switch (d.iteration()) {
                            case 0 -> "<null-check>?<s:DV>:<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 1, 2 -> "<null-check>?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 3, 4 -> "null==`parameterizedType.bestTypeInfo`?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default -> "null==`parameterizedType.bestTypeInfo`?`dynamicValue.value`>=`unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV.value`?dynamicValue:unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        assertEquals(ex, d.currentValue().toString());
                    }
                    // problems start at statement 8
                    if ("8".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,bestType:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                            case 3, 4 -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1,unboundIsMutable:-1";
                            default -> "AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:0,AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_0.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,AnalysisProvider_0.IMMUTABLE:-1,AnalysisProvider_0.NOT_INVOLVED_DV:-1,baseValue:0,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,typeAnalysis:-1";
                        };
                        //  assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String ex = switch (d.iteration()) {
                            case 0 -> "<m:isAtLeastE2Immutable>&&!<m:isEmpty>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:<null-check>?<s:DV>:<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 1, 2 -> "<m:isAtLeastE2Immutable>&&!<m:isEmpty>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:<null-check>?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 3, 4 -> "!parameterizedType.parameters.isEmpty()&&<m:isAtLeastE2Immutable>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:null==`parameterizedType.bestTypeInfo`?<m:max>:parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default -> "!parameterizedType.parameters.isEmpty()&&<m:isAtLeastE2Immutable>?<m:valueIsTrue>?<m:isDelayed>?<m:reduce>:<m:sumImmutableLevels>:<m:isDelayed>?<s:DV>:<return value>:<m:isDelayed>?<s:DV>:null==`parameterizedType.bestTypeInfo`?`dynamicValue.value`>=`unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV.value`?dynamicValue:unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        // assertEquals(ex, d.currentValue().toString());
                        // still delays at 22
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
                    case 1, 2 -> "Precondition[expression=<inline>, causes=[]]";
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
                        //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() <= 2 ? "<m:reduce>" : "parameterizedType.parameters.stream().map(/*inline apply*/this.defaultImmutable(pt)).reduce(AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,DV::min)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if ("paramValue".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 -> "AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1";
                            case 3, 4, 5, 6, 7, 8 -> "parameterizedType.parameters:-1,parameterizedType:-1";
                            default -> "parameterizedType.parameters:3,parameterizedType:3";
                        };

                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "parameterizedType".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 -> "AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,paramValue:-1,parameterizedType.parameters:-1,this:-1";
                            case 3, 4, 5, 6, 7, 8 -> "paramValue:-1,parameterizedType.parameters:-1";
                            default -> "paramValue:3,parameterizedType.parameters:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$2", d.methodInfo().typeInfo.simpleName);
                assertEquals("0", d.statementId());
                String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                if (d.variable() instanceof ParameterInfo pi && "parameterizedType".equals(pi.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "pt".equals(pi.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof This) {
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String linkedRv = switch (d.iteration()) {
                        case 0 -> "pt:-1,this:-1";
                        case 1, 2, 3 -> "this:-1";
                        default -> "this:3";
                    };
                    assertEquals(linkedRv, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String props = switch (d.iteration()) {
                        case 0 -> "parameterizedType={modified in context=link@NOT_YET_SET, not null in context=nullable:1}, this={read=true:1}";
                        case 1 -> "parameterizedType={modified in context=initial:AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV@Method_defaultImmutable_0-C;initial:parameterizedType.parameters@Method_defaultImmutable_0-C;initial@Field_causes;initial@Field_value;link@NOT_YET_SET, not null in context=nullable:1}, this={read=true:1}";
                        case 2 -> "parameterizedType={modified in context=initial@Field_EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;initial@Field_bestTypeInfo;initial@Field_causes;initial@Field_parameters;initial@Field_value, not null in context=nullable:1}, this={read=true:1}";
                        case 3 -> "parameterizedType={modified in context=initial:AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV@Method_defaultImmutable_0-C;initial:parameterizedType.parameters@Method_defaultImmutable_0-C;initial@Field_EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;initial@Field_bestTypeInfo;initial@Field_causes;initial@Field_parameters;initial@Field_value;link@NOT_YET_SET, not null in context=nullable:1}, this={read=true:1}";
                        default -> "parameterizedType={modified in context=false:0, not null in context=nullable:1}, this={read=true:1}";
                    };
                    assertEquals(props, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$2", d.methodInfo().typeInfo.simpleName);
                assertEquals("0", d.statementId());
                String delay = switch (d.iteration()) {
                    case 0 -> "link@NOT_YET_SET";
                    case 1 -> "initial:AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV@Method_defaultImmutable_0-C;initial:parameterizedType.parameters@Method_defaultImmutable_0-C;initial@Field_causes;initial@Field_value;link@NOT_YET_SET";
                    case 2 -> "initial@Field_EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;initial@Field_bestTypeInfo;initial@Field_causes;initial@Field_parameters;initial@Field_value";
                    case 3 -> "initial:AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV@Method_defaultImmutable_0-C;initial:parameterizedType.parameters@Method_defaultImmutable_0-C;initial@Field_EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;initial@Field_bestTypeInfo;initial@Field_causes;initial@Field_parameters;initial@Field_value;link@NOT_YET_SET";
                    default -> "";
                };
                assertEquals(delay, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                // after breaking delay in field analyser
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("DV".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("apply".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:apply>" : "/*inline apply*/this.defaultImmutable(pt)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DV".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("AnalysisProvider_2", 0, 1,
                new DebugConfiguration.Builder()
                     //   .addEvaluationResultVisitor(evaluationResultVisitor)
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                     //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    // cycle of 3, stripped
    // _4 is simpler, there we deal with a direct method call.
    @Test
    public void test_3() throws IOException {
        String callCycle = "apply,defaultImmutable,defaultImmutable,sumImmutableLevels";
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if (numParams == 1) {
                    assertTrue(methodResolution.partOfCallCycle());
                    assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                    assertEquals(callCycle, methodResolution
                            .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                } else if (numParams == 2) {
                    assertTrue(methodResolution.partOfCallCycle());
                    assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                    assertEquals(callCycle, methodResolution
                            .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                } else fail();
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$2", d.methodInfo().typeInfo.simpleName);
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                assertTrue(methodResolution.partOfCallCycle());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle()); // this one "breaks" the call cycle

                String expected = d.iteration() <= BIG ? "<m:apply>" : "/*inline apply*/this.defaultImmutable(pt)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                // after breaking delay in field analyser
                assertDv(d, 21, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        testClass("AnalysisProvider_3", 0, 6,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    // apply as a method call rather than something hidden in a lambda
    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("c".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "c0".equals(pi.name)) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                } else if (d.variable() instanceof ReturnVariable) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                } else if (d.variable() instanceof This) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                } else fail("?: " + d.variableName());
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("c".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());

                assertEquals("", d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
            }
        };
        String callCycle = "a,b,c,sumImmutableLevels";
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("a".equals(d.methodInfo().name)) {

                assertTrue(methodResolution.partOfCallCycle());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle()); // this one "breaks" the call cycle
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));

                String expected = d.iteration() <= 3 ? "<m:a>" : "nullable instance type DV";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("b".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));

                String expected = d.iteration() <= 2 ? "<m:b>"
                        : "this.sumImmutableLevels(n<=9?this.a(b0):AnalysisProvider_4.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("c".equals(d.methodInfo().name)) {
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                assertTrue(methodResolution.partOfCallCycle());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());

                String expected = "/*inline c*/this.a(c0)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DV".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("AnalysisProvider_4", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }
}