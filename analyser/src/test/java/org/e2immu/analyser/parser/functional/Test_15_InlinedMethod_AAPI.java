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

package org.e2immu.analyser.parser.functional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_15_InlinedMethod_AAPI extends CommonTestRunner {

    public Test_15_InlinedMethod_AAPI() {
        super(true);
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("i+r", d.evaluationResult().value().toString());
                    assertTrue(d.evaluationResult().value() instanceof Sum);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {

                if (d.methodAnalysis().getSingleReturnValue() instanceof Sum) {
                    assertEquals("i+r", d.methodAnalysis().getSingleReturnValue().toString());
                } else {
                    fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("difference31".equals(d.methodInfo().name)) {
                assertEquals("instance type int-(instance type int)", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name)) {
                // and not 0!!!
                assertEquals("instance type int-(instance type int)", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo random = typeMap.get(Random.class);
            MethodInfo nextInt = random.findUniqueMethod("nextInt", 0);
            MethodAnalysis nextIntAnalysis = nextInt.methodAnalysis.get();
            assertEquals(DV.TRUE_DV, nextIntAnalysis.getProperty(Property.MODIFIED_METHOD));
        };

        testClass("InlinedMethod_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_3_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("i+r", d.evaluationResult().value().toString());
                    assertTrue(d.evaluationResult().value() instanceof Sum);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {

                if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertEquals("/*inline plusRandom*/i+r", inlinedMethod.toString());
                } else {
                    fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("difference31".equals(d.methodInfo().name)) {
                assertEquals("2", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name)) {
                assertEquals("0", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("InlinedMethod_3_2", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<p:i>+<m:nextInt>";
                        default -> "i+r";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertTrue(d.evaluationResult().value() instanceof Sum);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:plusRandom>" : "i+r";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 1) assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof Sum);
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("difference31".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:difference31>" : "instance type int-(instance type int)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:difference11>" : "instance type int-(instance type int)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("InlinedMethod_3_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("numParameters".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<m:size>"
                                : "(`inspectionProvider.getMethodInspection(this).b`?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).size()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("parameters".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<m:getParameters>"
                                : "`inspectionProvider.getMethodInspection(this).b`?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3.0.0".equals(d.statementId())) {
                        assertValue(d,
                                it0("(<m:isLong>||<m:equals>)&&(<m:equals>||<m:equals>)"),
                                it(1, 2, "(\"equals\".equals(name)||\"wait\".equals(name))&&(\"equals\".equals(name)||<m:isLong>)"),
                                it(3, "(\"equals\".equals(name)||`((`inspectionProvider.getMethodInspection(this).b`?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).get(0)).parameterizedType.s`.startsWith(\"x\")||null==`((`inspectionProvider.getMethodInspection(this).b`?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).get(0)).parameterizedType.s`)&&(\"equals\".equals(name)||\"wait\".equals(name))"));
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    String expected = d.iteration() < 2 ? "1==<m:size>"
                            : "1==(`inspectionProvider.getMethodInspection(this).b`?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).size()";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isLong".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:isLong>" : "/*inline isLong*/null==s||s.startsWith(\"x\")";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("getParameters".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:getParameters>"
                        : "/*inline getParameters*/b$0?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("parameterizedType".equals(d.fieldInfo().name)) {
                assertEquals("parameterizedType", d.fieldAnalysis().getValue().toString());
                assertDv(d, DV.TRUE_DV, Property.FINAL);
            }
            if ("ZERO_PARAMS".equals(d.fieldInfo().name)) {
                assertEquals("Set.of(\"clone\",\"finalize\",\"getClass\",\"hashCode\",\"notify\",\"notifyAll\",\"toString\",\"wait\")", d.fieldAnalysis().getValue().toString());
                assertDv(d, DV.TRUE_DV, Property.FINAL);
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        testClass("InlinedMethod_8", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }

    @Test
    public void test_10() throws IOException {
        final String CALL_CYCLE = "find, find2, test, test";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("find".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "s".equals(pi.name)) {
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
                if (d.variable() instanceof ParameterInfo pi && "stream".equals(pi.name)) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 3 ? "s:-1,stream:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("find2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "s".equals(pi.name)) {
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
            }
            if ("find3".equals(d.methodInfo().name)) {
                if ("filtered".equals(d.variableName()) && "0".equals(d.statementId())) {
                    String linked = d.iteration() == 0 ? "stream:-1" : "stream:2";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "stream".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("find4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "stream".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("find2".equals(d.methodInfo().name)) {
                assertEquals(CALL_CYCLE, d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertFalse(d.methodInfo().methodResolution.get().ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d.p(1), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("find".equals(d.methodInfo().name)) {
                // cycle!!
                assertEquals(CALL_CYCLE, d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertTrue(d.methodInfo().methodResolution.get().ignoreMeBecauseOfPartOfCallCycle());

                String expected = d.iteration() < 4 ? "<m:find>"
                        : "s.length()<2?InlinedMethod_10.find2(stream,s,s.length()):stream.filter(/*inline test*/f.contains(s)).findFirst().orElse(null)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertFalse(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                assertDv(d.p(1), 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };
        /*
         IMPORTANT 20221031: for the moment, we stand by the decision to raise an error when a stream is finalized on a parameter.
         All 5 errors are of this type.
         */
        testClass("InlinedMethod_10", 5, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_11() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("e.variables().stream()", d.evaluationResult().value().toString());
                assertTrue(d.evaluationResult().causesOfDelay().isDone());
            }
            if ("variables".equals(d.methodInfo().name)) {
                String expected = "this.subElements().stream().flatMap(instance type $1).collect(Collectors.toList())";
                assertEquals(expected, d.evaluationResult().value().toString());
                String delay = d.iteration() == 0 ? "immutable@Record_Variable" : "";
                assertEquals("", d.evaluationResult().causesOfDelay().toString());

                EvaluationResult.ChangeData cd = d.findValueChangeByToString("this");
                assertEquals(DV.FALSE_DV, cd.getProperty(Property.CONTEXT_MODIFIED));
                assertEquals("", cd.linkedVariables().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("variables".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("this:4", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("subElements".equals(d.methodInfo().name)) {
                if ("BinaryOperator".equals(d.methodInfo().typeInfo.simpleName)) {
                    if (d.variable() instanceof ReturnVariable) {
                        String linked = d.iteration() < 2 ? "this.lhs:-1,this.rhs:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() < 2 ? "<m:of>" : "List.of(lhs,rhs)";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("apply, subElements, variables",
                        d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertTrue(d.methodInfo().methodResolution.get().ignoreMeBecauseOfPartOfCallCycle());

                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertDv(d, "mm@Method_apply", 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD_ALT_TEMP);

                String expected = d.iteration() == 0 ? "<m:apply>" : "/*inline apply*/e.variables().stream()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("variables".equals(d.methodInfo().name)) {
                assertEquals("apply, subElements, variables",
                        d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertEquals("apply, variables", d.methodInfo().methodResolution.get().callCycleSorted());
                assertFalse(d.methodInfo().methodResolution.get().ignoreMeBecauseOfPartOfCallCycle());

                // verified that the e.variables() call in apply is evaluated as recursive
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);

                String expected = "/*inline variables*/this.subElements().stream().flatMap(instance type $1).collect(Collectors.toList())";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("compare".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:compare>"
                        : "/*inline compare*/e1.subElements().stream().flatMap(instance type $1).collect(Collectors.toList()).stream().map(Variable::name).sorted().collect(Collectors.joining(\",\")).compareTo(e2.subElements().stream().flatMap(instance type $1).collect(Collectors.toList()).stream().map(Variable::name).sorted().collect(Collectors.joining(\",\")))";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertEquals("", d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertEquals("", d.methodInfo().methodResolution.get().callCycleSorted());

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("compareTo".equals(d.methodInfo().name)) {
                assertEquals("compare", d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertEquals("", d.methodInfo().methodResolution.get().callCycleSorted());

                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("subElements".equals(d.methodInfo().name)) {
                if ("Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                } else if ("BinaryOperator".equals(d.methodInfo().typeInfo.simpleName)) {
                    String expected = d.iteration() < 2 ? "<m:subElements>" : "/*inline subElements*/List.of(lhs,rhs)";
                    assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                    assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                }
            }
            if ("lhs".equals(d.methodInfo().name)) {
                assertFalse(d.allowBreakDelay());
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("lhs".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.FINAL);
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Variable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Expression".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("InlinedMethod_11", 1, 5, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_12() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:find>&&<m:find>"
                        : "Arrays.stream({s1,s2}).anyMatch(/*inline test*/s.contains(\"magic\"))&&Arrays.stream({s1}).anyMatch(/*inline test*/s.contains(\"magic\"))";
                assertEquals(expected, d.evaluationResult().value().toString());
            }
        };
        testClass("InlinedMethod_12", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("collector".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 2 ? "<new:Collector<Entry<T,Boolean>,UpgradableBooleanMap<T>,UpgradableBooleanMap<T>>>"
                            : "new Collector<>(){public Supplier<UpgradableBooleanMap<T>> supplier(){return UpgradableBooleanMap::new;}public BiConsumer<UpgradableBooleanMap<T>,Entry<T,Boolean>> accumulator(){return (map,e)->{... debugging ...};}public BinaryOperator<UpgradableBooleanMap<T>> combiner(){return UpgradableBooleanMap::putAll;}public Function<UpgradableBooleanMap<T>,UpgradableBooleanMap<T>> finisher(){return t->Objects.requireNonNull(t);}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT,Characteristics.IDENTITY_FINISH,Characteristics.UNORDERED);}}";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("UpgradableBooleanMap".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("$3".equals(d.typeInfo().simpleName)) {
                assertEquals("UpgradableBooleanMap", d.typeInfo().packageNameOrEnclosingType.getRight().simpleName);
                assertFalse(d.typeInspection().isStatic());
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("typesReferenced".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertFalse(d.methodInfo().hasImplementations());

                if (d.iteration() >= 3) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("List.of().stream().flatMap(instance type $1).collect(new Collector<>(){public Supplier<UpgradableBooleanMap<T>> supplier(){return UpgradableBooleanMap::new;}public BiConsumer<UpgradableBooleanMap<T>,Entry<T,Boolean>> accumulator(){return (map,e)->{... debugging ...};}public BinaryOperator<UpgradableBooleanMap<T>> combiner(){return UpgradableBooleanMap::putAll;}public Function<UpgradableBooleanMap<T>,UpgradableBooleanMap<T>> finisher(){return t->Objects.requireNonNull(t);}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT,Characteristics.IDENTITY_FINISH,Characteristics.UNORDERED);}})",
                                inlinedMethod.expression().toString());
                        // no "this", because subElements is inlined!
                        assertEquals("", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("supplier".equals(d.methodInfo().name)) {
                assertEquals("$3", d.methodInfo().typeInfo.simpleName);
                assertEquals("/*inline supplier*/UpgradableBooleanMap::new", d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("InlinedMethod_13", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true) // solves all the potential null pointer warnings!
                .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_14() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("subElements".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().hasImplementations());
                assertEquals("/*inline subElements*/List.of()", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("typesReferenced".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertFalse(d.methodInfo().hasImplementations());

                if (d.iteration() >= 3) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("List.of().stream().flatMap(instance type $1).collect(new Collector<>(){public Supplier<UpgradableBooleanMap<T>> supplier(){return UpgradableBooleanMap::new;}public BiConsumer<UpgradableBooleanMap<T>,Entry<T,Boolean>> accumulator(){return (map,e)->{... debugging ...};}public BinaryOperator<UpgradableBooleanMap<T>> combiner(){return UpgradableBooleanMap::putAll;}public Function<UpgradableBooleanMap<T>,UpgradableBooleanMap<T>> finisher(){return Objects::requireNonNull;}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT,Characteristics.IDENTITY_FINISH,Characteristics.UNORDERED);}})",
                                inlinedMethod.expression().toString());
                        assertEquals("", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("[new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,map]", d.fieldAnalysis().getValue().toString());
            }
        };
        testClass("InlinedMethod_14", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true) // solves all the potential null pointer warnings!
                .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_15() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("immutableConcat".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:immutableConcat>"
                        : "/*inline immutableConcat*/List.copyOf(lists.length>=1?instance type List<T>:new LinkedList<>()/*0==this.size()*/)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 1) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("lists", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("concatImmutable".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 3 ? "<m:concatImmutable>"
                        : "/*inline concatImmutable*/list1.isEmpty()?list2:list2.isEmpty()?list1:List.copyOf(instance type List<T>)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("methods".equals(d.methodInfo().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("constructors".equals(d.methodInfo().name)) {
                assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
            }
        };
        testClass("InlinedMethod_15", 0, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_16() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("findTypeName".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:findTypeName>"
                        : "/*inline findTypeName*/strings.stream().filter(/*inline test*/e.contains(\"x\")).findFirst().orElseThrow()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 0) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("strings, this", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("test".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                String expected = d.iteration() == 0 ? "<m:test>" : "/*inline test*/e.contains(\"x\")";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        testClass("InlinedMethod_16", 0, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_17() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("findTypeName".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:findTypeName>"
                        : "/*inline findTypeName*/expressions.stream().filter(/*inline test*/e instanceof TypeName).findFirst().orElseThrow()/*(TypeName)*/";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 2) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("expressions, this", inlinedMethod.variablesOfExpressionSorted());
                    } else fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
            }
            if ("test".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                String expected = d.iteration() == 0 ? "<m:test>" : "/*inline test*/e instanceof TypeName";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        testClass("InlinedMethod_17", 0, 3, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
