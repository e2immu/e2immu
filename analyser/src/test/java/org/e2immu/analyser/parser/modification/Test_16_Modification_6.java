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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_6 extends CommonTestRunner {

    public Test_16_Modification_6() {
        super(true);
    }

    @Test
    public void test6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("add6".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "values6".equals(p.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);

                } else if (d.variable() instanceof ParameterInfo p && "example6".equals(p.name)) {
                    String expectValue = d.iteration() == 0 ? "<p:example6>"
                            : "nullable instance type Modification_6/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);

                    assertEquals("example6.set6:4,values6:4", d.variableInfo().getLinkedVariables().toString());

                } else if ("org.e2immu.analyser.parser.modification.testexample.Modification_6.set6#org.e2immu.analyser.parser.modification.testexample.Modification_6.add6(org.e2immu.analyser.parser.modification.testexample.Modification_6,java.util.Set<java.lang.String>):0:example6".equals(d.variableName())) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    assertEquals("example6:2,values6:4", d.variableInfo().getLinkedVariables().toString());

                } else if ("org.e2immu.analyser.parser.modification.testexample.Modification_6.this".equals(d.variableName())) {
                    // since 20220404 we cannot create variables after iteration 0, so this has to exist, even in static methods
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                } else fail("? " + d.variableName());
            }
            if ("Modification_6".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set6".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                        assertEquals("in6:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set6".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));

                assertEquals("in6", d.fieldAnalysis().getValue().toString());
                // in FieldAnalyserImpl.analyseLinked we block all links to field references
                // that go to the same fieldInfo, disallowing example6.set6:0
                assertEquals("in6:0", d.fieldAnalysis().getLinkedVariables().toString());
                assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);

                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
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
                        .latestInfoOfVariablesReferringTo(set6);
                assertEquals(1, vis.size());
                VariableInfo vi = vis.get(0);
                if (d.iteration() > 0) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.NOT_NULL_EXPRESSION));
                    assertEquals(DV.TRUE_DV, vi.getProperty(Property.CONTEXT_MODIFIED));
                }

                assertDv(d.p(1), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
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
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }
}
