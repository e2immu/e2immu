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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_6 extends CommonTestRunner {

    public Test_16_Modification_6() {
        super(true);
    }

    @Test
    public void test6() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_6";
        final String SET6 = TYPE + ".set6";
        final String EXAMPLE6_SET6 = TYPE + ".set6#" + TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String EXAMPLE6 = TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String VALUES6 = TYPE + ".add6(Modification_6,Set<String>):1:values6";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("add6".equals(d.methodInfo().name)) {
                if (VALUES6.equals(d.variableName())) {
                    assertEquals(DV.FALSE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }

                if (EXAMPLE6.equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ?
                            "<parameter:org.e2immu.analyser.testexample.Modification_6.add6(Modification_6,Set<String>):0:example6>" :
                            "nullable? instance type Modification_6";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
                if (EXAMPLE6_SET6.equals(d.variableName())) {
                    if (d.iteration() > 0)
                        assertEquals(DV.TRUE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                }
            }
            if ("Modification_6".equals(d.methodInfo().name)) {
                if (SET6.equals(d.variableName()) && "0".equals(d.statementId()) && d.iteration() == 3) {
                    assertEquals(DV.TRUE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set6")) {

                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                assertEquals("in6", d.fieldAnalysis().getValue().toString());
                assertEquals("in6:0", d.fieldAnalysis().getLinkedVariables().toString());

                if (iteration >= 1) {
                    assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 0, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("Example6".equals(name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d.p(0), 2, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("add6".equals(name)) {
                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);

                FieldInfo set6 = d.methodInfo().typeInfo.getFieldByName("set6", true);
                VariableInfo set6VariableInfo = d.getFieldAsVariable(set6);
                assertNull(set6VariableInfo); // this variable does not occur!

                List<VariableInfo> vis = d.methodAnalysis().getLastStatement()
                        .latestInfoOfVariablesReferringTo(set6, false);
                assertEquals(1, vis.size());
                VariableInfo vi = vis.get(0);
                if (d.iteration() > 0) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.NOT_NULL_EXPRESSION));
                    assertEquals(DV.TRUE_DV, vi.getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo p0 = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.parameterAnalysis.get()
                    .getProperty(Property.NOT_NULL_PARAMETER));
        };

        testClass("Modification_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }
}
