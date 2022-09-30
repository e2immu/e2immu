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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_16_Modification_9 extends CommonTestRunner {

    public Test_16_Modification_9() {
        super(true);
    }

    @Test
    public void test9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("theSet".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s2>" : "s2";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s2>" : "s2";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() == 0 ? "Modification_9.LOGGER:-1,this.s2:0" : "this.s2:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    String expectLinked;
                    if ("0".equals(d.statementId())) {
                        expectLinked = "theSet:0";
                    } else {
                        expectLinked = d.iteration() == 0 ? "Modification_9.LOGGER:-1,theSet:0" : "theSet:0";
                    }
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                    if (("2".equals(d.statementId()) || "3".equals(d.statementId()))) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                        String expected = d.iteration() == 0 ? "<f:s2>"
                                : "instance type HashSet<String>/*this.contains(s)&&this.size()>=1*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().fullyQualifiedName)) {
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s2".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("LOGGER".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.EXTERNAL_IMMUTABLE);
                // important: the logger will not store your objects, will never modify their hidden content
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo add = set.findUniqueMethod("add", 1);
            ParameterInfo p0Add = add.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0Add.parameterAnalysis.get()
                    .getProperty(Property.INDEPENDENT));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_9".equals(d.typeInfo().simpleName)) {
                // Logger is an interface, so it must be possible to have hidden content
                assertHc(d, 0, "Logger");
            }
        };

        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
