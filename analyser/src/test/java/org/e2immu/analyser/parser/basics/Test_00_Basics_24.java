
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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_24 extends CommonTestRunner {

    public Test_00_Basics_24() {
        super(false);
    }

    // IMPORTANT: parameter "defaultValue" in map.getOrDefault is MODIFYING, there are no annotated APIs
    // 20220621: this has no bearing on the delay loop we're trying to fix. It is the "new X(a)" call
    // we focus on.
    @Test
    public void test() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() <= 5 ? "<null-check>" : "null==map.getOrDefault(pos,a)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("x".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        // after breaking
                        String expected = d.iteration() <= 5 ? "<new:X>" : "new X(a)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 5 ? "<new:X>" : "instance type X";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 3 ? "<m:getOrDefault>" : "map.getOrDefault(pos,a)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3 -> "<null-check>?\"b\":<m:getOrDefault>";
                            case 4, 5 -> "<null-check>?\"b\":map.getOrDefault(pos,a)";
                            default -> "null==map.getOrDefault(pos,a)?\"b\":map.getOrDefault(pos,a)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if (d.statementId().compareTo("1") >= 0) {
                        String expected = d.iteration() <= 5 ? "<f:map>" : "instance type Map<Integer,String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String linked = d.iteration() <= 3 ? "a:-1,pos:-1,x.s:-1,x:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0, 1, 2, 3 -> "a:-1,pos:-1,this.map:-1,x.s:0,x:-1";
                            case 4, 5 -> "a:-1,x.s:0,x:-1";
                            default -> "x.s:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                String delay = switch (d.iteration()) {
                    case 0 -> "cm@Parameter_s;constructor-to-instance@Method_method_0-E;initial:this.map@Method_method_1-C;mom@Parameter_s";
                    case 1 -> "constructor-to-instance@Method_method_0-E;initial@Field_s;mom@Parameter_s";
                    case 2, 3 -> "constructor-to-instance@Method_method_0-E;mom@Parameter_s";
                    default -> "condition@Method_method_2:M";
                };
                assertDv(d, delay, 6, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                String linked = d.iteration() <= 3 ? "a:-1,pos:-1,s:-1,this.map:-1,x:-1" : "s:0";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("X".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                String delay = d.iteration() == 0 ? "cm@Parameter_s;mom@Parameter_s" : "mom@Parameter_s";
                assertDv(d.p(0), delay, 7, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        testClass("Basics_24", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
