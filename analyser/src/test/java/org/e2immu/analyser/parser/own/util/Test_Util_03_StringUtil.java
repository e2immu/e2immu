
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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Util_03_StringUtil extends CommonTestRunner {

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName()) && "0".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("n<11?Integer.toString(i):<return value>", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("n<11?Integer.toString(i):n<101?i<10?\"0\"+Integer.toString(i):Integer.toString(i):<return value>",
                                d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("n<11?Integer.toString(i):n<101?i<10?\"0\"+Integer.toString(i):Integer.toString(i):n<1001?i<10?\"00\"+Integer.toString(i):i<100?\"0\"+Integer.toString(i):Integer.toString(i):<return value>",
                                d.currentValue().toString());
                        assertEquals(193, d.currentValue().getComplexity());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                if ("5".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().flowData().alwaysEscapesViaException());
                    assertEquals("n>=10001", d.conditionManagerForNextStatement().state().toString());
                    // IMPROVE has to move to preconditions (n <= 1000)
                }
            }
        };

        TypeMapVisitor typeMapVisitor = d -> {
            TypeInfo system = d.typeMap().get(System.class);
            MethodInfo arrayCopy = system.findUniqueMethod("arraycopy", 5);
            List<ParameterInfo> parameters = arrayCopy.methodInspection.get().getParameters();

            ParameterAnalysis p0 = d.getParameterAnalysis(parameters.get(0));
            assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));

            ParameterAnalysis p2 = d.getParameterAnalysis(parameters.get(2));
            assertEquals(DV.TRUE_DV, p2.getProperty(Property.MODIFIED_VARIABLE));

            TypeInfo integer = d.typeMap().get(Integer.class);
            MethodInfo toString = integer.findUniqueMethod("toString", 1);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getMethodAnalysis(toString)
                    .getProperty(Property.NOT_NULL_EXPRESSION));
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                assertEquals("/*inline pad*/n<11?Integer.toString(i):n<101?i<10?\"0\"+Integer.toString(i):Integer.toString(i):n<1001?i<10?\"00\"+Integer.toString(i):i<100?\"0\"+Integer.toString(i):Integer.toString(i):n<10001?i<10?\"000\"+Integer.toString(i):i<100?\"00\"+Integer.toString(i):i<1000?\"0\"+Integer.toString(i):Integer.toString(i):<return value>",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testSupportAndUtilClasses(List.of(StringUtil.class), 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
