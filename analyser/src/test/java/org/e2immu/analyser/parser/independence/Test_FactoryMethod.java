
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

package org.e2immu.analyser.parser.independence;


import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_FactoryMethod extends CommonTestRunner {

    public Test_FactoryMethod() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("of2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "f:-1,tt:-1" : "f:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "f:0,t:-1,tt:-1" : "f:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "t:-1,tt:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("of".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "ts".equals(pi.name)) {
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "f:-1" : "f:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "f:0,ts:-1" : "f:0,ts:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "ts:-1" : "ts:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("getStream".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 2 ? "this.list:-1" : "this.list:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("copy".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() < 2 ? "this.list:-1" : "this.list:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("of".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
        };

        // FIXME one error, still to be implemented
        testClass("FactoryMethod_0", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
