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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_9 extends CommonTestRunner {

    public Test_16_Modification_9() {
        super(true);
    }

    @Test
    public void test9() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_9";
        final String S2 = TYPE + ".s2";
        final String ADD = TYPE + ".add(String)";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 1,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("theSet".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s2>" : "s2";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s2>" : "instance type HashSet<String>";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() == 0 ? "s:-1,theSet:0,this.s2:0" : "theSet:0,this.s2:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (S2.equals(d.variableName())) {
                    if (d.iteration() > 0) {
                        assertEquals("theSet:0,this.s2:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if (("2".equals(d.statementId()) || "3".equals(d.statementId())) && d.iteration() > 1) {
                        assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                    if ("3".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (ADD.equals(d.methodInfo().fullyQualifiedName) && d.iteration() > 1) {
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s2".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo add = set.findUniqueMethod("add", 1);
            ParameterInfo p0Add = add.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.INDEPENDENT_1_DV, p0Add.parameterAnalysis.get()
                    .getProperty(Property.INDEPENDENT));
        };

        // there is no transparent content in this type; as a consequence, the parameter s
        // can never be @Dependent1 (even if it weren't of immutable type String)
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_9".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }
}
