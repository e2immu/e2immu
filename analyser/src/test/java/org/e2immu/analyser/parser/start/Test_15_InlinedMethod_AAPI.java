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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
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
        testClass("InlinedMethod_10", 0, 1, new DebugConfiguration.Builder()
                //   .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
    @Test
    public void test_11() throws IOException {
        testClass("InlinedMethod_11", 0, 1, new DebugConfiguration.Builder()
                .build());
    }
}
