
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
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "EFFECTIVELY_E1IMMUTABLE_DV".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("9".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("doSum".equals(d.variableName())) {
                    if ("8.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:DV>";
                            case 1, 2, 3 -> "<m:immutableCanBeIncreasedByTypeParameters>";
                            default ->
                                    "this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).immutableCanBeIncreasedByTypeParameters()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("baseValue".equals(d.variableName())) {
                    if ("5".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2 -> "<s:DV>";
                            case 3 -> "<m:getProperty>";
                            default ->
                                    "this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            case 1, 2, 3, 4 ->
                                    "parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<return value>";
                            default ->
                                    "parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:<return value>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("6".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 ->
                                    "<f:parameterizedType.arrays>>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<null-check>?<s:DV>:<m:isDelayed>?<s:DV>:<return value>";
                            case 1, 2 ->
                                    "parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:<null-check>?<m:max>:<m:isDelayed>?<s:DV>:<return value>";
                            case 3 ->
                                    "parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:null==`parameterizedType.bestTypeInfo`?<m:max>:<m:isDelayed>?<s:DV>:<return value>";
                            case 4 ->
                                    "parameterizedType.arrays>=1?<f:EFFECTIVELY_E1IMMUTABLE_DV>:null==`parameterizedType.bestTypeInfo`?<m:max>:`this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE).value`<0?this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE):<return value>";
                            default ->
                                    "parameterizedType.arrays>=1?AnalysisProvider_0.EFFECTIVELY_E1IMMUTABLE_DV:null==`parameterizedType.bestTypeInfo`?`dynamicValue.value`>=`unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV.value`?dynamicValue:unboundIsMutable?AnalysisProvider_0.NOT_INVOLVED_DV:AnalysisProvider_0.EFFECTIVELY_E2IMMUTABLE_DV:`this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE).value`<0?this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE):<return value>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("dynamicBaseValue".equals(d.variableName())) {
                    if ("7".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3 -> "<s:DV>";
                            case 4 -> "<vp:DV:initial@Field_EFFECTIVELY_E2IMMUTABLE_DV;initial@Field_NOT_INVOLVED_DV>";
                            default ->
                                    "`dynamicValue.value`>=`this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE).value`?dynamicValue:this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$5".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = switch (d.iteration()) {
                    case 0, 1 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 2 -> "Precondition[expression=<inline>, causes=[]]";
                    default -> "Precondition[expression=1==`cause`.priority, causes=[methodCall:containsCauseOfDelay]]";
                };
                assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
            }
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String reached = d.iteration() == 0 ? "initial_flow_value@Method_defaultImmutable_1-C" : "ALWAYS:2";
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("4".equals(d.statementId())) {
                    String reached = d.iteration() < 3 ? "initial_flow_value@Method_defaultImmutable_4-C" : "CONDITIONALLY:1";
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("7".equals(d.statementId())) {
                    String reached = d.iteration() < 4 ? "initial_flow_value@Method_defaultImmutable_7-C" : "CONDITIONALLY:1";
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("8".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2 -> "!<m:isEmpty>&&<m:isAtLeastE2Immutable>";
                        case 3, 4 -> "!parameterizedType.parameters.isEmpty()&&<m:isAtLeastE2Immutable>";
                        default ->
                                "!parameterizedType.parameters.isEmpty()&&this.isAtLeastE2Immutable(`dynamicValue.value`>=`this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE).value`?dynamicValue:this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).getProperty(AnalysisProvider_0.IMMUTABLE))";
                    };
                    assertEquals(expected,
                            d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
                if ("8.0.1".equals(d.statementId())) {
                    String reached = switch (d.iteration()) {
                        case 0 ->
                                "constructor-to-instance@Method_defaultImmutable_4-E;initial:parameterizedType.arrays@Method_defaultImmutable_1-C;initial:parameterizedType@Method_defaultImmutable_1-E;initial:this.value@Method_isDone_0-C;srv@Method_isDone";
                        case 1 ->
                                "constructor-to-instance@Method_defaultImmutable_4-E;initial:parameterizedType.arrays@Method_defaultImmutable_1-C;initial:parameterizedType@Method_defaultImmutable_1-E;initial:this.value@Method_isDone_0-C;srv@Method_bestTypeInfo;srv@Method_isDone";
                        case 2 ->
                                "cm@Parameter_name;constructor-to-instance@Method_defaultImmutable_4-E;initial:parameterizedType.arrays@Method_defaultImmutable_1-C;initial:parameterizedType@Method_defaultImmutable_1-E;initial:this.value@Method_isDone_0-C;mom@Parameter_name;srv@Method_bestTypeInfo;srv@Method_isDone";
                        case 3 ->
                                "cm@Parameter_name;de:baseValue@Method_defaultImmutable_6-E;mom@Parameter_name;srv@Method_bestTypeInfo";
                        case 4 -> "initial@Field_EFFECTIVELY_E2IMMUTABLE_DV;initial@Field_NOT_INVOLVED_DV";
                        default -> "CONDITIONALLY:1";
                    };
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("8.0.2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2, 3 -> "<m:valueIsTrue>";
                        case 4 -> "<c:boolean>";
                        default ->
                                "1==`this.getTypeAnalysisNullWhenAbsent(`parameterizedType.bestTypeInfo`).immutableCanBeIncreasedByTypeParameters().value`";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                    String reached = d.iteration() < 5 ? "initial_flow_value@Method_defaultImmutable_8.0.2-C" : "CONDITIONALLY:1";
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("containsCauseOfDelay".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2
                        ? "Precondition[expression=<precondition>, causes=[escape]]"
                        : "Precondition[expression=1==cause.priority, causes=[escape]]";
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("IMMUTABLE".equals(d.fieldInfo().name)) {
                assertEquals("new Property(){}", d.fieldAnalysis().getValue().toString());
            }
            if ("EFFECTIVELY_E1IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                assertEquals(d.iteration() > 0, d.fieldAnalysis().getLinkedVariables().isDone());
                assertDv(d, 5, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d, 5, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                String expected = d.iteration() < 4 ? "<f:EFFECTIVELY_E1IMMUTABLE_DV>" : "instance type DV";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                assertDv(d, 2, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, 3, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 0, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);
            }
        };
        testClass("AnalysisProvider_0", 0, 5,
                new DebugConfiguration.Builder()
                   //     .addStatementAnalyserVisitor(statementAnalyserVisitor)
                   //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                   //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                    //    .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    static final String CALL_CYCLE = "apply,apply,defaultImmutable,defaultImmutable,getTypeAnalysisNullWhenAbsent,highPriority,isAtLeastE2Immutable,sumImmutableLevels";

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            if ("defaultImmutable".equals(d.methodInfo().name) && numParams == 3) {
                if ("typeAnalysis".equals(d.variableName())) {
                    if ("5".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("7".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("8.0.0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 ->
                                    "AnalysisProvider_1.EFFECTIVELY_E1IMMUTABLE_DV:-1,AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.IMMUTABLE:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,bestType:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType.arrays:-1,parameterizedType.parameters:-1,parameterizedType:-1,this:-1,unboundIsMutable:-1";
                            case 1, 2 ->
                                    "AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.IMMUTABLE:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,bestType:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType:-1,this:-1";
                            case 3 ->
                                    "AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.IMMUTABLE:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,this:-1";
                            case 4 ->
                                    "AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,this:-1";
                            default -> "baseValue:2,doSum:2,dynamicBaseValue:2,dynamicValue:2,this:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        /*
                        True because linked to dynamicBaseValue, which is modified given that the call isAtLeastE2Immutable()
                        has a modifying parameter.
                         */
                        assertDv(d, 5, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("apply".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId()); // pt -> defaultImmutable(pt, true)
                if ("typeAnalysis".equals(d.variableName())) {
                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    String linked = switch (d.iteration()) {
                        case 0 -> "NOT_YET_SET";
                        case 1, 2 ->
                                "AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.IMMUTABLE:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,bestType:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,parameterizedType:-1,pt:-1,this:-1";
                        case 3 ->
                                "AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.IMMUTABLE:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,pt:-1,this:-1";
                        case 4 ->
                                "AnalysisProvider_1.EFFECTIVELY_E2IMMUTABLE_DV:-1,AnalysisProvider_1.NOT_INVOLVED_DV:-1,baseValue:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,pt:-1,this:-1";
                        case 5, 6, 7 -> "baseValue:-1,doSum:-1,dynamicBaseValue:-1,dynamicValue:-1,pt:-1,this:-1";
                        default -> "baseValue:2,doSum:2,dynamicBaseValue:2,dynamicValue:2,this:2";
                    };
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());

                    assertFalse(d.variableInfoContainer().hasMerge());
                    assertDv(d, 8, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
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
            if ("immutableCanBeIncreasedByTypeParameters".equals(d.methodInfo().name) && "TypeAnalysis".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("isAtLeastE2Immutable".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), DV.TRUE_DV, Property.MODIFIED_VARIABLE); // default value
            }
        };
        testClass("AnalysisProvider_1", 0, 6,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
                    String expected = d.iteration() < 4 ? "<m:reduce>"
                            : "parameterizedType.parameters.stream().map(instance type $2).reduce(AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,DV::min)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if ("paramValue".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2, 3 ->
                                    "AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,parameterizedType.parameters:-1,parameterizedType:-1";
                            default -> "parameterizedType.parameters:4,parameterizedType:4";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "parameterizedType".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2, 3 ->
                                    "AnalysisProvider_2.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,paramValue:-1,parameterizedType.parameters:-1";
                            default -> "paramValue:4,parameterizedType.parameters:4";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof This thisVar && "$2".equals(thisVar.typeInfo.simpleName)) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String linkedRv = d.iteration() == 0 ? "NOT_YET_SET" : "";
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("", d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$2", d.methodInfo().typeInfo.simpleName);
                assertEquals("0", d.statementId());
                String delay = d.iteration() == 0 ? "link@NOT_YET_SET" : "";
                assertEquals(delay, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                // after breaking delay in field analyser
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
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
                assertDv(d, 2, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("AnalysisProvider_2", 0, 1,
                new DebugConfiguration.Builder()
                  //      .addEvaluationResultVisitor(evaluationResultVisitor)
                 //       .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                   //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                   //     .addStatementAnalyserVisitor(statementAnalyserVisitor)
                   //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                    //    .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    // cycle of 3, stripped
    // _4 is simpler, there we deal with a direct method call.
    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("b".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() < 3 ? "<m:reduce>"
                            : "b0.parameters.stream().map(instance type $2).reduce(AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,DV::min)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("b".equals(d.methodInfo().name)) {
                if ("paramValue".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 ->
                                    "AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,b0.parameters:-1,b0:-1";
                            default -> "b0.parameters:4,b0:4";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                } else if (d.variable() instanceof This) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                } else if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String linked = "AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                } else if (d.variable() instanceof ParameterInfo pi && "b0".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2 ->
                                    "AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,b0.parameters:-1,paramValue:-1";
                            default -> "b0.parameters:4,paramValue:4";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                } else if (d.variable() instanceof ParameterInfo pi && "n".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                        String linked = "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                } else if (d.variable() instanceof FieldReference fr && "EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String linked = d.iteration() < 3 ? "b0.parameters:-1,b0:-1,paramValue:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                } else if (d.variable() instanceof FieldReference fr && "parameters".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String linked = d.iteration() < 3
                                ? "AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV:-1,b0:-1,paramValue:-1"
                                : "b0:2,paramValue:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                } else fail("? " + d.variableName());
            }
            if ("apply".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    // can only be solved when "a" is fully done, which happens after "b" is fully done
                    String expected = d.iteration() < 6 ? "<m:a>"
                            : "this.sumImmutableLevels(`a0.parameters`.stream().map(instance type $2).reduce(`AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV`,DV::min))";
                    assertEquals(expected, d.currentValue().toString());
                    String linked = d.iteration() < 6 ? "pt:-1,this:-1" : "this:2";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("b".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertFalse(d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().isDelayed());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() < 3,
                            d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().isDelayed());
                }
            }
        };
        String callCycle = "a,apply,b,sumImmutableLevels";
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("a".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
            }
            if ("b".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$2", d.methodInfo().typeInfo.simpleName);
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                assertTrue(methodResolution.partOfCallCycle());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle()); // this one "breaks" the call cycle

                String expected = d.iteration() < 7 ? "<m:apply>"
                        : "/*inline apply*/this.sumImmutableLevels(`a0.parameters`.stream().map(instance type $2).reduce(`AnalysisProvider_3.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV`,DV::min))";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                // after breaking delay in field analyser
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        testClass("AnalysisProvider_3", 0, 1,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
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

                String expected = d.iteration() < 7 ? "<m:a>" : "nullable instance type DV";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("b".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));

                String expected = d.iteration() < 5 ? "<m:b>"
                        : "this.sumImmutableLevels(n<10?this.a(b0):AnalysisProvider_4.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("c".equals(d.methodInfo().name)) {
                assertEquals(callCycle, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                assertTrue(methodResolution.partOfCallCycle());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());

                String expected = d.iteration() == 0 ? "<m:c>" : "/*inline c*/this.a(c0)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV".equals(d.fieldInfo().name)) {
                assertDv(d, 4, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DV".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
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