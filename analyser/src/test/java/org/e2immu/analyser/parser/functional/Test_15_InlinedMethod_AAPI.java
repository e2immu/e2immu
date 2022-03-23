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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;
import java.util.stream.Collectors;

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
                if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertEquals("i+r", inlinedMethod.toString());
                } else {
                    fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
            }
            if ("difference31".equals(d.methodInfo().name)) {
                assertEquals("2+instance type int-(instance type int)", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name)) {
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
    public void test_8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("numParameters".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:size>"
                                : "(inspectionProvider.getMethodInspection(this).b?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).size()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("parameters".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:getParameters>"
                                : "inspectionProvider.getMethodInspection(this).b?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "(<m:equals>||<m:equals>)&&(<m:equals>||<m:isLong>)";
                            case 1, 2 -> "(\"equals\".equals(name)||\"wait\".equals(name))&&(\"equals\".equals(name)||<m:isLong>)";
                            default -> "(\"equals\".equals(name)||\"wait\".equals(name))&&(\"equals\".equals(name)||(instance type boolean&&inspectionProvider.getMethodInspection(this).b?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).get(0).parameterizedType.s.startsWith(\"x\")||null==(instance type boolean&&inspectionProvider.getMethodInspection(this).b?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).get(0).parameterizedType.s)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "1==<m:size>"
                            : "1==(inspectionProvider.getMethodInspection(this).b&&0!=(inspectionProvider.getMethodInspection(this).b?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).size()?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()).size()";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isLong".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:isLong>" : "s.startsWith(\"x\")||null==s";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("getParameters".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:getParameters>" : "b$0?List.of(new ParameterInfo(new ParameterizedType(\"i\"),0)):List.of()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("parameterizedType".equals(d.fieldInfo().name)) {
                assertEquals("parameterizedType", d.fieldAnalysis().getValue().toString());
                assertDv(d, DV.TRUE_DV, Property.FINAL);
            }
        };
        testClass("InlinedMethod_8", 1, 5, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }

    @Test
    public void test_10() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("find".equals(d.methodInfo().name)) {
                // the visualisation is f.contains(s) "because" (we should improve on this) this is an inlined method, with f as parameter
                String expected = d.iteration() <= 1 ? "<m:find>"
                        : "s.length()<=1?s.length()<=0?InlinedMethod_10.find(stream,s):stream.filter(ff.equals(s)).findAny().orElse(s):stream.filter(f.contains(s)).findFirst().orElse(null)";
                if (d.iteration() >= 2) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("s,stream", inlinedMethod.getVariablesOfExpression().stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                    }
                }
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlinedMethod_10", 0, 3, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_11() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("variables".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:variables>" : "this.subElements().stream().flatMap(e.variables().stream()).collect(Collectors.toList())";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 2) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("this", inlinedMethod.getVariablesOfExpression().stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                        if (inlinedMethod.expression() instanceof MethodCall mc1) {
                            if (mc1.object instanceof MethodCall mc2) {
                                assertEquals(1, mc2.parameterExpressions.size());
                                if (mc2.parameterExpressions.get(0) instanceof InlinedMethod inlinedMethod2) {
                                    assertEquals("e", inlinedMethod2.getVariablesOfExpression().stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                                } else fail();
                            } else fail();
                        } else fail();
                    }
                }
            }
            if ("compare".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 2 ? "<m:compare>"
                        : "e1.subElements().stream().flatMap(e.variables().stream()).collect(Collectors.toList()).stream().map(Variable::name).sorted().collect(Collectors.joining(\",\")).compareTo(e2.subElements().stream().flatMap(e.variables().stream()).collect(Collectors.toList()).stream().map(Variable::name).sorted().collect(Collectors.joining(\",\")))";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 3) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("e1,e2", inlinedMethod.getVariablesOfExpression().stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                        if (inlinedMethod.expression() instanceof MethodCall mc1) {
                            assertEquals(1, mc1.parameterExpressions.size());
                            if (mc1.parameterExpressions.get(0) instanceof MethodCall mc2) {
                                if (mc2.object instanceof MethodCall mc3) {
                                    assertEquals("sorted", mc3.methodInfo.name);
                                    if (mc3.object instanceof MethodCall mc4) {
                                        assertEquals("map", mc4.methodInfo.name);
                                        if (mc4.object instanceof MethodCall mc5) {
                                            assertEquals("stream", mc5.methodInfo.name);
                                            if (mc5.object instanceof MethodCall mc6) {
                                                assertEquals("collect", mc6.methodInfo.name);
                                                if (mc6.object instanceof MethodCall mc7) {
                                                    assertEquals("flatMap", mc7.methodInfo.name);
                                                    assertEquals(1, mc7.parameterExpressions.size());
                                                    if (mc7.parameterExpressions.get(0) instanceof InlinedMethod inlinedMethod2) {
                                                        assertEquals("e", inlinedMethod2.getVariablesOfExpression().stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                                                    } else fail();
                                                } else fail();
                                            } else fail();
                                        } else fail();
                                    } else fail();
                                } else fail();
                            } else fail();
                        } else fail();
                    }
                }
            }
        };
        testClass("InlinedMethod_11", 1, 5, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test_12() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:find>&&<m:find>"
                        : "Arrays.stream({s1}).anyMatch(s.contains(\"magic\"))&&Arrays.stream({s1,s2}).anyMatch(s.contains(\"magic\"))";
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
                            : "new Collector<>(){public Supplier<UpgradableBooleanMap<T>> supplier(){return UpgradableBooleanMap::new;}public BiConsumer<UpgradableBooleanMap<T>,Entry<T,Boolean>> accumulator(){return (map,e)->{... debugging ...};}public BinaryOperator<UpgradableBooleanMap<T>> combiner(){return UpgradableBooleanMap::putAll;}public Function<UpgradableBooleanMap<T>,UpgradableBooleanMap<T>> finisher(){return t->t;}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT,Characteristics.IDENTITY_FINISH,Characteristics.UNORDERED);}}";
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
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("typesReferenced".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertFalse(d.methodInfo().hasImplementations());

                if (d.iteration() >= 3) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("List.of().stream().flatMap(e.typesReferenced().map.entrySet().stream()).collect(new Collector<>(){public Supplier<UpgradableBooleanMap<T>> supplier(){return UpgradableBooleanMap::new;}public BiConsumer<UpgradableBooleanMap<T>,Entry<T,Boolean>> accumulator(){return (map,e)->{... debugging ...};}public BinaryOperator<UpgradableBooleanMap<T>> combiner(){return UpgradableBooleanMap::putAll;}public Function<UpgradableBooleanMap<T>,UpgradableBooleanMap<T>> finisher(){return t->t;}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT,Characteristics.IDENTITY_FINISH,Characteristics.UNORDERED);}})",
                                inlinedMethod.expression().toString());
                        // no "this", because subElements is inlined!
                        assertEquals("[]", inlinedMethod.getVariablesOfExpression().toString());
                    } else fail();
                }
            }
            if ("supplier".equals(d.methodInfo().name)) {
                assertEquals("$3", d.methodInfo().typeInfo.simpleName);
                assertEquals("UpgradableBooleanMap::new", d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("InlinedMethod_13", 0, 7, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_14() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("subElements".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().hasImplementations());
                assertEquals("nullable instance type List<Expression>", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("typesReferenced".equals(d.methodInfo().name) && "Expression".equals(d.methodInfo().typeInfo.simpleName)) {
                assertFalse(d.methodInfo().hasImplementations());

                if (d.iteration() >= 3) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("this.subElements().stream().flatMap(e.typesReferenced().map.entrySet().stream()).collect(new Collector<>(){public Supplier<UpgradableBooleanMap<T>> supplier(){return UpgradableBooleanMap::new;}public BiConsumer<UpgradableBooleanMap<T>,Entry<T,Boolean>> accumulator(){return (map,e)->{... debugging ...};}public BinaryOperator<UpgradableBooleanMap<T>> combiner(){return UpgradableBooleanMap::putAll;}public Function<UpgradableBooleanMap<T>,UpgradableBooleanMap<T>> finisher(){return t->t;}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT,Characteristics.IDENTITY_FINISH,Characteristics.UNORDERED);}})",
                                inlinedMethod.expression().toString());
                        // "this", because subElements is not inlined!
                        assertEquals("[this]", inlinedMethod.getVariablesOfExpression().toString());
                    } else fail();
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals("[new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,map]", d.fieldAnalysis().getValue().toString());
            }
        };
        testClass("InlinedMethod_14", 0, 8, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}